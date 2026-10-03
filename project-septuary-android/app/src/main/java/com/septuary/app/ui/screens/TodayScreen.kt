package com.septuary.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.septuary.app.data.Repository
import com.septuary.app.ui.MotivationQuotes
import com.septuary.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

fun formatTime12h(t: String): String {
    val (h, m) = t.split(":").map { it.toInt() }
    val ampm = if (h >= 12) "PM" else "AM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, ampm)
}

@Composable
fun TodayScreen(repo: Repository) {
    val context = LocalContext.current
    var doses by remember { mutableStateOf(listOf<Repository.Dose>()) }
    var done by remember { mutableStateOf(setOf<String>()) }
    var photoPaths by remember { mutableStateOf(mapOf<String, String>()) }
    val scope = rememberCoroutineScope()

    // Non-blocking "Add a photo?" prompt shown after a food/coffee/tea/meal item is marked
    // done — never gates the toggle itself, which has already completed by the time this shows.
    var pendingPhotoDoseKey by remember { mutableStateOf<String?>(null) }
    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    var viewingPhotoPath by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        doses = repo.todayDoses()
        done = repo.todayLog()
        photoPaths = repo.todayPhotoPaths()
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val doseKey = pendingPhotoDoseKey
        val file = pendingPhotoFile
        pendingPhotoDoseKey = null
        pendingPhotoFile = null
        if (success && doseKey != null && file != null) {
            scope.launch {
                repo.attachDosePhoto(doseKey, "dose_photos/${file.name}")
                reload()
            }
        }
    }

    fun launchCameraFor(doseKey: String) {
        val file = repo.newDosePhotoFile(doseKey)
        pendingPhotoFile = file
        val uri: Uri = FileProvider.getUriForFile(context, "com.septuary.app.fileprovider", file)
        cameraLauncher.launch(uri)
    }

    fun handleToggle(dose: Repository.Dose, wasDone: Boolean) {
        scope.launch {
            repo.toggleDose(dose.doseKey, wasDone)
            reload()
            // Only offer a photo when a non-medication item is going TO done, never on un-toggle.
            if (!wasDone && dose.category != "medication") {
                pendingPhotoDoseKey = dose.doseKey
            } else if (pendingPhotoDoseKey == dose.doseKey) {
                pendingPhotoDoseKey = null
            }
        }
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
            photoPaths = photoPaths,
            pendingPhotoDoseKey = pendingPhotoDoseKey,
            onToggle = ::handleToggle,
            onAddPhoto = {},
            onSkipPhoto = { pendingPhotoDoseKey = null },
            onViewPhoto = { viewingPhotoPath = it }
        )

        Spacer(Modifier.height(12.dp))

        DoseSection(
            title = "Daily Reminders",
            subtitle = "${reminders.count { done.contains(it.doseKey) }}/${reminders.size}",
            doses = reminders, done = done,
            photoPaths = photoPaths,
            pendingPhotoDoseKey = pendingPhotoDoseKey,
            onToggle = ::handleToggle,
            onAddPhoto = { doseKey -> launchCameraFor(doseKey) },
            onSkipPhoto = { pendingPhotoDoseKey = null },
            onViewPhoto = { viewingPhotoPath = it }
        )
    }

    viewingPhotoPath?.let { path ->
        val file = File(context.filesDir, path)
        Dialog(onDismissRequest = { viewingPhotoPath = null }) {
            val bitmap = remember(path) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }
            bitmap?.let {
                Image(
                    it, contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableSimple { viewingPhotoPath = null }
                )
            }
        }
    }
}

@Composable
private fun DoseSection(
    title: String,
    subtitle: String,
    doses: List<Repository.Dose>,
    done: Set<String>,
    photoPaths: Map<String, String> = emptyMap(),
    pendingPhotoDoseKey: String? = null,
    onToggle: (Repository.Dose, Boolean) -> Unit,
    onAddPhoto: (String) -> Unit = {},
    onSkipPhoto: () -> Unit = {},
    onViewPhoto: (String) -> Unit = {}
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
                val photoPath = photoPaths[dose.doseKey]
                Column(Modifier.fillMaxWidth()) {
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
                                .clickableSimple { onToggle(dose, isDone) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDone) Text("✓", color = Bg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(dose.name, color = if (isDone) TextMuted else TextMain, fontSize = 15.sp)
                            Text(dose.detail, color = TextMuted, fontSize = 12.sp)
                        }
                        if (photoPath != null) {
                            DosePhotoThumbnail(
                                photoPath = photoPath,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickableSimple { onViewPhoto(photoPath) }
                            )
                        }
                        Text(formatTime12h(dose.time), color = TextMuted, fontSize = 12.sp)
                    }
                    if (pendingPhotoDoseKey == dose.doseKey) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp + 26.dp + 12.dp, end = 14.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Add a photo?", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(
                                "Yes", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickableSimple { onAddPhoto(dose.doseKey) }
                            )
                            Text(
                                "Skip", color = TextMuted, fontSize = 13.sp,
                                modifier = Modifier.clickableSimple { onSkipPhoto() }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Decodes a small local JPEG straight off disk — no Coil/Glide, this is one thumbnail at a
 *  time from a known app-private file, which doesn't need a caching/loading library. */
@Composable
private fun DosePhotoThumbnail(photoPath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(photoPath) {
        BitmapFactory.decodeFile(File(context.filesDir, photoPath).path)?.asImageBitmap()
    }
    bitmap?.let { Image(it, contentDescription = null, modifier = modifier) }
}

// Small helper so we don't need to import the full clickable() boilerplate at every call site.
fun Modifier.clickableSimple(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
