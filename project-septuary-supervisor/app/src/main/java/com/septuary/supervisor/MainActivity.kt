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
import androidx.compose.ui.draw.alpha
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.text.SimpleDateFormat
import java.util.*

// The family code is entered once by the parents (shared from Priyansh's app) and kept only on
// this phone. Nothing secret is compiled into this app or stored in the public repository.
private const val PREFS = "supervisor"
private const val KEY_CODE = "family_code"
private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
private const val CODE_LENGTH = 20

private fun normalizeCode(raw: String) = raw.uppercase().filter { it in ALPHABET }

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
                Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
                    val prefs = remember { getSharedPreferences(PREFS, MODE_PRIVATE) }
                    var code by remember { mutableStateOf(prefs.getString(KEY_CODE, null)) }
                    val current = code
                    if (current == null) {
                        PairingScreen { entered ->
                            prefs.edit().putString(KEY_CODE, entered).apply()
                            code = entered
                        }
                    } else {
                        key(current) {
                            StatusScreen(root = "sp_$current", onUnpair = {
                                prefs.edit().remove(KEY_CODE).apply()
                                code = null
                            })
                        }
                    }
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

/** One medicine or food item as reported in septuary/detail. [dueToday] is always true for
 *  food items (already filtered to today); for medicine it reflects the full standing routine
 *  — a medicine not due today (e.g. a weekly one, off-day) still shows, just dimmed. */
data class DetailItem(val name: String, val label: String, val done: Boolean, val dueToday: Boolean = true)

/**
 * This app reads two small Firestore documents the Septuary app pushes to: the status doc
 * (an at-a-glance tri-state summary) and the detail doc (actual medicine/food names,
 * exercise, and recent weight) — a deliberate, explicit choice to show his parents real
 * detail rather than only Done/Pending/Not Done. Both documents require anonymous auth to
 * read (see firestore.rules) — this app signs in before attaching either listener.
 */
@Composable
fun StatusScreen(root: String, onUnpair: () -> Unit) {
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
        // Retry until signed in: Tasks.await must run off the main thread (calling it on the
        // main thread throws, which previously showed a permanent "No connection").
        while (!authReady) {
            try {
                withContext(Dispatchers.IO) {
                    if (FirebaseAuth.getInstance().currentUser == null) {
                        Tasks.await(FirebaseAuth.getInstance().signInAnonymously())
                    }
                }
                authReady = true
                connected = true
            } catch (_: Exception) {
                connected = false
                kotlinx.coroutines.delay(5_000)
            }
        }
    }

    if (authReady) {
        DisposableEffect(Unit) {
            val reg: ListenerRegistration = FirebaseFirestore.getInstance()
                .document("$root/status")
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
                .document("$root/detail")
                .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) return@addSnapshotListener
                @Suppress("UNCHECKED_CAST")
                val rawMedicine = snap.get("medicineItems") as? List<Map<String, Any>> ?: emptyList()
                medicineItems = rawMedicine.map {
                    val dueToday = it["dueToday"] as? Boolean ?: true
                    val schedule = it["schedule"] as? String ?: ""
                    val time = it["time"] as? String ?: ""
                    val label = listOf(schedule, time).filter { s -> s.isNotBlank() }.joinToString(" — ") +
                        (if (!dueToday) " · not due today" else "")
                    DetailItem(
                        name = it["name"] as? String ?: "",
                        label = label,
                        done = it["done"] as? Boolean ?: false,
                        dueToday = dueToday
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
            .verticalScroll(rememberScrollState())
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

        DetailSection("Medicines", medicineItems, emptyText = "No medicines scheduled yet.")
        Spacer(Modifier.height(16.dp))
        DetailSection("Food & Drinks", foodItems, emptyText = "No food or drinks logged yet.")
        Spacer(Modifier.height(16.dp))

        ExerciseSection(exerciseItems)
        Spacer(Modifier.height(16.dp))
        WeightSection(recentWeights)

        Spacer(Modifier.height(20.dp))
        RemindMeButton(authReady, root)

        Spacer(Modifier.height(20.dp))

        val lastUpdatedText = updatedAtMillis?.let {
            "Last updated " + SimpleDateFormat("h:mm a, MMM d", Locale.US).format(Date(it))
        } ?: "Not synced yet"
        Text(lastUpdatedText, color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onUnpair, contentPadding = PaddingValues(0.dp)) {
            Text("Change family code", color = TextMuted, fontSize = 12.sp)
        }
    }
}

/** First launch: the parent pastes or types the family code shared from Priyansh's app. */
@Composable
fun PairingScreen(onPaired: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Connect to Priyansh", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter the family code he shared with you. You only need to do this once.",
            color = TextMuted, fontSize = 14.sp
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; error = "" },
            placeholder = { Text("XXXX-XXXX-XXXX-XXXX-XXXX") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(error, color = Red, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val code = normalizeCode(input.substringAfter(":"))
                if (code.length == CODE_LENGTH) onPaired(code) else error = "Please check the code — it has 20 letters and numbers."
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green)
        ) { Text("Connect", fontSize = 16.sp) }
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

/** A titled list of medicine or food items, each styled like a smaller [SectionRow]. Always
 *  renders — an empty list shows [emptyText] instead of disappearing, matching how
 *  [ExerciseSection]/[WeightSection] handle "nothing logged yet" rather than hiding the card. */
@Composable
fun DetailSection(title: String, items: List<DetailItem>, emptyText: String) {
    Text(title.uppercase(), color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    if (items.isEmpty()) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Panel)
                .padding(16.dp)
        ) {
            Text(emptyText, color = TextMuted, fontSize = 13.sp)
        }
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Panel)
        ) {
            items.forEach { item -> DetailListRow(item) }
        }
    }
}

@Composable
fun DetailListRow(item: DetailItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (item.dueToday) 1f else 0.5f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(item.name, color = TextMain, fontSize = 14.sp)
            if (item.label.isNotBlank()) {
                Text(item.label, color = TextMuted, fontSize = 11.sp)
            }
        }
        if (item.dueToday) {
            Icon(
                if (item.done) Icons.Filled.CheckCircle else Icons.Filled.Circle,
                contentDescription = null,
                tint = if (item.done) Green else TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
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
fun RemindMeButton(authReady: Boolean, root: String) {
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
                    withContext(Dispatchers.IO) {
                        Tasks.await(
                            FirebaseFirestore.getInstance()
                                .collection("${root}_reminders")
                                .add(mapOf("createdAt" to FieldValue.serverTimestamp()))
                        )
                    }
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
