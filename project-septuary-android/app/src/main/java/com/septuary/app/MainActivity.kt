package com.septuary.app

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.septuary.app.alarm.AlarmScheduler
import com.septuary.app.alarm.Notifications
import com.septuary.app.crypto.BiometricVault
import com.septuary.app.crypto.PinCrypto
import com.septuary.app.data.AppDatabase
import com.septuary.app.data.Repository
import com.septuary.app.data.SyncRepository
import com.septuary.app.ui.LocalSnackbar
import com.septuary.app.ui.promptBiometric
import com.septuary.app.ui.screens.*
import com.septuary.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Locks automatically after this long in the background. */
private const val AUTO_LOCK_MS = 5 * 60 * 1000L

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.ensureChannels(this)
        // Keep health data out of the Recents/app-switcher thumbnail.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP ->
                    if (!isChangingConfigurations) Session.backgroundedAt = SystemClock.elapsedRealtime()
                Lifecycle.Event.ON_START -> {
                    val since = Session.backgroundedAt
                    Session.backgroundedAt = 0L
                    if (since > 0 && !Session.externalActivityInFlight && SystemClock.elapsedRealtime() - since > AUTO_LOCK_MS) {
                        Session.lock()
                    }
                }
                Lifecycle.Event.ON_RESUME -> onResumedWork()
                else -> {}
            }
        })

        setContent {
            SeptuaryTheme {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    App(this)
                }
            }
        }
    }

    /** Every return to the app: re-arm reminders, merge notification taps, refresh "today". */
    private fun onResumedWork() {
        val appContext = applicationContext
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try { AlarmScheduler.rescheduleFromCache(appContext) } catch (_: Exception) {}
            }
            val repo = Session.repo ?: return@launch
            try {
                if (repo.applyPendingActions()) SyncRepository.schedulePush(repo)
            } catch (_: Exception) {
            }
            Session.refresh()
        }
    }
}

@Composable
fun App(activity: FragmentActivity) {
    val repo = Session.repo
    if (repo == null) LockGate(activity) else MainScaffold(repo, activity)
}

// --- Unlock ------------------------------------------------------------------------------------

private fun isWrongKey(e: Throwable): Boolean {
    var t: Throwable? = e
    while (t != null) {
        val m = t.message ?: ""
        if (m.contains("file is not a database", ignoreCase = true)) return true
        t = t.cause
    }
    return false
}

@Composable
private fun LockGate(activity: FragmentActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isFirstRun by remember { mutableStateOf(!PinCrypto.hasPinSetup(context)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var errorTick by remember { mutableIntStateOf(0) }
    var lockoutSeconds by remember { mutableIntStateOf(((PinCrypto.lockoutRemainingMs(context) + 999) / 1000).toInt()) }
    val bioEnabled = remember { !isFirstRun && BiometricVault.isEnabled(context) && BiometricVault.isAvailable(context) }

    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0) {
            delay(1000)
            lockoutSeconds = ((PinCrypto.lockoutRemainingMs(context) + 999) / 1000).toInt()
        }
    }

    fun fail(message: String) {
        error = message
        errorTick++
        busy = false
    }

    /** Opens the database with [passphrase]; on success the session starts and the UI switches. */
    suspend fun open(passphrase: CharArray, viaPin: Boolean, isSetup: Boolean) {
        val outcome: Result<Repository> = withContext(Dispatchers.IO) {
            runCatching {
                val db = AppDatabase.open(context, passphrase)
                try {
                    db.medicationDao().count() // a wrong key fails here
                    val r = Repository(db, context.applicationContext)
                    r.prepare()
                    r
                } catch (e: Exception) {
                    try { db.close() } catch (_: Exception) {}
                    throw e
                }
            }
        }
        outcome.onSuccess { repo ->
            PinCrypto.recordSuccess(context)
            Session.open(repo, passphrase)
            Session.offerBiometric = (viaPin || isSetup) &&
                BiometricVault.isAvailable(context) && !BiometricVault.isEnabled(context) && !BiometricVault.wasDeclined(context)
            isFirstRun = false
            busy = false
            Session.scope.launch {
                SyncRepository.cleanupLegacy(context.applicationContext)
                SyncRepository.importInbox(repo)
                SyncRepository.checkReminders(context.applicationContext, repo)
                SyncRepository.pushToday(repo)
                withContext(Dispatchers.Main) { Session.refresh() }
            }
        }.onFailure { e ->
            when {
                isWrongKey(e) && viaPin -> {
                    PinCrypto.recordFailure(context)
                    lockoutSeconds = ((PinCrypto.lockoutRemainingMs(context) + 999) / 1000).toInt()
                    fail("Wrong PIN")
                }
                isWrongKey(e) -> {
                    BiometricVault.disable(context)
                    fail("Fingerprint unlock was reset. Enter your PIN.")
                }
                else -> fail("Couldn't open your data. Please try again.")
            }
        }
    }

    fun onPin(pin: String, isSetup: Boolean) {
        if (busy || PinCrypto.lockoutRemainingMs(context) > 0) return
        busy = true
        error = null
        scope.launch {
            val pass = withContext(Dispatchers.Default) {
                if (isSetup) PinCrypto.setupPin(context, pin) else PinCrypto.deriveForUnlock(context, pin)
            }
            open(pass, viaPin = true, isSetup = isSetup)
        }
    }

    fun onBiometric() {
        if (busy) return
        val cipher = BiometricVault.cipherForUnlock(context)
        if (cipher == null) {
            fail("Fingerprint unlock was reset. Enter your PIN.")
            return
        }
        promptBiometric(activity, cipher, "Unlock Project Septuary", "Confirm it's you") { authorised ->
            if (authorised != null) {
                val pass = BiometricVault.reveal(context, authorised)
                if (pass != null) {
                    busy = true
                    scope.launch { open(pass, viaPin = false, isSetup = false) }
                }
            }
        }
    }

    // Offer fingerprint straight away when the lock screen appears.
    LaunchedEffect(Unit) {
        if (bioEnabled && lockoutSeconds == 0) {
            activity.lifecycle.withResumed { }
            onBiometric()
        }
    }

    LockScreen(
        isFirstRun = isFirstRun,
        busy = busy,
        errorMessage = error,
        errorTick = errorTick,
        lockoutSeconds = lockoutSeconds,
        biometricEnabled = bioEnabled,
        onPinReady = ::onPin,
        onBiometric = ::onBiometric
    )
}

// --- Main ----------------------------------------------------------------------------------------

private enum class Tab(val label: String) { MEDICINE("Medicine"), FOOD("Food"), EXERCISE("Exercise") }

@Composable
fun MainScaffold(repo: Repository, activity: FragmentActivity) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.MEDICINE) }
    var menuOpen by remember { mutableStateOf(false) }
    var showFamily by remember { mutableStateOf(false) }
    var bioOn by remember { mutableStateOf(BiometricVault.isEnabled(context)) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val showSnackbar: (String, String?, (() -> Unit)?) -> Unit = { message, action, onAction ->
        scope.launch {
            snackbarHost.currentSnackbarData?.dismiss()
            val result = snackbarHost.showSnackbar(message, actionLabel = action, withDismissAction = false, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }

    // Roll "today" over at midnight even if the app stays open.
    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            val next = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 5); set(Calendar.MILLISECOND, 0)
            }
            delay(next.timeInMillis - now.timeInMillis)
            Session.refresh()
        }
    }

    BackHandler(enabled = tab != Tab.MEDICINE) { tab = Tab.MEDICINE }

    fun enableBiometric() {
        val pass = Session.passphrase ?: return
        val cipher = BiometricVault.cipherForEnrol()
        if (cipher == null) {
            showSnackbar("Fingerprint unlock isn't available on this phone", null, null)
            return
        }
        promptBiometric(activity, cipher, "Turn on fingerprint unlock", "Confirm with your fingerprint") { authorised ->
            if (authorised != null && BiometricVault.store(context, authorised, pass)) {
                bioOn = true
                showSnackbar("Fingerprint unlock is on", null, null)
            }
        }
    }

    CompositionLocalProvider(LocalSnackbar provides showSnackbar) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            Column(Modifier.fillMaxSize()) {
                // Header
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            remember(Session.dataVersion) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()).uppercase() },
                            color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp
                        )
                        Text("Project Septuary", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = TextMuted)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Family sharing", color = TextMain) },
                                onClick = { menuOpen = false; showFamily = true }
                            )
                            if (BiometricVault.isAvailable(context)) {
                                DropdownMenuItem(
                                    text = { Text(if (bioOn) "Turn off fingerprint unlock" else "Turn on fingerprint unlock", color = TextMain) },
                                    onClick = {
                                        menuOpen = false
                                        if (bioOn) {
                                            BiometricVault.disable(context)
                                            bioOn = false
                                            showSnackbar("Fingerprint unlock is off", null, null)
                                        } else {
                                            enableBiometric()
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Lock now", color = Danger) },
                                onClick = { menuOpen = false; Session.lock() }
                            )
                        }
                    }
                }

                Box(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "tab"
                    ) { t ->
                        when (t) {
                            Tab.MEDICINE -> DoseScreen(repo, medicine = true)
                            Tab.FOOD -> DoseScreen(repo, medicine = false)
                            Tab.EXERCISE -> ExerciseScreen(repo)
                        }
                    }
                }

                NavigationBar(containerColor = Panel, contentColor = TextMain, tonalElevation = 0.dp) {
                    Tab.values().forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {
                                Icon(
                                    when (t) {
                                        Tab.MEDICINE -> Icons.Filled.Medication
                                        Tab.FOOD -> Icons.Filled.Restaurant
                                        Tab.EXERCISE -> Icons.Filled.FitnessCenter
                                    },
                                    contentDescription = null
                                )
                            },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent, selectedTextColor = Accent,
                                unselectedIconColor = TextMuted, unselectedTextColor = TextMuted,
                                indicatorColor = AccentSoft
                            )
                        )
                    }
                }
            }

            SnackbarHost(
                snackbarHost,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp, start = 12.dp, end = 12.dp)
            ) { data ->
                Snackbar(data, containerColor = Panel2, contentColor = TextMain, actionColor = Accent, shape = MaterialTheme.shapes.medium)
            }
        }

        if (showFamily) FamilyDialog(onDismiss = { showFamily = false })

        if (Session.offerBiometric) {
            AlertDialog(
                onDismissRequest = { Session.offerBiometric = false },
                containerColor = Panel,
                title = { Text("Unlock with fingerprint?", color = TextMain) },
                text = {
                    Text(
                        "Faster than typing your PIN. Your PIN still works, and adding a new fingerprint to the phone turns this off automatically.",
                        color = TextMuted
                    )
                },
                confirmButton = {
                    TextButton(onClick = { Session.offerBiometric = false; enableBiometric() }) { Text("Turn on", color = Accent) }
                },
                dismissButton = {
                    TextButton(onClick = { Session.offerBiometric = false; BiometricVault.setDeclined(context) }) {
                        Text("Not now", color = TextMuted)
                    }
                }
            )
        }
    }
}
