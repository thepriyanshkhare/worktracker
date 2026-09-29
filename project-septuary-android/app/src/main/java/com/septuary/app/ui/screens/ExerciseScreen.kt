package com.septuary.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.septuary.app.data.ExerciseLogEntity
import com.septuary.app.data.Repository
import com.septuary.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val DURATION_PRESETS = listOf(15, 30, 45, 60)

@Composable
fun ExerciseScreen(repo: Repository) {
    var today by remember { mutableStateOf(listOf<ExerciseLogEntity>()) }
    var recent by remember { mutableStateOf(listOf<ExerciseLogEntity>()) }
    var selectedType by remember { mutableStateOf(repo.exerciseTypes.first()) }
    var selectedMinutes by remember { mutableStateOf(30) }
    var customMinutes by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        today = repo.todayExercise()
        recent = repo.exerciseLog()
    }
    LaunchedEffect(Unit) { reload() }

    val weeklyTotals = remember(recent) { weeklyTotalsByType(recent, repo.exerciseTypes) }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Card(title = "Log a Session") {
            Text("Type", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repo.exerciseTypes.forEach { t ->
                    FilterChipSimple(t, selected = selectedType == t) { selectedType = t }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Duration", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DURATION_PRESETS.forEach { m ->
                    FilterChipSimple(
                        "${m}m",
                        selected = customMinutes.isEmpty() && selectedMinutes == m
                    ) { selectedMinutes = m; customMinutes = "" }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = customMinutes,
                onValueChange = { customMinutes = it },
                placeholder = { Text("custom minutes") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val minutes = customMinutes.toIntOrNull() ?: selectedMinutes
                    if (minutes <= 0) return@Button
                    scope.launch {
                        repo.addExercise(selectedType, minutes)
                        customMinutes = ""
                        reload()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Log Session") }
        }

        Spacer(Modifier.height(12.dp))

        Card(title = "This Week") {
            if (weeklyTotals.all { it.second == 0 }) {
                Text("No sessions logged yet this week.", color = TextMuted, fontSize = 13.sp)
            } else {
                weeklyTotals.forEach { (type, mins) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(type, color = TextMain, fontSize = 13.sp)
                        Text("${mins} min", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(title = "Today") {
            if (today.isEmpty()) {
                Text("Nothing logged today.", color = TextMuted, fontSize = 13.sp)
            } else {
                today.forEach { e ->
                    Text(
                        "${e.time}   ${e.type} — ${e.minutes} min",
                        color = TextMain, fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(title = "Recent") {
            if (recent.isEmpty()) {
                Text("No history yet.", color = TextMuted, fontSize = 13.sp)
            } else {
                recent.take(15).forEach { e ->
                    Text(
                        "${e.date}  ${e.time}   ${e.type} — ${e.minutes} min",
                        color = TextMuted, fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

/** Sums minutes per type for the trailing 7 days (today inclusive), preserving [types] order. */
private fun weeklyTotalsByType(recent: List<ExerciseLogEntity>, types: List<String>): List<Pair<String, Int>> {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -6)
    val cutoff = fmt.format(cal.time)
    val windowed = recent.filter { it.date >= cutoff }
    return types.map { t -> t to windowed.filter { it.type == t }.sumOf { it.minutes } }
}
