package com.septuary.app.ui.screens

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.septuary.app.ui.theme.*

@Composable
fun SettingsScreen(onLock: () -> Unit) {
    val context = LocalContext.current

    var notifGranted by remember { mutableStateOf(hasNotifPermission(context)) }
    var exactAlarmGranted by remember { mutableStateOf(hasExactAlarmPermission(context)) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifGranted = granted
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

        Card(title = "Privacy") {
            Text(
                "This app requests no INTERNET permission — the OS itself blocks all network access at the " +
                    "sandbox level, not just the code choosing not to make calls. All data lives in an encrypted " +
                    "SQLCipher database, keyed by your PIN (PBKDF2, 150,000 rounds). Auto-backup to Google Drive is " +
                    "disabled (allowBackup=false) so an OS-level backup can't copy your data off this device either.",
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

fun hasNotifPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

fun hasExactAlarmPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return am.canScheduleExactAlarms()
}
