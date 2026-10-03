package com.septuary.supervisor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Oct 2026 security hardening: must match the Septuary app's SyncRepository.ROOT_COLLECTION,
// firestore.rules, and scripts/push_to_septuary.py exactly. If this token is ever leaked,
// rotate it in all four places plus the rules.
private const val ROOT_COLLECTION = "septuary_86800832c1af658f9d30a04276952c81"

private val Bg = Color(0xFF0E1116)
private val Panel = Color(0xFF171B22)
private val TextMain = Color(0xFFF2F4F7)
private val TextMuted = Color(0xFF8B95A5)
private val Green = Color(0xFF3FB86F)
private val Amber = Color(0xFFE0A63A)
private val Red = Color(0xFFE0543A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Panel)) {
                Surface(color = Bg) {
                    StatusScreen()
                }
            }
        }
    }
}

data class SectionStatus(val label: String, val wire: String?) {
    val display: String get() = when (wire) {
        "done" -> "Done"
        "pending" -> "Pending"
        "not_done" -> "Not Done"
        else -> "No data yet"
    }
    val color: Color get() = when (wire) {
        "done" -> Green
        "pending" -> Amber
        "not_done" -> Red
        else -> TextMuted
    }
}

/** One medicine or food item as reported in septuary/detail. */
data class DetailItem(val name: String, val label: String, val done: Boolean)

/**
 * This app reads two small Firestore documents the Septuary app pushes to: the status doc
 * (an at-a-glance tri-state summary) and the detail doc (actual medicine/food names,
 * exercise, and recent weight) — a deliberate, explicit choice to show his parents real
 * detail rather than only Done/Pending/Not Done. Both documents require anonymous auth to
 * read (see firestore.rules) — this app signs in before attaching either listener.
 */
@Composable
fun StatusScreen() {
    var date by remember { mutableStateOf<String?>(null) }
    var medicine by remember { mutableStateOf<String?>(null) }
    var food by remember { mutableStateOf<String?>(null) }
    var exercise by remember { mutableStateOf<String?>(null) }
    var updatedAtMillis by remember { mutableStateOf<Long?>(null) }
    var connected by remember { mutableStateOf(true) }

    var medicineItems by remember { mutableStateOf(listOf<DetailItem>()) }
    var foodItems by remember { mutableStateOf(listOf<DetailItem>()) }
    var exerciseItems by remember { mutableStateOf(listOf<Pair<String, Int>>()) } // type to minutes
    var recentWeights by remember { mutableStateOf(listOf<Double>()) } // oldest first

    // Anonymous sign-in required before either listener can read anything (firestore.rules
    // requires request.auth != null). No credentials or UI — this is silent and automatic.
    var authReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            if (FirebaseAuth.getInstance().currentUser == null) {
                Tasks.await(FirebaseAuth.getInstance().signInAnonymously())
            }
            authReady = true
        } catch (_: Exception) {
            connected = false // surfaces as the existing "No connection" banner below
        }
    }

    if (authReady) {
        DisposableEffect(Unit) {
            val reg: ListenerRegistration = FirebaseFirestore.getInstance()
                .document("$ROOT_COLLECTION/status")
                .addSnapshotListener { snap, error ->
                    connected = error == null
                    if (snap != null && snap.exists()) {
                        date = snap.getString("date")
                        medicine = snap.getString("medicine")
                        food = snap.getString("food")
                        exercise = snap.getString("exercise")
                        updatedAtMillis = snap.getTimestamp("updatedAt")?.toDate()?.time
                    }
                }
            onDispose { reg.remove() }
        }

        DisposableEffect(Unit) {
            val reg: ListenerRegistration = FirebaseFirestore.getInstance()
                .document("$ROOT_COLLECTION/detail")
                .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) return@addSnapshotListener
                @Suppress("UNCHECKED_CAST")
                val rawMedicine = snap.get("medicineItems") as? List<Map<String, Any>> ?: emptyList()
                medicineItems = rawMedicine.map {
                    DetailItem(
                        name = it["name"] as? String ?: "",
                        label = it["time"] as? String ?: "",
                        done = it["done"] as? Boolean ?: false
                    )
                }
                @Suppress("UNCHECKED_CAST")
                val rawFood = snap.get("foodItems") as? List<Map<String, Any>> ?: emptyList()
                foodItems = rawFood.map {
                    val category = (it["category"] as? String)?.replaceFirstChar { c -> c.uppercase() } ?: ""
                    val time = it["time"] as? String ?: ""
                    DetailItem(
                        name = it["name"] as? String ?: "",
                        label = listOf(category, time).filter { s -> s.isNotBlank() }.joinToString(" — "),
                        done = it["done"] as? Boolean ?: false
                    )
                }
                @Suppress("UNCHECKED_CAST")
                val rawExercise = snap.get("exercise") as? List<Map<String, Any>> ?: emptyList()
                exerciseItems = rawExercise.map {
                    (it["type"] as? String ?: "") to ((it["minutes"] as? Long)?.toInt() ?: 0)
                }
                // Pushed newest-first (Repository.weightLog() is DESC) — reverse so the
                // sparkline/delta read left-to-right as oldest -> newest, chronologically.
                @Suppress("UNCHECKED_CAST")
                val rawWeights = snap.get("recentWeights") as? List<Map<String, Any>> ?: emptyList()
                recentWeights = rawWeights
                    .mapNotNull { (it["kg"] as? Double) ?: (it["kg"] as? Long)?.toDouble() }
                    .reversed()
            }
            onDispose { reg.remove() }
        }
    }

    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val isStale = date != null && date != today

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text("Priyansh — Today", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("View only · updates live from his phone", color = TextMuted, fontSize = 12.sp)

        Spacer(Modifier.height(24.dp))

        if (!connected) {
            Banner("No connection — showing the last update received.", Amber)
            Spacer(Modifier.height(12.dp))
        } else if (isStale) {
            Banner("This is from $date, not today — his app hasn't reported in yet.", Amber)
            Spacer(Modifier.height(12.dp))
        } else if (date == null) {
            Banner("Waiting for his first update.", TextMuted)
            Spacer(Modifier.height(12.dp))
        }

        SectionRow(SectionStatus("Medicine", medicine))
        Spacer(Modifier.height(12.dp))
        SectionRow(SectionStatus("Food", food))
        Spacer(Modifier.height(12.dp))
        SectionRow(SectionStatus("Exercise", exercise))

        Spacer(Modifier.height(20.dp))

        if (medicineItems.isNotEmpty()) {
            DetailSection("Medicines", medicineItems)
            Spacer(Modifier.height(16.dp))
        }
        if (foodItems.isNotEmpty()) {
            DetailSection("Food & Drinks", foodItems)
            Spacer(Modifier.height(16.dp))
        }

        ExerciseSection(exerciseItems)
        Spacer(Modifier.height(16.dp))
        WeightSection(recentWeights)

        Spacer(Modifier.height(20.dp))
        RemindMeButton(authReady)

        Spacer(Modifier.weight(1f))

        val lastUpdatedText = updatedAtMillis?.let {
            "Last updated " + SimpleDateFormat("h:mm a, MMM d", Locale.US).format(Date(it))
        } ?: "Not synced yet"
        Text(lastUpdatedText, color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
fun Banner(text: String, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextMain, fontSize = 13.sp)
    }
}

/** A titled list of medicine or food items, each styled like a smaller [SectionRow]. */
@Composable
fun DetailSection(title: String, items: List<DetailItem>) {
    Text(title.uppercase(), color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
    ) {
        items.forEach { item -> DetailListRow(item) }
    }
}

@Composable
fun DetailListRow(item: DetailItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(item.name, color = TextMain, fontSize = 14.sp)
            if (item.label.isNotBlank()) {
                Text(item.label, color = TextMuted, fontSize = 11.sp)
            }
        }
        Icon(
            if (item.done) Icons.Filled.CheckCircle else Icons.Filled.Circle,
            contentDescription = null,
            tint = if (item.done) Green else TextMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun ExerciseSection(items: List<Pair<String, Int>>) {
    Text("EXERCISE", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .padding(16.dp)
    ) {
        if (items.isEmpty()) {
            Text("No exercise logged yet today.", color = TextMuted, fontSize = 13.sp)
        } else {
            Column {
                items.forEach { (type, minutes) ->
                    Text("$type — $minutes min", color = TextMain, fontSize = 14.sp)
                }
            }
        }
    }
}

/** Last weight value plus a short hand-drawn trend line — a minimal Canvas sparkline rather
 *  than pulling in a charting library for this single small view. */
@Composable
fun WeightSection(recentWeights: List<Double>) {
    Text("WEIGHT", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .padding(16.dp)
    ) {
        if (recentWeights.isEmpty()) {
            Text("No weight logged yet.", color = TextMuted, fontSize = 13.sp)
        } else {
            val latest = recentWeights.last()
            val summary = if (recentWeights.size > 1) {
                val delta = latest - recentWeights.first()
                val arrow = if (delta > 0) "▲" else if (delta < 0) "▼" else "–"
                "%.1f kg today (%s %.1f over last %d entries)".format(latest, arrow, kotlin.math.abs(delta), recentWeights.size)
            } else {
                "%.1f kg".format(latest)
            }
            Text(summary, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (recentWeights.size > 1) {
                Spacer(Modifier.height(10.dp))
                WeightSparkline(recentWeights)
            }
        }
    }
}

@Composable
private fun WeightSparkline(values: List<Double>) {
    val lineColor = Green
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val points = values.mapIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / range * size.height).toFloat()
            Offset(x, y)
        }
        for (i in 0 until points.size - 1) {
            drawLine(lineColor, points[i], points[i + 1], strokeWidth = 4f)
        }
    }
}

/**
 * This app's first-ever Firestore *write* (everything else here is read-only). Writes a small
 * flag doc to the reminders collection; the Septuary app drains it on next unlock, shows a
 * local notification, then deletes the doc so it fires exactly once. Deliberately NOT a true
 * push (no Cloud Function/FCM) — Priyansh chose this simpler in-app delivery once told a true
 * push would require enabling Firebase's paid Blaze plan.
 */
@Composable
fun RemindMeButton(authReady: Boolean) {
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var sentJustNow by remember { mutableStateOf(false) }

    LaunchedEffect(sentJustNow) {
        if (sentJustNow) {
            kotlinx.coroutines.delay(4000)
            sentJustNow = false
        }
    }

    Button(
        enabled = authReady && !sending,
        onClick = {
            scope.launch {
                sending = true
                try {
                    Tasks.await(
                        FirebaseFirestore.getInstance()
                            .collection("${ROOT_COLLECTION}_reminders")
                            .add(mapOf("createdAt" to FieldValue.serverTimestamp()))
                    )
                    sentJustNow = true
                } catch (_: Exception) {
                    // Best-effort, same posture as everything else here — no error UI for a personal app.
                } finally {
                    sending = false
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (sentJustNow) "Reminder sent" else "Remind me")
    }
}

@Composable
fun SectionRow(status: SectionStatus) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(status.label, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (status.wire == "done") Icons.Filled.CheckCircle else Icons.Filled.Circle,
                contentDescription = null,
                tint = status.color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(status.display, color = status.color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
