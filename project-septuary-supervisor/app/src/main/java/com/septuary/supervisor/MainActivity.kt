package com.septuary.supervisor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

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

/**
 * This app only ever reads one small Firestore document — septuary/status — which the
 * Septuary app pushes to after every dose toggle or exercise log. It never sees
 * medication names, doses, or any actual health values (weight, glucose, notes).
 */
@Composable
fun StatusScreen() {
    var date by remember { mutableStateOf<String?>(null) }
    var medicine by remember { mutableStateOf<String?>(null) }
    var food by remember { mutableStateOf<String?>(null) }
    var exercise by remember { mutableStateOf<String?>(null) }
    var updatedAtMillis by remember { mutableStateOf<Long?>(null) }
    var connected by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val reg: ListenerRegistration = FirebaseFirestore.getInstance()
            .document("septuary/status")
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
