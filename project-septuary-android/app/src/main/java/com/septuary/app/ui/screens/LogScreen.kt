package com.septuary.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.septuary.app.data.GlucoseEntity
import com.septuary.app.data.Repository
import com.septuary.app.data.SleepEntity
import com.septuary.app.data.WeightEntity
import com.septuary.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LogScreen(repo: Repository) {
    var weights by remember { mutableStateOf(listOf<WeightEntity>()) }
    var glucoses by remember { mutableStateOf(listOf<GlucoseEntity>()) }
    var sleeps by remember { mutableStateOf(listOf<SleepEntity>()) }
    var weightInput by remember { mutableStateOf("") }
    var glucoseInput by remember { mutableStateOf("") }
    var glucoseType by remember { mutableStateOf("Fasting") }
    var bedTimeInput by remember { mutableStateOf("") }
    var wakeTimeInput by remember { mutableStateOf("") }
    var sleepQuality by remember { mutableStateOf(3) } // 1..5, default "Good"
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        weights = repo.weightLog()
        glucoses = repo.glucoseLog()
        sleeps = repo.sleepLog()
    }
    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Card(title = "Log Weight") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weightInput, onValueChange = { weightInput = it },
                    placeholder = { Text("kg") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors()
                )
                Button(onClick = {
                    val v = weightInput.toDoubleOrNull() ?: return@Button
                    scope.launch { repo.addWeight(v); weightInput = ""; reload() }
                }) { Text("Add") }
            }
            Spacer(Modifier.height(8.dp))
            weights.take(8).forEach { w ->
                Text("${w.date}  ${w.time}   ${w.kg} kg", color = TextMuted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(title = "Log Glucose") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = glucoseInput, onValueChange = { glucoseInput = it },
                    placeholder = { Text("mg/dL") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors()
                )
                Button(onClick = {
                    val v = glucoseInput.toIntOrNull() ?: return@Button
                    scope.launch { repo.addGlucose(v, glucoseType); glucoseInput = ""; reload() }
                }) { Text("Add") }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Fasting", "PP", "PP (night)", "Random").forEach { t ->
                    FilterChipSimple(t, selected = glucoseType == t) { glucoseType = t }
                }
            }
            Spacer(Modifier.height(8.dp))
            glucoses.take(8).forEach { g ->
                Text("${g.date}  ${g.time}   ${g.type}: ${g.value} mg/dL", color = TextMuted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(title = "Sleep — Feel & Quality") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = bedTimeInput, onValueChange = { bedTimeInput = it },
                    placeholder = { Text("bed HH:MM") }, modifier = Modifier.weight(1f),
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = wakeTimeInput, onValueChange = { wakeTimeInput = it },
                    placeholder = { Text("wake HH:MM") }, modifier = Modifier.weight(1f),
                    colors = fieldColors()
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("How did it feel?", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repo.sleepQualityLabels.forEachIndexed { idx, label ->
                    FilterChipSimple(label, selected = sleepQuality == idx + 1) { sleepQuality = idx + 1 }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = {
                val bed = bedTimeInput.trim()
                val wake = wakeTimeInput.trim()
                val validTime = Regex("""^\d{1,2}:\d{2}$""")
                if (!validTime.matches(bed) || !validTime.matches(wake)) return@Button
                scope.launch {
                    repo.addSleep(bed, wake, sleepQuality)
                    bedTimeInput = ""; wakeTimeInput = ""
                    reload()
                }
            }) { Text("Log Sleep") }
            Spacer(Modifier.height(8.dp))
            sleeps.take(8).forEach { s ->
                val qualityLabel = repo.sleepQualityLabels.getOrElse(s.quality - 1) { "Good" }
                Text(
                    "${s.date}   ${s.bedTime}–${s.wakeTime} (${"%.1f".format(s.hours)}h) — $qualityLabel",
                    color = TextMuted, fontSize = 12.sp
                )
            }
        }
    }
}

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
