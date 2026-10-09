package com.septuary.app.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.septuary.app.Session
import com.septuary.app.data.Repository
import com.septuary.app.ui.LocalSnackbar
import com.septuary.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

private const val OVERDUE_GRACE_MIN = 60

private data class Slot(val label: String, val fromMin: Int, val toMin: Int)

private val SLOTS = listOf(
    Slot("Morning", 4 * 60, 12 * 60),
    Slot("Afternoon", 12 * 60, 17 * 60),
    Slot("Evening", 17 * 60, 21 * 60),
    Slot("Night", 21 * 60, 28 * 60)
)

private fun slotOf(timeMin: Int): Slot {
    val m = if (timeMin < 4 * 60) timeMin + 24 * 60 else timeMin
    return SLOTS.firstOrNull { m >= it.fromMin && m < it.toMin } ?: SLOTS.last()
}

private fun currentMinute(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

/** One screen for both tabs: [medicine] = true shows medicines, false shows meals, coffee and tea. */
@Composable
fun DoseScreen(repo: Repository, medicine: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val snackbar = LocalSnackbar.current

    var loaded by remember { mutableStateOf(false) }
    var doses by remember { mutableStateOf(listOf<Repository.Dose>()) }
    var done by remember { mutableStateOf(mapOf<String, String>()) }
    var photos by remember { mutableStateOf(mapOf<String, String>()) }
    var health by remember { mutableStateOf(reminderHealth(context)) }
    var nowMin by remember { mutableIntStateOf(currentMinute()) }
    var editing by remember { mutableStateOf<Repository.Dose?>(null) }
    var expandedKey by remember { mutableStateOf<String?>(null) }
    var photoPromptKey by remember { mutableStateOf<String?>(null) }
    var pendingPhotoKey by remember { mutableStateOf<String?>(null) }
    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    var viewingPhoto by remember { mutableStateOf<String?>(null) }

    val version = Session.dataVersion
    LaunchedEffect(version) {
        try {
            val all = repo.todayDoses()
            doses = all.filter { (it.category == "medication") == medicine }
            done = repo.todayLog()
            photos = repo.todayPhotoPaths()
        } catch (_: Exception) {
            // Session locked mid-load; the lock screen is about to replace this view.
        }
        health = reminderHealth(context)
        nowMin = currentMinute()
        loaded = true
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMin = currentMinute()
        }
    }

    fun mutate(block: suspend () -> Unit) {
        Session.scope.launch {
            try {
                block()
            } catch (_: Exception) {
            }
            withContext(Dispatchers.Main) { Session.refresh() }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        Session.externalActivityInFlight = false
        val key = pendingPhotoKey
        val file = pendingPhotoFile
        pendingPhotoKey = null
        pendingPhotoFile = null
        photoPromptKey = null
        if (key != null && file != null) {
            if (success && file.length() > 0) mutate { repo.attachDosePhoto(key, "dose_photos/${file.name}") }
            else file.delete()
        }
    }

    fun takePhoto(doseKey: String) {
        try {
            val file = repo.newDosePhotoFile(doseKey)
            pendingPhotoKey = doseKey
            pendingPhotoFile = file
            val uri: Uri = FileProvider.getUriForFile(context, "com.septuary.app.fileprovider", file)
            Session.externalActivityInFlight = true
            cameraLauncher.launch(uri)
        } catch (_: Exception) {
            Session.externalActivityInFlight = false
            snackbar("No camera app available", null, null)
        }
    }

    fun openSettings(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
        health = reminderHealth(context)
    }

    fun fixReminders() {
        when {
            !health.notifications ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            !health.exactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
            !health.battery ->
                openSettings(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName)))
        }
    }

    fun toggle(dose: Repository.Dose, isDone: Boolean) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        // Optimistic: the tick appears instantly; the database write follows.
        done = if (isDone) done - dose.doseKey else done + (dose.doseKey to java.time.Instant.now().toString())
        photoPromptKey = if (!isDone && !medicine) dose.doseKey else if (photoPromptKey == dose.doseKey) null else photoPromptKey
        mutate { repo.setDone(listOf(dose.doseKey), !isDone) }
    }

    fun markAll(keys: List<String>) {
        if (keys.isEmpty()) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val now = java.time.Instant.now().toString()
        done = done + keys.associateWith { now }
        mutate { repo.setDone(keys, true) }
        snackbar("${keys.size} marked ${if (medicine) "taken" else "done"}", "Undo") {
            mutate { repo.setDone(keys, false) }
        }
    }

    val doneCount = doses.count { done.containsKey(it.doseKey) }
    val overdue = doses.filter { !done.containsKey(it.doseKey) && nowMin > minutesOf(it.time) + OVERDUE_GRACE_MIN }
    val next = doses.filter { !done.containsKey(it.doseKey) && nowMin <= minutesOf(it.time) + OVERDUE_GRACE_MIN }
        .minByOrNull { minutesOf(it.time) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Summary
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    fraction = if (doses.isEmpty()) 0f else doneCount.toFloat() / doses.size,
                    size = 76.dp
                ) {
                    Text("$doneCount/${doses.size}", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (medicine) "Medicine" else "Food", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    val line = when {
                        !loaded -> ""
                        doses.isEmpty() -> "Nothing scheduled today"
                        doneCount == doses.size -> "All done for today"
                        next != null -> "Next · ${next.name} at ${formatTime12h(next.time)}"
                        else -> "${doses.size - doneCount} left today"
                    }
                    Text(line, color = if (doses.isNotEmpty() && doneCount == doses.size) Accent else TextMuted, fontSize = 13.sp)
                    if (overdue.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text("${overdue.size} overdue", color = Warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (medicine && !health.allGood) {
            Spacer(Modifier.height(12.dp))
            ReminderBanner(health, onFix = ::fixReminders)
        }

        // Grouped by time of day
        val groups = SLOTS.mapNotNull { slot ->
            val list = doses.filter { slotOf(minutesOf(it.time)) == slot }
            if (list.isEmpty()) null else slot to list
        }
        groups.forEach { (slot, list) ->
            val remaining = list.filter { !done.containsKey(it.doseKey) }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(slot.label.uppercase(), color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Spacer(Modifier.width(8.dp))
                Text("${list.size - remaining.size}/${list.size}", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                if (remaining.size > 1) {
                    Text(
                        "Mark all", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { markAll(remaining.map { it.doseKey }) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Panel)
            ) {
                list.forEachIndexed { i, dose ->
                    if (i > 0) HorizontalDivider(color = Border.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                    DoseRow(
                        dose = dose,
                        takenAt = done[dose.doseKey],
                        isOverdue = dose in overdue,
                        isNext = dose == next,
                        expanded = expandedKey == dose.doseKey,
                        photoPath = photos[dose.doseKey],
                        showPhotoPrompt = photoPromptKey == dose.doseKey && photos[dose.doseKey] == null,
                        medicine = medicine,
                        onToggle = { toggle(dose, done.containsKey(dose.doseKey)) },
                        onExpand = { expandedKey = if (expandedKey == dose.doseKey) null else dose.doseKey },
                        onEditTime = { editing = dose },
                        onTakePhoto = { takePhoto(dose.doseKey) },
                        onSkipPhoto = { photoPromptKey = null },
                        onViewPhoto = { viewingPhoto = it }
                    )
                }
            }
        }

        if (loaded && doses.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Tap an item for details · tap its time to change it",
                color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    editing?.let { d ->
        TimeEditDialog(
            title = d.name,
            initial = d.time,
            onDismiss = { editing = null },
            onConfirm = { newTime ->
                editing = null
                mutate { repo.updateTime(d.medId, newTime) }
                snackbar("${d.name} moved to ${formatTime12h(newTime)}", null, null)
            }
        )
    }

    viewingPhoto?.let { path ->
        Dialog(onDismissRequest = { viewingPhoto = null }) {
            val bitmap = remember(path) {
                try {
                    BitmapFactory.decodeFile(File(context.filesDir, path).path, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
                } catch (_: Throwable) {
                    null
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { viewingPhoto = null }
                )
            }
        }
    }
}

@Composable
private fun DoseRow(
    dose: Repository.Dose,
    takenAt: String?,
    isOverdue: Boolean,
    isNext: Boolean,
    expanded: Boolean,
    photoPath: String?,
    showPhotoPrompt: Boolean,
    medicine: Boolean,
    onToggle: () -> Unit,
    onExpand: () -> Unit,
    onEditTime: () -> Unit,
    onTakePhoto: () -> Unit,
    onSkipPhoto: () -> Unit,
    onViewPhoto: (String) -> Unit
) {
    val isDone = takenAt != null
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CheckCircle(isDone, onToggle)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    dose.name, color = if (isDone) TextMuted else TextMain, fontSize = 15.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (dose.detail.isNotBlank()) {
                    Text(
                        dose.detail, color = TextMuted, fontSize = 12.sp,
                        maxLines = if (expanded) 3 else 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (expanded && dose.instruction.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(dose.instruction, color = TextMain.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 16.sp)
                }
                when {
                    isDone -> formatInstantLocal(takenAt!!).takeIf { it.isNotEmpty() }?.let {
                        Text("${if (medicine) "Taken" else "Done"} at $it", color = Accent, fontSize = 11.sp)
                    }
                    isOverdue -> Text("Overdue", color = Warn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (photoPath != null) {
                PhotoThumb(photoPath, Modifier
                    .padding(start = 8.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onViewPhoto(photoPath) })
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isNext) AccentSoft else Panel2)
                    .clickable(onClick = onEditTime)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    formatTime12h(dose.time), fontSize = 12.sp,
                    color = when {
                        isNext -> Accent
                        isOverdue -> Warn
                        else -> TextMuted
                    },
                    fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
        if (showPhotoPrompt) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Add a photo?", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AccentSoft)
                        .clickable(onClick = onTakePhoto)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Camera", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Skip", color = TextMuted, fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onSkipPhoto)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun CheckCircle(checked: Boolean, onClick: () -> Unit) {
    val fill by animateColorAsState(if (checked) Accent else Color.Transparent, label = "checkFill")
    val ring by animateColorAsState(if (checked) Accent else Border, label = "checkRing")
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, ring, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Filled.Check, contentDescription = "Done", tint = Bg, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ReminderBanner(health: ReminderHealth, onFix: () -> Unit) {
    val issue = when {
        !health.notifications -> "Notifications are off"
        !health.exactAlarms -> "Exact-time alarms are off"
        else -> "Battery saver may delay reminders"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Warn.copy(alpha = 0.12f))
            .clickable(onClick = onFix)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.NotificationsOff, contentDescription = null, tint = Warn, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(issue, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("Reminders may not ring on time", color = TextMuted, fontSize = 12.sp)
        }
        Text("Fix", color = Warn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PhotoThumb(photoPath: String, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap = remember(photoPath) {
        try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
            BitmapFactory.decodeFile(File(context.filesDir, photoPath).path, opts)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    if (bitmap != null) Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}
