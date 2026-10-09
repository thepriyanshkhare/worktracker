package com.septuary.app.ui.screens

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.septuary.app.alarm.AlarmScheduler
import com.septuary.app.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// --- Formatting --------------------------------------------------------------------------------

fun formatTime12h(t: String): String {
    val parts = t.split(":").mapNotNull { it.toIntOrNull() }
    if (parts.size != 2) return t
    val (h, m) = parts
    val ampm = if (h >= 12) "PM" else "AM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, ampm)
}

fun minutesOf(t: String): Int {
    val parts = t.split(":").mapNotNull { it.toIntOrNull() }
    return if (parts.size == 2) parts[0] * 60 + parts[1] else 0
}

private val clockFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

fun formatInstantLocal(iso: String): String = try {
    clockFmt.format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
} catch (_: Exception) {
    ""
}

fun Modifier.clickableSimple(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

// --- Building blocks ---------------------------------------------------------------------------

@Composable
fun Card(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        if (title != null) {
            Text(title.uppercase(), color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Panel2, unfocusedContainerColor = Panel2,
    focusedTextColor = TextMain, unfocusedTextColor = TextMain,
    focusedBorderColor = Accent, unfocusedBorderColor = Border,
    focusedPlaceholderColor = TextMuted, unfocusedPlaceholderColor = TextMuted,
    cursorColor = Accent
)

@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) Accent else Panel2, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label, fontSize = 13.sp,
            color = if (selected) Bg else TextMain,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** Circular progress with an animated sweep. */
@Composable
fun ProgressRing(fraction: Float, size: Dp, stroke: Dp = 7.dp, color: Color = Accent, content: @Composable BoxScope.() -> Unit) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val inset = s / 2
            val arcSize = Size(this.size.width - s, this.size.height - s)
            drawArc(Panel2, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(s))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(s, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

// --- Reminder health (what can stop a reminder from ringing) -----------------------------------

data class ReminderHealth(val notifications: Boolean, val exactAlarms: Boolean, val battery: Boolean) {
    val allGood get() = notifications && exactAlarms && battery
}

fun reminderHealth(context: Context): ReminderHealth {
    val notif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return ReminderHealth(
        notifications = notif,
        exactAlarms = AlarmScheduler.canScheduleExact(context),
        battery = pm.isIgnoringBatteryOptimizations(context.packageName)
    )
}

// --- Time picker -------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeEditDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val parts = remember(initial) { initial.split(":").map { it.toIntOrNull() ?: 0 } }
    val state = rememberTimePickerState(
        initialHour = parts.getOrElse(0) { 0 },
        initialMinute = parts.getOrElse(1) { 0 },
        is24Hour = false
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Panel, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("The reminder moves with it.", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = Panel2,
                        selectorColor = Accent,
                        timeSelectorSelectedContainerColor = AccentSoft,
                        timeSelectorSelectedContentColor = Accent,
                        timeSelectorUnselectedContainerColor = Panel2,
                        timeSelectorUnselectedContentColor = TextMain,
                        periodSelectorSelectedContainerColor = AccentSoft,
                        periodSelectorSelectedContentColor = Accent,
                        periodSelectorUnselectedContentColor = TextMuted,
                        periodSelectorBorderColor = Border
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm("%02d:%02d".format(state.hour, state.minute)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
                    ) { Text("Save") }
                }
            }
        }
    }
}
