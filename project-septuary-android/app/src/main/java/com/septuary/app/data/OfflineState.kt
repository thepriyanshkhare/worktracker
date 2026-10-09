package com.septuary.app.data

import android.content.Context
import com.septuary.app.crypto.LocalVault
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * "Taken" taps made from a notification while the app is locked. The encrypted database can't
 * be opened without the PIN, so each tap is queued here (Keystore-encrypted) and merged into
 * the database the next time the app is unlocked or resumed.
 */
object PendingActions {
    private const val NAME = "pending_taken"

    data class Action(val id: String, val medId: String, val date: String, val atIso: String)

    @Synchronized
    fun add(context: Context, medId: String, date: String) {
        val list = read(context).toMutableList()
        if (list.none { it.medId == medId && it.date == date }) {
            list.add(Action(UUID.randomUUID().toString(), medId, date, java.time.Instant.now().toString()))
            write(context, list)
        }
    }

    @Synchronized
    fun read(context: Context): List<Action> {
        val raw = LocalVault.get(context, NAME) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Action(o.getString("id"), o.getString("medId"), o.getString("date"), o.getString("at"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Removes only the entries that were successfully applied (never loses a concurrent tap). */
    @Synchronized
    fun remove(context: Context, ids: Set<String>) {
        if (ids.isEmpty()) return
        write(context, read(context).filterNot { it.id in ids })
    }

    private fun write(context: Context, list: List<Action>) {
        if (list.isEmpty()) {
            LocalVault.put(context, NAME, null)
            return
        }
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("medId", it.medId).put("date", it.date).put("at", it.atIso))
        }
        LocalVault.put(context, NAME, arr.toString())
    }
}

/**
 * Keystore-encrypted mirror of which reminder items are already done today, kept in step by the
 * app on every tick. Lets the alarm receiver skip a reminder for something already taken.
 */
object DoneMirror {
    private const val NAME = "done_today"

    @Synchronized
    fun set(context: Context, date: String, doseKeys: Set<String>) {
        val obj = JSONObject().put("date", date).put("keys", JSONArray(doseKeys.toList()))
        LocalVault.put(context, NAME, obj.toString())
    }

    @Synchronized
    fun add(context: Context, date: String, doseKey: String) {
        set(context, date, keysFor(context, date) + doseKey)
    }

    @Synchronized
    fun isDone(context: Context, date: String, doseKey: String): Boolean = doseKey in keysFor(context, date)

    private fun keysFor(context: Context, date: String): Set<String> {
        val raw = LocalVault.get(context, NAME) ?: return emptySet()
        return try {
            val obj = JSONObject(raw)
            if (obj.optString("date") != date) return emptySet()
            val arr = obj.getJSONArray("keys")
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}
