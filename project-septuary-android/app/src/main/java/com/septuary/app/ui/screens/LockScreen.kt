package com.septuary.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.septuary.app.ui.theme.*
import kotlin.math.roundToInt

private enum class LockMode { SETUP_FIRST, SETUP_CONFIRM, UNLOCK }

const val PIN_LENGTH = 4

@Composable
fun LockScreen(
    isFirstRun: Boolean,
    busy: Boolean,
    errorMessage: String?,
    errorTick: Int,
    lockoutSeconds: Int,
    biometricEnabled: Boolean,
    onPinReady: (pin: String, isSetup: Boolean) -> Unit,
    onBiometric: () -> Unit
) {
    var mode by remember(isFirstRun) { mutableStateOf(if (isFirstRun) LockMode.SETUP_FIRST else LockMode.UNLOCK) }
    var buffer by remember { mutableStateOf("") }
    var firstEntry by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }

    // Each failure (errorTick changes) clears the dots and shakes them.
    LaunchedEffect(errorTick) {
        if (errorTick > 0) {
            buffer = ""
            localError = errorMessage ?: ""
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            for (x in listOf(18f, -16f, 12f, -8f, 4f, 0f)) shake.animateTo(x, tween(45))
        }
    }

    val title = when (mode) {
        LockMode.SETUP_FIRST -> "Create a PIN"
        LockMode.SETUP_CONFIRM -> "Confirm your PIN"
        LockMode.UNLOCK -> "Project Septuary"
    }
    val sub = when (mode) {
        LockMode.SETUP_FIRST -> "Four digits. It encrypts everything on this phone and is never stored or sent anywhere."
        LockMode.SETUP_CONFIRM -> "Enter the same four digits again."
        LockMode.UNLOCK -> if (biometricEnabled) "Touch the sensor or enter your PIN" else "Enter your PIN"
    }

    val lockedOut = lockoutSeconds > 0
    val inputEnabled = !busy && !lockedOut

    fun submit() {
        when (mode) {
            LockMode.SETUP_FIRST -> {
                firstEntry = buffer
                buffer = ""
                mode = LockMode.SETUP_CONFIRM
            }
            LockMode.SETUP_CONFIRM -> {
                if (buffer != firstEntry) {
                    localError = "Those didn't match. Start again."
                    buffer = ""
                    firstEntry = ""
                    mode = LockMode.SETUP_FIRST
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } else {
                    onPinReady(buffer, true)
                }
            }
            LockMode.UNLOCK -> onPinReady(buffer, false)
        }
    }

    fun press(d: String) {
        if (!inputEnabled) return
        localError = ""
        if (buffer.length < PIN_LENGTH) {
            buffer += d
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (buffer.length == PIN_LENGTH) submit()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(sub, color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
        Spacer(Modifier.height(28.dp))

        Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Row(
                    Modifier.offset { IntOffset(shake.value.roundToInt(), 0) },
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    repeat(PIN_LENGTH) { i ->
                        val filled = i < buffer.length
                        Box(
                            Modifier
                                .size(14.dp)
                                .background(if (filled) Accent else Bg, CircleShape)
                                .border(2.dp, if (filled) Accent else Border, CircleShape)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val status = when {
            lockedOut -> "Too many attempts. Try again in ${lockoutSeconds}s."
            busy -> if (mode == LockMode.UNLOCK) "Unlocking…" else "Securing your data…"
            else -> localError
        }
        Text(
            status, color = if (lockedOut || localError.isNotEmpty()) Danger else TextMuted,
            fontSize = 13.sp, modifier = Modifier.height(20.dp), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del"))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    row.forEach { key ->
                        when (key) {
                            "bio" -> KeyButton(enabled = biometricEnabled && mode == LockMode.UNLOCK && inputEnabled, onClick = onBiometric) {
                                if (biometricEnabled && mode == LockMode.UNLOCK) {
                                    Icon(Icons.Filled.Fingerprint, contentDescription = "Use fingerprint", tint = Accent, modifier = Modifier.size(28.dp))
                                }
                            }
                            "del" -> KeyButton(enabled = inputEnabled && buffer.isNotEmpty(), onClick = { buffer = buffer.dropLast(1) }) {
                                if (buffer.isNotEmpty()) {
                                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(22.dp))
                                }
                            }
                            else -> KeyButton(enabled = inputEnabled, filled = true, onClick = { press(key) }) {
                                Text(key, color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Light)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(enabled: Boolean, filled: Boolean = false, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .size(74.dp)
            .clip(CircleShape)
            .background(if (filled) Panel2 else Bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}
