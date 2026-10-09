package com.septuary.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.septuary.app.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Arrays

/**
 * Process-wide unlocked session. Living outside the Activity means rotation, dark-mode switches
 * and other configuration changes never drop you back to the lock screen or leak the database.
 */
object Session {
    var repo by mutableStateOf<Repository?>(null)
        private set

    /** Bumped whenever data may have changed outside the visible screen; screens reload on it. */
    var dataVersion by mutableIntStateOf(0)
        private set

    /** The unlocked database key, held only while unlocked (needed to turn on fingerprint unlock). */
    var passphrase: CharArray? = null
        private set

    /** Set after a PIN unlock when fingerprint unlock could be offered. */
    var offerBiometric by mutableStateOf(false)

    /** True while the Camera app is open for a food photo — auto-lock must not fire then. */
    @Volatile var externalActivityInFlight = false

    /** elapsedRealtime when the app last went to the background; 0 when in the foreground. */
    @Volatile var backgroundedAt = 0L

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun open(repository: Repository, key: CharArray) {
        repo = repository
        passphrase = key
        dataVersion++
    }

    fun refresh() {
        dataVersion++
    }

    fun lock() {
        val old = repo ?: return
        repo = null
        offerBiometric = false
        passphrase?.let { Arrays.fill(it, '\u0000') }
        passphrase = null
        // Close after a short grace period so any in-flight query finishes instead of crashing.
        scope.launch {
            delay(2_000)
            try { old.close() } catch (_: Exception) {}
        }
    }
}
