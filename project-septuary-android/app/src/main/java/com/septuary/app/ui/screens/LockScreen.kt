package com.septuary.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.septuary.app.ui.theme.*

enum class LockMode { SETUP_FIRST, SETUP_CONFIRM, UNLOCK }

@Composable
fun LockScreen(
    isFirstRun: Boolean,
    onPinReady: (pin: String, isSetup: Boolean) -> Unit,
    errorMessage: String?
) {
    var mode by remember { mutableStateOf(if (isFirstRun) LockMode.SETUP_FIRST else LockMode.UNLOCK) }
    var buffer by remember { mutableStateOf("") }
    var firstEntry by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf(errorMessage ?: "") }

    LaunchedEffect(errorMessage) { if (errorMessage != null) { localError = errorMessage; buffer = "" } }

    val title = when (mode) {
        LockMode.SETUP_FIRST -> "Set a PIN"
        LockMode.SETUP_CONFIRM -> "Confirm your PIN"
        LockMode.UNLOCK -> "Enter your PIN"
    }
    val sub = when (mode) {
        LockMode.SETUP_FIRST -> "This encrypts everything on this device. It's never stored or sent anywhere — only you know it."
        LockMode.SETUP_CONFIRM -> "Enter it again to confirm."
        LockMode.UNLOCK -> "Project Septuary is locked."
    }

    fun submit() {
        when (mode) {
            LockMode.SETUP_FIRST -> {
                firstEntry = buffer
                buffer = ""
                mode = LockMode.SETUP_CONFIRM
            }
            LockMode.SETUP_CONFIRM -> {
                if (buffer != firstEntry) {
                    localError = "PINs didn't match. Start over."
                    buffer = ""
                    firstEntry = ""
                    mode = LockMode.SETUP_FIRST
                } else {
                    onPinReady(buffer, true)
                }
            }
            LockMode.UNLOCK -> {
                onPinReady(buffer, false)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(sub, color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (i in 0 until maxOf(4, buffer.length)) {
                val filled = i < buffer.length
                Box(
                    Modifier
                        .size(14.dp)
                        .background(if (filled) Accent else Bg, CircleShape)
                        .border(2.dp, if (filled) Accent else Border, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(localError, color = Danger, fontSize = 13.sp, modifier = Modifier.height(18.dp))
        Spacer(Modifier.height(16.dp))

        val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            digits.chunked(3).forEach { rowDigits ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    rowDigits.forEach { d ->
                        if (d.isEmpty()) {
                            Box(Modifier.size(64.dp))
                        } else {
                            Button(
                                onClick = {
                                    localError = ""
                                    if (d == "⌫") {
                                        buffer = buffer.dropLast(1)
                                    } else if (buffer.length < 8) {
                                        buffer += d
                                        if (buffer.length == 4) submit()
                                    }
                                },
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = Panel2, contentColor = TextMain)
                            ) { Text(d, fontSize = 20.sp) }
                        }
                    }
                }
            }
        }
    }
}
