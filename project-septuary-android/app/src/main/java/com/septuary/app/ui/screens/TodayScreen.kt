package com.septuary.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.septuary.app.data.Repository
import com.septuary.app.ui.MotivationQuotes
import com.septuary.app.ui.theme.*
import kotlinx.coroutines.launch

fun formatTime12h(t: String): String {
    val (h, m) = t.split(":").map { it.toInt() }
    val ampm = if (h >= 12) "PM" else "AM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, ampm)
}

@Composable
fun TodayScreen(repo: Repository) {
    var doses by remember { mutableStateOf(listOf<Repository.Dose>()) }
    var done by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        doses = repo.todayDoses()
        done = repo.todayLog()
    }

    LaunchedEffect(Unit) { reload() }
    // Picked fresh each time this composable enters composition (i.e. each visit to the Today tab).
    val quote = remember { MotivationQuotes.random() }

    val medications = doses.filter { it.category == "medication" }
    val reminders = doses.filter { it.category != "medication" }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Panel2, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(quote, color = TextMain, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "${done.size} / ${doses.size} taken today",
            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp)
        )

        DoseSection(
            title = "Medications",
            subtitle = "${medications.count { done.contains(it.doseKey) }}/${medications.size}",
            doses = medications, done = done,
            onToggle = { doseKey, isDone -> scope.launch { repo.toggleDose(doseKey, isDone); reload() } }
        )

        Spacer(Modifier.height(12.dp))

        DoseSection(
            title = "Daily Reminders",
            subtitle = "${reminders.count { done.contains(it.doseKey) }}/${reminders.size}",
            doses = reminders, done = done,
            onToggle = { doseKey, isDone -> scope.launch { repo.toggleDose(doseKey, isDone); reload() } }
        )
    }
}

@Composable
private fun DoseSection(
    title: String,
    subtitle: String,
    doses: List<Repository.Dose>,
    done: Set<String>,
    onToggle: (String, Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title.uppercase(), color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = TextMuted, fontSize = 12.sp)
    }
    if (doses.isEmpty()) {
        Text("Nothing scheduled.", color = TextMuted, fontSize = 13.sp)
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(14.dp))
                .padding(vertical = 4.dp)
        ) {
            doses.forEach { dose ->
                val isDone = done.contains(dose.doseKey)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .background(if (isDone) Accent else Bg, CircleShape)
                            .border(2.dp, if (isDone) Accent else Border, CircleShape)
                            .clickableSimple { onToggle(dose.doseKey, isDone) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) Text("✓", color = Bg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(dose.name, color = if (isDone) TextMuted else TextMain, fontSize = 15.sp)
                        Text(dose.detail, color = TextMuted, fontSize = 12.sp)
                    }
                    Text(formatTime12h(dose.time), color = TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

// Small helper so we don't need to import the full clickable() boilerplate at every call site.
fun Modifier.clickableSimple(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
