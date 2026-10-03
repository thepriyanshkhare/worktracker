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
import kotlinx.coroutines.withContext

/**
 * Pushes today's status to Firestore so the separate "Project Septuary Supervisor" app
 * (installed on his parents' phones) can show how the day is going. Two documents:
 * [STATUS_DOC_PATH] (an at-a-glance tri-state per section) and [DETAIL_DOC_PATH] (actual
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

    // Oct 2026 security hardening: the collection name is an unguessable random token, not
    // the plain word "septuary" — must match firestore.rules exactly, and must also match
    // Supervisor MainActivity.kt's listener paths and scripts/push_to_septuary.py's inbox
    // collection. If this token is ever leaked, rotate it in all four places plus the rules.
    private const val ROOT_COLLECTION = "septuary_86800832c1af658f9d30a04276952c81"

    /** One shared document — this app has a single patient, so no per-user path needed. */
    private const val STATUS_DOC_PATH = "$ROOT_COLLECTION/status"

    /**
     * A second, richer document — deliberately separate from [STATUS_DOC_PATH] so the
     * frequently-written tri-state doc stays small. This one carries actual medicine names,
     * food items, exercise detail and weight numbers to the parents' app. This is an explicit,
     * approved loosening of the original "status only, never names/values" privacy posture —
     * do not extend it further (e.g. glucose) without the same kind of explicit confirmation.
     */
    private const val DETAIL_DOC_PATH = "$ROOT_COLLECTION/detail"

    /**
     * Entries queued by the Claude chat session (e.g. "I weighed 94.5kg today") land here.
     * This app drains the collection on every unlock: each entry gets inserted into the
     * *local encrypted* DB via the same Repository methods the in-app forms use, then the
     * Firestore doc is deleted — so entries are never held in the cloud any longer than it
     * takes this app to next come online. If Firestore is unreachable, nothing here throws
     * or blocks the unlock; entries simply wait for the next successful drain.
     */
    private const val INBOX_COLLECTION = "${ROOT_COLLECTION}_inbox"

    /**
     * "Remind me" requests from the parents' Supervisor app land here (its first-ever write).
     * Drained on every unlock: each pending doc triggers one local notification (reusing the
     * "septuary_doses" channel from DoseAlarmReceiver's style), then gets deleted so it's
     * shown exactly once. Deliberately in-app only — no Cloud Function/FCM/true push, by
     * explicit choice once the Blaze-plan billing requirement was surfaced.
     */
    private const val REMINDERS_COLLECTION = "${ROOT_COLLECTION}_reminders"

    /**
     * firestore.rules now requires request.auth != null on every path this app touches.
     * Anonymous auth needs no credentials/UI — it just proves the request came through a
     * real Firebase Auth SDK call rather than a bare, unauthenticated REST request, which
     * blocks casual discovery and scanning. Safe to call repeatedly; a no-op once signed in.
     */
    private suspend fun ensureAuth(): Boolean = withContext(Dispatchers.IO) {
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
            val done = repo.todayLog()

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

            FirebaseFirestore.getInstance()
                .document(STATUS_DOC_PATH)
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
            val done = repo.todayLog()

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

            FirebaseFirestore.getInstance()
                .document(DETAIL_DOC_PATH)
                .set(payload, SetOptions.merge())
        } catch (_: Exception) {
            // Best-effort only, same as pushTodayStatus.
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
                FirebaseFirestore.getInstance().collection(INBOX_COLLECTION).get()
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
                FirebaseFirestore.getInstance().collection(REMINDERS_COLLECTION).get()
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

    /** Same channel/style as DoseAlarmReceiver's dose notifications, built here rather than by
     *  modifying that receiver — this isn't a dose, just reusing the established look/feel. */
    private fun postReminderNotification(context: Context) {
        val channelId = "septuary_doses"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(channelId, "Medication reminders", NotificationManager.IMPORTANCE_HIGH))
        }
        val openIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, "parent_reminder".hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Reminder from home")
            .setContentText("Your parents sent a reminder — open Septuary.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        nm.notify("parent_reminder".hashCode(), notification)
    }
}
