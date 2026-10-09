package com.septuary.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.septuary.app.Session
import com.septuary.app.data.FamilyLink
import com.septuary.app.data.SyncRepository
import com.septuary.app.ui.theme.*

/**
 * Shows the private family code the parents enter once in the Supervisor app, with Share/Copy,
 * plus a way to re-use an old code after a reinstall so the parents never need to re-pair.
 */
@Composable
fun FamilyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var code by remember { mutableStateOf(FamilyLink.code(context)) }
    var replacing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Panel, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(22.dp)) {
                Text("Family sharing", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your parents enter this code once in the Supervisor app. Only someone with the code can see " +
                        "today's medicine, food and exercise status. Share it only with them.",
                    color = TextMuted, fontSize = 13.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Panel2, RoundedCornerShape(14.dp))
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        FamilyLink.pretty(code), color = Accent, fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Project Septuary family code: ${FamilyLink.pretty(code)}")
                            }
                            try {
                                context.startActivity(Intent.createChooser(send, "Share family code"))
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
                    ) { Text("Share") }
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Family code", FamilyLink.pretty(code)))
                            copied = true
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (copied) "Copied" else "Copy", color = TextMain) }
                }

                Spacer(Modifier.height(18.dp))
                if (!replacing) {
                    TextButton(onClick = { replacing = true }) {
                        Text("Reinstalled? Use your previous code", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it; error = "" },
                        placeholder = { Text("XXXX-XXXX-XXXX-XXXX-XXXX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors()
                    )
                    if (error.isNotEmpty()) Text(error, color = Danger, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { replacing = false; input = ""; error = "" }) { Text("Cancel", color = TextMuted) }
                        TextButton(onClick = {
                            if (FamilyLink.setCode(context, input)) {
                                code = FamilyLink.code(context)
                                replacing = false
                                input = ""
                                Session.repo?.let { SyncRepository.schedulePush(it) }
                            } else {
                                error = "That doesn't look like a valid ${FamilyLink.CODE_LENGTH}-character code."
                            }
                        }) { Text("Use code", color = Accent) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Done", color = Accent) }
                }
            }
        }
    }
}
