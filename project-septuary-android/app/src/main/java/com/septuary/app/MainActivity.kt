package com.septuary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.septuary.app.crypto.PinCrypto
import com.septuary.app.data.AppDatabase
import com.septuary.app.data.Repository
import com.septuary.app.ui.screens.*
import com.septuary.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SeptuaryTheme {
                Surface(color = Bg) {
                    App()
                }
            }
        }
    }
}

@Composable
fun App() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var repo by remember { mutableStateOf<Repository?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isFirstRun by remember { mutableStateOf(!PinCrypto.hasPinSetup(context)) }
    val scope = rememberCoroutineScope()

    if (repo == null) {
        LockScreen(
            isFirstRun = isFirstRun,
            errorMessage = errorMessage,
            onPinReady = { pin, isSetup ->
                scope.launch {
                    errorMessage = null
                    try {
                        val passphrase = if (isSetup) PinCrypto.setupPin(context, pin) else PinCrypto.deriveForUnlock(context, pin)
                        val db = withContext(Dispatchers.IO) { AppDatabase.open(context, passphrase) }
                        val newRepo = Repository(db, context)
                        // Touch the DB now, inside the try, so a wrong PIN fails HERE (with a
                        // catchable exception) rather than surfacing later as a mysterious crash.
                        withContext(Dispatchers.IO) { db.medicationDao().count() }
                        newRepo.seedIfEmpty()
                        repo = newRepo
                        isFirstRun = false
                        // Drain anything logged from the Claude chat, then push current status —
                        // both run on unlock so parents see today's real state even before
                        // anything's been toggled in the app itself.
                        launch {
                            com.septuary.app.data.SyncRepository.importInbox(newRepo)
                            com.septuary.app.data.SyncRepository.pushTodayStatus(newRepo)
                        }
                    } catch (e: Exception) {
                        errorMessage = if (isSetup) "Something went wrong setting up. Try again." else "Wrong PIN. Try again."
                    }
                }
            }
        )
    } else {
        MainScaffold(repo = repo!!, onLock = {
            repo?.close()
            repo = null
        })
    }
}

private enum class Tab(val label: String) { TODAY("Today"), EXERCISE("Exercise"), TRENDS("Trends"), LOG("Log"), GOALS("Goals"), SETTINGS("Settings") }

@Composable
fun MainScaffold(repo: Repository, onLock: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.TODAY) }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.padding(top = 18.dp, start = 16.dp, end = 16.dp, bottom = 10.dp)) {
            Text("Project Septuary", color = TextMain, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text("Private · on-device · status shared with family", color = TextMuted, fontSize = 12.sp)
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.TODAY -> TodayScreen(repo)
                Tab.EXERCISE -> ExerciseScreen(repo)
                Tab.TRENDS -> TrendsScreen(repo)
                Tab.LOG -> LogScreen(repo)
                Tab.GOALS -> GoalsScreen(repo)
                Tab.SETTINGS -> SettingsScreen(onLock = onLock)
            }
        }

        NavigationBar(containerColor = Panel, contentColor = TextMain) {
            NavigationBarItem(
                selected = tab == Tab.TODAY, onClick = { tab = Tab.TODAY },
                icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                label = { Text("Today") },
                colors = navColors()
            )
            NavigationBarItem(
                selected = tab == Tab.EXERCISE, onClick = { tab = Tab.EXERCISE },
                icon = { Icon(Icons.Filled.FitnessCenter, contentDescription = null) },
                label = { Text("Exercise") },
                colors = navColors()
            )
            NavigationBarItem(
                selected = tab == Tab.TRENDS, onClick = { tab = Tab.TRENDS },
                icon = { Icon(Icons.Filled.TrendingUp, contentDescription = null) },
                label = { Text("Trends") },
                colors = navColors()
            )
            NavigationBarItem(
                selected = tab == Tab.LOG, onClick = { tab = Tab.LOG },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                label = { Text("Log") },
                colors = navColors()
            )
            NavigationBarItem(
                selected = tab == Tab.GOALS, onClick = { tab = Tab.GOALS },
                icon = { Icon(Icons.Filled.Flag, contentDescription = null) },
                label = { Text("Goals") },
                colors = navColors()
            )
            NavigationBarItem(
                selected = tab == Tab.SETTINGS, onClick = { tab = Tab.SETTINGS },
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                label = { Text("Settings") },
                colors = navColors()
            )
        }
    }
}

@Composable
fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Accent, selectedTextColor = Accent,
    unselectedIconColor = TextMuted, unselectedTextColor = TextMuted,
    indicatorColor = Panel2
)
