package com.septuary.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.septuary.app.Session
import com.septuary.app.data.ExerciseLogEntity
import com.septuary.app.data.Repository
import com.septuary.app.ui.LocalSnackbar
import com.septuary.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private val DURATION_PRESETS = listOf(15, 20, 30, 45, 60)

/** WHO baseline for adults: 150 minutes of moderate activity per week. */
private const val WEEKLY_TARGET_MIN = 150

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseScreen(repo: Repository) {
    val haptic = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val snackbar = LocalSnackbar.current

    var today by remember { mutableStateOf(listOf<ExerciseLogEntity>()) }
    var week by remember { mutableStateOf(listOf<ExerciseLogEntity>()) }
    var selectedType by rememberSaveable { mutableStateOf(repo.exerciseTypes.first()) }
    var selectedMinutes by rememberSaveable { mutableIntStateOf(30) }
    var customMinutes by rememberSaveable { mutableStateOf("") }

    val version = Session.dataVersion
    LaunchedEffect(version) {
        try {
            today = repo.todayExercise()
            week = repo.weekExercise()
        } catch (_: Exception) {
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

    val minutesToLog = customMinutes.toIntOrNull()?.takeIf { it in 1..600 } ?: selectedMinutes

    fun log() {
        focus.clearFocus()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val type = selectedType
        val mins = minutesToLog
        customMinutes = ""
        Session.scope.launch {
            val id = try { repo.addExercise(type, mins) } catch (_: Exception) { -1L }
            withContext(Dispatchers.Main) {
                Session.refresh()
                if (id > 0) snackbar("Logged $mins min ${type.lowercase()}", "Undo") { mutate { repo.deleteExercise(id) } }
            }
        }
    }

    fun remove(e: ExerciseLogEntity) {
        mutate { repo.deleteExercise(e.id) }
        snackbar("Removed ${e.minutes} min ${e.type.lowercase()}", "Undo") { mutate { repo.restoreExercise(e) } }
    }

    val days = remember(week) { lastSevenDays() }
    val perDay = days.map { d -> week.filter { it.date == d.first }.sumOf { it.minutes } }
    val weekTotal = perDay.sum()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // This week
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(fraction = weekTotal.toFloat() / WEEKLY_TARGET_MIN, size = 76.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$weekTotal", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("min", color = TextMuted, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Exercise", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    val activeDays = perDay.count { it > 0 }
                    Text(
                        if (weekTotal >= WEEKLY_TARGET_MIN) "Weekly 150 min reached · $activeDays active days"
                        else "${WEEKLY_TARGET_MIN - weekTotal} min to the weekly 150 · $activeDays active days",
                        color = if (weekTotal >= WEEKLY_TARGET_MIN) Accent else TextMuted, fontSize = 13.sp
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            WeekBars(days.map { it.second }, perDay)
        }

        Spacer(Modifier.height(12.dp))

        // Log a session
        Card(title = "Log a session") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repo.exerciseTypes.forEach { t -> ChoiceChip(t, selectedType == t) { selectedType = t } }
            }
            Spacer(Modifier.height(16.dp))
            Text("Minutes", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DURATION_PRESETS.forEach { m ->
                    ChoiceChip("$m", customMinutes.isEmpty() && selectedMinutes == m) {
                        selectedMinutes = m
                        customMinutes = ""
                        focus.clearFocus()
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = customMinutes,
                onValueChange = { v -> customMinutes = v.filter { it.isDigit() }.take(3) },
                placeholder = { Text("Other (minutes)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                colors = fieldColors()
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = ::log,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
            ) {
                Text("Log $minutesToLog min ${selectedType.lowercase()}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Today
        Card(title = "Today") {
            if (today.isEmpty()) {
                Text("Nothing logged yet.", color = TextMuted, fontSize = 13.sp)
            } else {
                today.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(color = Border.copy(alpha = 0.5f), thickness = 0.5.dp)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${e.type} · ${e.minutes} min", color = TextMain, fontSize = 15.sp)
                            Text(formatTime12h(e.time), color = TextMuted, fontSize = 12.sp)
                        }
                        IconButton(onClick = { remove(e) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Week by activity
        val byType = repo.exerciseTypes.map { t -> t to week.filter { it.type == t }.sumOf { it.minutes } }.filter { it.second > 0 }
        if (byType.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Card(title = "Last 7 days by activity") {
                byType.forEach { (type, mins) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(type, color = TextMain, fontSize = 14.sp)
                        Text("$mins min", color = TextMuted, fontSize = 14.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Last seven days oldest-first as (yyyy-MM-dd, one-letter weekday). */
private fun lastSevenDays(): List<Pair<String, String>> {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val dayFmt = SimpleDateFormat("EEEEE", Locale.US)
    return (6 downTo 0).map { back ->
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -back) }
        fmt.format(c.time) to dayFmt.format(c.time)
    }
}

@Composable
private fun WeekBars(labels: List<String>, minutes: List<Int>) {
    val max = (minutes.maxOrNull() ?: 0).coerceAtLeast(30)
    Row(
        Modifier.fillMaxWidth().height(84.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        labels.forEachIndexed { i, label ->
            val isToday = i == labels.lastIndex
            val target = minutes[i].toFloat() / max
            val h by animateFloatAsState(target, tween(500), label = "bar$i")
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (minutes[i] > 0) Text("${minutes[i]}", color = TextMuted, fontSize = 9.sp)
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .width(18.dp)
                        .height((52 * h).coerceAtLeast(3f).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (minutes[i] > 0) (if (isToday) Accent else Accent.copy(alpha = 0.55f)) else Panel2)
                )
                Spacer(Modifier.height(6.dp))
                Text(label, color = if (isToday) TextMain else TextMuted, fontSize = 11.sp, fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}
