package com.septuary.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.septuary.app.data.Repository
import com.septuary.app.data.Trends
import com.septuary.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private enum class Period(val label: String, val days: Int) { DAILY("Daily", 1), WEEKLY("Weekly", 7), MONTHLY("Monthly", 30) }

private val WarnColor = Color(0xFFF5A623) // matches the amber already used for open flags on Goals

/** 100 = best possible outcome for that category that day; null = nothing scheduled/logged. */
private fun bandColor(score: Int?): Color = when {
    score == null -> Border
    score >= 80 -> Accent
    score >= 50 -> WarnColor
    else -> Danger
}

private fun scoreLabel(score: Int?): String = if (score == null) "—" else "$score%"

@Composable
fun TrendsScreen(repo: Repository) {
    var period by remember { mutableStateOf(Period.DAILY) }
    var raw by remember { mutableStateOf<Trends.RawData?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        raw = repo.trendsData(30) // always fetch the full 30-day window; slice per period below
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Performance Trends", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "100 = best possible outcome that day. Medicine & Food are adherence to what was " +
                "scheduled; Exercise is minutes vs. a ${Trends.EXERCISE_DAILY_TARGET_MIN}min/day target; " +
                "Sleep blends hours-vs-target with how you rated it.",
            color = TextMuted, fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Period.values().forEach { p ->
                FilterChipSimple(p.label, selected = period == p) { period = p }
            }
        }
        Spacer(Modifier.height(14.dp))

        val data = raw
        if (data == null) {
            Text("Loading…", color = TextMuted, fontSize = 13.sp)
            return@Column
        }

        when (period) {
            Period.DAILY -> DailyTrends(repo, data)
            Period.WEEKLY -> WeeklyTrends(repo, data)
            Period.MONTHLY -> MonthlyTrends(repo, data)
        }
    }
}

@Composable
private fun DailyTrends(repo: Repository, data: Trends.RawData) {
    val today = repo.todayKey()
    val score = remember(data) {
        Trends.computeDailyScores(listOf(today), data, repo::isMedActiveOnDate).first()
    }
    Card(title = "Today") {
        CategoryBigBar("Medicine", score.medicine)
        Spacer(Modifier.height(10.dp))
        CategoryBigBar("Food", score.food)
        Spacer(Modifier.height(10.dp))
        CategoryBigBar("Exercise", score.exercise)
        Spacer(Modifier.height(10.dp))
        CategoryBigBar("Sleep", score.sleep)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("OVERALL", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("${score.overall}%", color = bandColor(score.overall), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CategoryBigBar(label: String, score: Int?) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextMain, fontSize = 13.sp)
            Text(scoreLabel(score), color = bandColor(score), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(Panel2, RoundedCornerShape(5.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(((score ?: 0).coerceIn(0, 100)) / 100f)
                    .fillMaxHeight()
                    .background(bandColor(score), RoundedCornerShape(5.dp))
            )
        }
    }
}

@Composable
private fun WeeklyTrends(repo: Repository, data: Trends.RawData) {
    val dates = remember { (6 downTo 0).map { repo.dateDaysAgo(it) } }
    val scores = remember(data) { Trends.computeDailyScores(dates, data, repo::isMedActiveOnDate) }
    val labels = remember(dates) { dates.map { shortDay(it) } }

    Card(title = "Last 7 Days") {
        CategoryWeekRow("Medicine", labels, scores.map { it.medicine }, Trends.average(scores) { it.medicine })
        Spacer(Modifier.height(16.dp))
        CategoryWeekRow("Food", labels, scores.map { it.food }, Trends.average(scores) { it.food })
        Spacer(Modifier.height(16.dp))
        CategoryWeekRow("Exercise", labels, scores.map { it.exercise }, Trends.average(scores) { it.exercise })
        Spacer(Modifier.height(16.dp))
        CategoryWeekRow("Sleep", labels, scores.map { it.sleep }, Trends.average(scores) { it.sleep })
        Spacer(Modifier.height(16.dp))
        val overallAvg = if (scores.isEmpty()) 0 else scores.sumOf { it.overall } / scores.size
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("WEEKLY OVERALL", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("$overallAvg%", color = bandColor(overallAvg), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MonthlyTrends(repo: Repository, data: Trends.RawData) {
    val dates = remember { (29 downTo 0).map { repo.dateDaysAgo(it) } }
    val scores = remember(data) { Trends.computeDailyScores(dates, data, repo::isMedActiveOnDate) }
    // Bucket the 30 days into ~5 weekly chunks, oldest first, so the chart stays readable.
    val buckets = remember(scores) { scores.chunked(7) }
    val bucketLabels = remember(buckets) { buckets.mapIndexed { i, _ -> "W${i + 1}" } }

    Card(title = "Last 30 Days (by week)") {
        CategoryWeekRow(
            "Medicine", bucketLabels,
            buckets.map { b -> Trends.average(b) { it.medicine } },
            Trends.average(scores) { it.medicine }
        )
        Spacer(Modifier.height(16.dp))
        CategoryWeekRow(
            "Food", bucketLabels,
            buckets.map { b -> Trends.average(b) { it.food } },
            Trends.average(scores) { it.food }
        )
        Spacer(Modifier.height(16.dp))
        CategoryWeekRow(
            "Exercise", bucketLabels,
            buckets.map { b -> Trends.average(b) { it.exercise } },
            Trends.average(scores) { it.exercise }
        )
        Spacer(Modifier.height(16.dp))
        CategoryWeekRow(
            "Sleep", bucketLabels,
            buckets.map { b -> Trends.average(b) { it.sleep } },
            Trends.average(scores) { it.sleep }
        )
        Spacer(Modifier.height(16.dp))
        val overallAvg = if (scores.isEmpty()) 0 else scores.sumOf { it.overall } / scores.size
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("MONTHLY OVERALL", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("$overallAvg%", color = bandColor(overallAvg), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** One category's label + running average, with a small per-bucket bar chart underneath. */
@Composable
private fun CategoryWeekRow(label: String, labels: List<String>, values: List<Int?>, average: Int?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextMain, fontSize = 13.sp)
        Text(scoreLabel(average), color = bandColor(average), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.55f)
                            .fillMaxHeight(((v ?: 0).coerceIn(0, 100)) / 100f)
                            .background(bandColor(v), RoundedCornerShape(3.dp))
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(labels.getOrElse(i) { "" }, color = TextMuted, fontSize = 9.sp)
            }
        }
    }
}

private fun shortDay(dateStr: String): String = try {
    val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)
    if (d != null) SimpleDateFormat("EEE", Locale.US).format(d) else dateStr.takeLast(2)
} catch (e: Exception) {
    dateStr.takeLast(2)
}
