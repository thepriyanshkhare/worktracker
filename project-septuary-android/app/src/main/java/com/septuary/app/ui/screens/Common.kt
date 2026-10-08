package com.septuary.app.ui.screens

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import com.septuary.app.ui.theme.*

@Composable
fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(title.uppercase(), color = TextMuted, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Panel2, unfocusedContainerColor = Panel2,
    focusedTextColor = TextMain, unfocusedTextColor = TextMain,
    focusedBorderColor = Accent, unfocusedBorderColor = Border,
    focusedPlaceholderColor = TextMuted, unfocusedPlaceholderColor = TextMuted
)

@Composable
fun FilterChipSimple(label: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) Accent else Panel2,
            labelColor = if (selected) Bg else TextMain
        )
    )
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeEditDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val parts = remember(initial) { initial.split(":").map { it.toIntOrNull() ?: 0 } }
    val state = rememberTimePickerState(
        initialHour = parts.getOrElse(0) { 0 },
        initialMinute = parts.getOrElse(1) { 0 },
        is24Hour = true
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Change time — $title", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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

