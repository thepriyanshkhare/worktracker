package com.septuary.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.septuary.app.data.FlagEntity
import com.septuary.app.data.GoalEntity
import com.septuary.app.data.Repository
import com.septuary.app.ui.theme.*

@Composable
fun GoalsScreen(repo: Repository) {
    var goals by remember { mutableStateOf(listOf<GoalEntity>()) }
    var flags by remember { mutableStateOf(listOf<FlagEntity>()) }
    LaunchedEffect(Unit) {
        goals = repo.goals()
        flags = repo.flags()
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Card(title = "Targets") {
            goals.forEach { g ->
                Column(Modifier.padding(bottom = 10.dp)) {
                    Text(g.metric, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Baseline: ${g.baseline}", color = TextMuted, fontSize = 12.sp)
                    Text("Phase I: ${g.phase1Target}", color = TextMuted, fontSize = 12.sp)
                    Text("Long-term: ${g.longTerm}", color = TextMuted, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(title = "Open Flags") {
            flags.forEach { f ->
                Text(
                    (if (f.resolved) "✓ " else "● ") + f.text,
                    color = if (f.resolved) TextMuted else Color(0xFFF5A623),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}
