package com.septuary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.septuary.app.crypto.PinCrypto
import com.septuary.app.data.AppDatabase
import com.septuary.app.data.Repository
import com.septuary.app.ui.screens.*
import com.septuary.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SeptuaryTheme {
                Surface(color = Bg) {
                    App()
                }
            }
        }
    }
}

@Composable
fun App() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var repo by remember { mutableStateOf<Repository?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isFirstRun by remember { mutableStateOf(!PinCrypto.hasPinSetup(context)) }
    val scope = rememberCoroutineScope()

    if (repo == null) {
        LockScreen(
            isFirstRun = isFirstRun,
            errorMessage = errorMessage,
            onPinReady = { pin, isSetup ->
                scope.launch {
                    errorMessage = null
                    try {
                        val passphrase = if (isSetup) PinCrypto.setupPin(context, pin) else PinCrypto.deriveForUnlock(context, pin)
                        val db = withContext(Dispatchers.IO) { AppDatabase.open(context, passphrase) }
                        val newRepo = Repository(db, context)
                        // Touch the DB now, inside the try, so a wrong PIN fails HERE (with a
                        // catchable exception) rather than surfacing later as a mysterious crash.
                        withContext(Dispatchers.IO) { db.medicationDao().count() }
                        newRepo.seedIfEmpty()
                        repo = newRepo
                        isFirstRun = false
                        // Drain anything logged from the Claude chat, then push current status —
                        // both run on unlock so parents see today's real state even before
                        // anything's been toggled in the app itself.
                        launch {
                            com.septuary.app.data.SyncRepository.importInbox(newRepo)
                            com.septuary.app.data.SyncRepository.checkReminders(context, newRepo)
                                            com.septuary.app.data.SyncRepository.pushToday(newRepo)
                        }
                    } catch (e: Exception) {
                        errorMessage = if (isSetup) "Something went wrong setting up. Try again." else "Wrong PIN. Try again."
                    }
                }
            }
        )
    } else {
        MainScaffold(repo = repo!!, onLock = {
            repo?.close()
            repo = null
        })
    }
}

private enum class Tab(val label: String) { MEDICINE("Medicine"), FOOD("Food"), EXERCISE("Exercise") }

@Composable
fun MainScaffold(repo: Repository, onLock: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.MEDICINE) }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp, start = 16.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                "Project Septuary", color = TextMain, fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onLock) {
                Icon(Icons.Filled.Lock, contentDescription = "Lock", tint = TextMuted)
            }
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.MEDICINE -> DoseScreen(repo, medicine = true)
                Tab.FOOD -> DoseScreen(repo, medicine = false)
                Tab.EXERCISE -> ExerciseScreen(repo)
            }
        }

        NavigationBar(containerColor = Panel, contentColor = TextMain) {
            Tab.values().forEach { t ->
                NavigationBarItem(
                    selected = tab == t, onClick = { tab = t },
                    icon = {
                        Icon(
                            when (t) {
                                Tab.MEDICINE -> Icons.Filled.CheckCircle
                                Tab.FOOD -> Icons.Filled.Restaurant
                                Tab.EXERCISE -> Icons.Filled.FitnessCenter
                            },
                            contentDescription = null
                        )
                    },
                    label = { Text(t.label) },
                    colors = navColors()
                )
            }
        }
    }
}

@Composable
fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Accent, selectedTextColor = Accent,
    unselectedIconColor = TextMuted, unselectedTextColor = TextMuted,
    indicatorColor = Panel2
)
