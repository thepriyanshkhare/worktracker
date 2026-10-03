package com.septuary.app.ui.screens

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.health.connect.client.PermissionController
import com.septuary.app.data.HealthConnectRepository
import com.septuary.app.data.MedicationEntity
import com.septuary.app.data.Repository
import com.septuary.app.data.scheduleLabel
import com.septuary.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repo: Repository, onLock: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var notifGranted by remember { mutableStateOf(hasNotifPermission(context)) }
    var exactAlarmGranted by remember { mutableStateOf(hasExactAlarmPermission(context)) }
    val hcAvailable = remember { HealthConnectRepository.isAvailable(context) }
    var hcGranted by remember { mutableStateOf(false) }
    var hcSyncing by remember { mutableStateOf(false) }

    var medications by remember { mutableStateOf(listOf<MedicationEntity>()) }
    var editingMed by remember { mutableStateOf<MedicationEntity?>(null) }

    LaunchedEffect(Unit) {
        if (hcAvailable) hcGranted = HealthConnectRepository.hasAllPermissions(context)
        medications = repo.medications()
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifGranted = granted
    }

    val hcPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        hcGranted = granted.containsAll(HealthConnectRepository.requiredPermissions())
        if (hcGranted) {
            scope.launch {
                hcSyncing = true
                HealthConnectRepository.sync(context, repo)
                hcSyncing = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Card(title = "Reminders") {
            Text("Notifications: " + if (notifGranted) "Enabled" else "Not enabled",
                color = if (notifGranted) Accent else TextMuted, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text("Exact alarms: " + if (exactAlarmGranted) "Enabled" else "Not enabled",
                color = if (exactAlarmGranted) Accent else TextMuted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))

            if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Button(onClick = { notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Allow notifications")
                }
                Spacer(Modifier.height(8.dp))
            }
            if (!exactAlarmGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
                    context.startActivity(intent)
                }) {
                    Text("Allow exact alarms")
                }
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            }, colors = ButtonDefaults.buttonColors(containerColor = Panel2)) {
                Text("Battery settings (exclude from optimization)")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Honest limit: exact alarms + battery-optimization exemption together get this close to a " +
                    "guaranteed wake-up, but Android can still defer in rare cases (extreme low-power mode, " +
                    "some OEM battery managers like MIUI/OneUI have their own aggressive killers on top of stock Android — " +
                    "if you're on a Xiaomi/Oppo/Vivo/OnePlus device, check that manufacturer's own \"autostart\"/\"app battery\" " +
                    "settings too, since those sit above what this app can control).",
                color = TextMuted, fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        Card(title = "Medications") {
            Text(
                "Tap a medicine to change its scheduled time — its reminder reschedules to match. " +
                    "The time only ever changes here, deliberately: logging a dose late or early " +
                    "never shifts it on its own.",
                color = TextMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            val meds = medications.filter { it.category == "medication" }
            if (meds.isEmpty()) {
                Text("No medicines set up.", color = TextMuted, fontSize = 13.sp)
            } else {
                meds.forEach { med ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editingMed = med }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(med.name, color = TextMain, fontSize = 14.sp)
                            Text(scheduleLabel(med.days), color = TextMuted, fontSize = 11.sp)
                        }
                        Text(med.time, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        editingMed?.let { med ->
            MedicationTimeEditDialog(
                med = med,
                onDismiss = { editingMed = null },
                onConfirm = { newTime ->
                    scope.launch {
                        repo.updateMedicationTime(med.id, newTime)
                        medications = repo.medications()
                        editingMed = null
                    }
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        Card(title = "Health Connect") {
            val statusText = when {
                !hcAvailable -> "Not available on this device"
                hcSyncing -> "Syncing…"
                hcGranted -> "Connected — Weight & Steps auto-fill on unlock"
                else -> "Permission needed"
            }
            Text(statusText, color = if (hcGranted) Accent else TextMuted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            if (hcAvailable && !hcGranted) {
                Button(onClick = { hcPermissionLauncher.launch(HealthConnectRepository.requiredPermissions()) }) {
                    Text("Connect Health Connect")
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "Reads only Weight and Steps — never sleep or heart rate. A day you log weight for " +
                    "yourself always wins; Health Connect only fills in days you haven't logged.",
                color = TextMuted, fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        Card(title = "Privacy") {
            Text(
                "All data lives in an encrypted SQLCipher database, keyed by your PIN (PBKDF2, 150,000 rounds). " +
                    "Auto-backup to Google Drive is disabled (allowBackup=false) so an OS-level backup can't copy " +
                    "your data off this device either. INTERNET permission is granted, but used for exactly one " +
                    "thing: a small, authenticated sync to Firestore so your parents' Supervisor app can see a " +
                    "status summary (and, as of Oct 2026, actual medicine/food names, exercise, and recent weight " +
                    "— a deliberate choice you made). Glucose and free-text notes are never synced. See " +
                    "SyncRepository.kt for exactly what leaves this device and firestore.rules for who can read it.",
                color = TextMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "One deliberate exception: medication names + times (not your logs or readings) are kept in a " +
                    "small unencrypted cache so reminders can survive a phone reboot without you reopening the app " +
                    "first. See ScheduleCache.kt in the source if you want to remove that trade-off.",
                color = TextMuted, fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        Card(title = "Session") {
            Button(onClick = onLock, colors = ButtonDefaults.buttonColors(containerColor = Panel2, contentColor = Danger)) {
                Text("Lock now")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationTimeEditDialog(med: MedicationEntity, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val parts = remember(med.time) { med.time.split(":").map { it.toIntOrNull() ?: 0 } }
    val state = rememberTimePickerState(
        initialHour = parts.getOrElse(0) { 0 },
        initialMinute = parts.getOrElse(1) { 0 },
        is24Hour = true
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Change time — ${med.name}", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "The reminder moves with it; today's already-logged history for this medicine is untouched.",
                    color = TextMuted, fontSize = 12.sp
                )
                Spacer(Modifier.height(14.dp))
                TimePicker(state = state)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onConfirm("%02d:%02d".format(state.hour, state.minute)) }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

fun hasNotifPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

fun hasExactAlarmPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return am.canScheduleExactAlarms()
}
