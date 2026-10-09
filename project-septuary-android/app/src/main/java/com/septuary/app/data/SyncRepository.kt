package com.septuary.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.septuary.app.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pushes today's status to Firestore so the separate "Project Septuary Supervisor" app
 * (installed on his parents' phones) can show how the day is going. Two documents:
 * "status" (an at-a-glance tri-state per section) and "detail" (actual
 * medicine/food names, exercise detail, and recent weight — an explicit, approved choice
 * to share real detail with his parents rather than only Done/Pending/Not Done). Glucose
 * and free-text notes are still never pushed.
 *
 * This is the ONLY class in the app that talks to the network. If Firestore is
 * unreachable (no signal, google-services.json not yet configured, etc.) every call
 * here fails silently — sync is a nice-to-have for the parents' peace of mind, never
 * something that should block or slow down the patient-facing app.
 */
object SyncRepository {

    // Paths are derived from the private family code generated on this phone (see FamilyLink).
    // No path, token or key is compiled into the app or stored in the public repository.
    private fun root(context: Context) = FamilyLink.root(context)
    private fun statusPath(context: Context) = root(context) + "/status"
    private fun detailPath(context: Context) = root(context) + "/detail"
    private fun inbox(context: Context) = root(context) + "_inbox"
    private fun reminders(context: Context) = root(context) + "_reminders"

    // The pre-pairing location, which appeared in the public repository. Its two documents are
    // deleted once (best-effort) so nothing stays readable there.
    private const val LEGACY_ROOT = "septuary_86800832c1af658f9d30a04276952c81"

    /**
     * firestore.rules now requires request.auth != null on every path this app touches.
     * Anonymous auth needs no credentials/UI — it just proves the request came through a
     * real Firebase Auth SDK call rather than a bare, unauthenticated REST request, which
     * blocks casual discovery and scanning. Safe to call repeatedly; a no-op once signed in.
     */
    suspend fun ensureAuth(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (FirebaseAuth.getInstance().currentUser == null) {
                Tasks.await(FirebaseAuth.getInstance().signInAnonymously())
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    enum class Status { DONE, PENDING, NOT_DONE;
        fun wire() = when (this) { DONE -> "done"; PENDING -> "pending"; NOT_DONE -> "not_done" }
    }

    /** total==0 (nothing scheduled) counts as Done — there's nothing pending to chase. */
    private fun statusFor(done: Int, total: Int): Status = when {
        total == 0 || done == total -> Status.DONE
        done > 0 -> Status.PENDING
        else -> Status.NOT_DONE
    }

    /**
     * Recomputes Medicine / Food / Exercise status for today from the repository's own
     * data and pushes it. Call this after any dose toggle and any exercise log — cheap,
     * and Firestore merges the write so a failed/offline attempt just gets superseded by
     * the next successful one.
     */
    suspend fun pushTodayStatus(repo: Repository) = withContext(Dispatchers.IO) {
        try {
            if (!ensureAuth()) return@withContext
            val doses = repo.todayDoses()
            val done = repo.todayLog().keys

            val medicineDoses = doses.filter { it.category == "medication" }
            val foodDoses = doses.filter { it.category != "medication" } // coffee/tea/meal

            val medicineStatus = statusFor(medicineDoses.count { done.contains(it.doseKey) }, medicineDoses.size)
            val foodStatus = statusFor(foodDoses.count { done.contains(it.doseKey) }, foodDoses.size)
            val exerciseStatus = if (repo.todayExercise().isNotEmpty()) Status.DONE else Status.PENDING

            val payload = mapOf(
                "date" to repo.todayKey(),
                "medicine" to medicineStatus.wire(),
                "food" to foodStatus.wire(),
                "exercise" to exerciseStatus.wire(),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            // Not awaited: Firestore queues the write offline and sends it when back online.
            FirebaseFirestore.getInstance()
                .document(statusPath(repo.appContext))
                .set(payload, SetOptions.merge())
        } catch (_: Exception) {
            // Best-effort only — never surface a sync failure to the patient-facing UI.
        }
    }

    /**
     * Pushes the full-detail doc the parents' app now reads: the complete standing medicine
     * routine (every active medication, whether or not it's due today — so a weekly medicine
     * doesn't silently vanish from what parents see on its off days), food items for today
     * (name/category/time/done), today's exercise, and a short recent weight trend. Glucose is
     * intentionally excluded — not part of the agreed detail set.
     */
    suspend fun pushTodayDetail(repo: Repository) = withContext(Dispatchers.IO) {
        try {
            if (!ensureAuth()) return@withContext
            val today = repo.todayKey()
            val doses = repo.todayDoses()
            val done = repo.todayLog().keys

            // Medicine: the full routine, not just today's slice — Oct 2026, requested so
            // parents always see the whole regimen, not only what happens to be due today.
            val medicineItems = repo.medications().filter { it.category == "medication" }.map { med ->
                val dueToday = repo.isMedActiveOnDate(med, today)
                mapOf(
                    "name" to med.name,
                    "time" to med.time,
                    "schedule" to scheduleLabel(med.days),
                    "dueToday" to dueToday,
                    "done" to (dueToday && done.contains(med.id + "@" + med.time))
                )
            }
            val foodItems = doses.filter { it.category != "medication" }.map {
                mapOf("name" to it.name, "category" to it.category, "time" to it.time, "done" to done.contains(it.doseKey))
            }
            val exercise = repo.todayExercise().map { mapOf("type" to it.type, "minutes" to it.minutes) }
            val recentWeights = repo.weightLog().take(7).map { mapOf("date" to it.date, "kg" to it.kg) }

            val payload = mapOf(
                "date" to repo.todayKey(),
                "medicineItems" to medicineItems,
                "foodItems" to foodItems,
                "exercise" to exercise,
                "recentWeights" to recentWeights,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            // Not awaited: Firestore queues the write offline and sends it when back online.
            FirebaseFirestore.getInstance()
                .document(detailPath(repo.appContext))
                .set(payload, SetOptions.merge())
        } catch (_: Exception) {
            // Best-effort only, same as pushTodayStatus.
        }
    }

    private val pushScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private var pendingPush: kotlinx.coroutines.Job? = null

    /**
     * Fire-and-forget, debounced push used after every tick/log: the UI never waits on the
     * network, and a burst of taps (e.g. "Mark all") becomes a single write.
     */
    @Synchronized
    fun schedulePush(repo: Repository) {
        pendingPush?.cancel()
        pendingPush = pushScope.launch {
            kotlinx.coroutines.delay(600)
            pushToday(repo)
        }
    }

    /** Pushes both the tri-state status doc and the full-detail doc together. Use this instead
     *  of calling pushTodayStatus directly, so the two never drift out of sync with each other. */
    suspend fun pushToday(repo: Repository) {
        pushTodayStatus(repo)
        pushTodayDetail(repo)
    }

    /**
     * Pulls every pending chat-logged entry into the local encrypted DB, deletes each one
     * from Firestore as it's consumed, then pushes an updated status. Call on every unlock
     * (see MainActivity) — safe to call repeatedly; an empty inbox is a no-op.
     */
    suspend fun importInbox(repo: Repository) = withContext(Dispatchers.IO) {
        try {
            if (!ensureAuth()) return@withContext
            val snapshot = Tasks.await(
                FirebaseFirestore.getInstance().collection(inbox(repo.appContext)).get()
            )
            var importedAny = false
            for (doc in snapshot.documents) {
                val type = doc.getString("type") ?: continue
                val onDate = doc.getString("date") // "YYYY-MM-DD", optional — defaults to today if absent
                try {
                    when (type) {
                        "weight" -> {
                            val kg = doc.getDouble("kg") ?: continue
                            repo.addWeight(kg, onDate, doc.getString("note") ?: "")
                        }
                        "glucose" -> {
                            val value = doc.getLong("value")?.toInt() ?: continue
                            val glucoseType = doc.getString("glucoseType") ?: "random"
                            repo.addGlucose(value, glucoseType, onDate)
                        }
                        "exercise" -> {
                            val exerciseType = doc.getString("exerciseType") ?: continue
                            val minutes = doc.getLong("minutes")?.toInt() ?: continue
                            repo.addExerciseOn(exerciseType, minutes, doc.getString("note") ?: "", onDate)
                        }
                        "sleep" -> {
                            val bedTime = doc.getString("bedTime") ?: continue
                            val wakeTime = doc.getString("wakeTime") ?: continue
                            val quality = doc.getLong("quality")?.toInt() ?: 3
                            repo.addSleep(bedTime, wakeTime, quality, doc.getString("note") ?: "")
                        }
                        "note" -> {
                            val text = doc.getString("text") ?: continue
                            repo.addFlag(text)
                        }
                        else -> continue
                    }
                    importedAny = true
                    Tasks.await(doc.reference.delete())
                } catch (_: Exception) {
                    // Leave this one doc in the inbox — retried on the next unlock.
                }
            }
            if (importedAny) pushToday(repo)
        } catch (_: Exception) {
            // Offline or not yet configured — nothing pending gets lost, just retried later.
        }
    }

    /**
     * Drains any pending "remind me" taps from the parents' Supervisor app: posts one local
     * notification per pending doc, then deletes each doc so it's shown exactly once. Call on
     * every unlock alongside [importInbox] — best-effort, never blocks or crashes.
     */
    suspend fun checkReminders(context: Context, repo: Repository) = withContext(Dispatchers.IO) {
        try {
            if (!ensureAuth()) return@withContext
            val snapshot = Tasks.await(
                FirebaseFirestore.getInstance().collection(reminders(context)).get()
            )
            for (doc in snapshot.documents) {
                try {
                    postReminderNotification(context)
                    Tasks.await(doc.reference.delete())
                } catch (_: Exception) {
                    // Leave this one doc in place — retried on the next unlock.
                }
            }
        } catch (_: Exception) {
            // Offline or not yet configured — nothing pending gets lost, just retried later.
        }
    }

    /** One-time, best-effort removal of the two documents at the old public path. */
    suspend fun cleanupLegacy(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("septuary_app", Context.MODE_PRIVATE)
        if (prefs.getBoolean("legacy_cleaned", false)) return@withContext
        try {
            if (!ensureAuth()) return@withContext
            val fs = FirebaseFirestore.getInstance()
            Tasks.await(fs.document("$LEGACY_ROOT/status").delete())
            Tasks.await(fs.document("$LEGACY_ROOT/detail").delete())
            prefs.edit().putBoolean("legacy_cleaned", true).apply()
        } catch (_: Exception) {
            // Rules may already deny the old path — which is the goal anyway.
            prefs.edit().putBoolean("legacy_cleaned", true).apply()
        }
    }

    /** Same channel/style as DoseAlarmReceiver's dose notifications, built here rather than by
     *  modifying that receiver — this isn't a dose, just reusing the established look/feel. */
    private fun postReminderNotification(context: Context) {
        val channelId = com.septuary.app.alarm.Notifications.CHANNEL_FAMILY
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        com.septuary.app.alarm.Notifications.ensureChannels(context)
        val openIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, "parent_reminder".hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Reminder from home")
            .setContentText("Your parents sent a reminder — open Septuary.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        nm.notify("parent_reminder".hashCode(), notification)
    }
}
