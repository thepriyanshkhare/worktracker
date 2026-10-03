package com.septuary.app.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.septuary.app.ui.theme.Bg
import com.septuary.app.ui.theme.SeptuaryTheme
import com.septuary.app.ui.theme.TextMain
import com.septuary.app.ui.theme.TextMuted

/**
 * Health Connect requires every app requesting health permissions to declare an activity that
 * explains, in plain language, what the data is used for — normally a link to a hosted privacy
 * policy. Project Septuary is a personal, side-loaded app (never distributed via Play Store),
 * so this is a trivial local explanation screen rather than a hosted URL. Declared in
 * AndroidManifest.xml for androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SeptuaryTheme {
                Surface(color = Bg) {
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        Text("How Project Septuary uses Health Connect", color = TextMain, fontSize = 18.sp)
                        Text(
                            "\nThis app reads your Weight and Steps from Health Connect to auto-fill the same " +
                                "daily log you could otherwise enter by hand. Nothing is written back to Health " +
                                "Connect, and no other data type (sleep, heart rate, etc.) is read.\n\n" +
                                "This data is stored only in this app's own encrypted, on-device database — " +
                                "the same place your manually logged weight already lives — and is shared only " +
                                "the way your other logged data already is (a brief summary visible to the " +
                                "Supervisor app your family uses). It is never sold, analyzed, or sent anywhere else.\n\n" +
                                "You can revoke this permission at any time from Android's Health Connect settings.",
                            color = TextMuted, fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
