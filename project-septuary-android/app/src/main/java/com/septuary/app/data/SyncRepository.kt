package com.septuary.app.data

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pushes today's high-level status — nothing else — to Firestore so the separate
 * "Project Septuary Supervisor" app (installed on his parents' phones) can show a
 * simple Done / Pending / Not Done view without ever seeing medication names, doses,
 * glucose or weight values, or notes.
 *
 * This is the ONLY class in the app that talks to the network. If Firestore is
 * unreachable (no signal, google-services.json not yet configured, etc.) every call
 * here fails silently — sync is a nice-to-have for the parents' peace of mind, never
 * something that should block or slow down the patient-facing app.
 */
object SyncRepository {

    /** One shared document — this app has a single patient, so no per-user path needed. */
    private const val STATUS_DOC_PATH = "septuary/status"

    /**
     * Entries queued by the Claude chat session (e.g. "I weighed 94.5kg today") land here.
     * This app drains the collection on every unlock: each entry gets inserted into the
     * *local encrypted* DB via the same Repository methods the in-app forms use, then the
     * Firestore doc is deleted — so entries are never held in the cloud any longer than it
     * takes this app to next come online. If Firestore is unreachable, nothing here throws
     * or blocks the unlock; entries simply wait for the next successful drain.
     */
    private const val INBOX_COLLECTION = "septuary_inbox"

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
     * Pulls every pending chat-logged entry into the local encrypted DB, deletes each one
     * from Firestore as it's consumed, then pushes an updated status. Call on every unlock
     * (see MainActivity) — safe to call repeatedly; an empty inbox is a no-op.
     */
    suspend fun importInbox(repo: Repository) = withContext(Dispatchers.IO) {
        try {
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
            if (importedAny) pushTodayStatus(repo)
        } catch (_: Exception) {
            // Offline or not yet configured — nothing pending gets lost, just retried later.
        }
    }
}
