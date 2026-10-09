package com.septuary.app.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * TRADE-OFF, STATED PLAINLY: the encrypted database cannot be opened by a boot-time
 * BroadcastReceiver, because that receiver has no PIN to derive the passphrase with —
 * you're not there to type it in when the phone reboots.
 *
 * To still have reminders survive a reboot without you having to reopen the app first,
 * a minimal, UNENCRYPTED cache of {medication name, detail, time} is kept here — NOT your
 * weight/glucose readings, NOT your dose-taken history, just the schedule shape needed to
 * post a notification. This is the one deliberate exception to "everything encrypted."
 *
 * If you'd rather have zero plaintext ever (including medication names) at the cost of
 * reminders not surviving a reboot until you reopen and unlock the app, delete the calls
 * to ScheduleCache.write() in AlarmScheduler and rely on in-app scheduling only.
 */
data class CachedDose(
    val doseKey: String, val medId: String, val name: String, val detail: String, val time: String,
    val days: String, val startDate: String?, val endDate: String?,
    val category: String = "medication", val instruction: String = ""
) {
    val isMedicine: Boolean get() = category == "medication"
}

object ScheduleCache {
    private const val PREFS = "septuary_schedule_cache"
    private const val KEY = "doses"

    fun write(context: Context, doses: List<CachedDose>) {
        val arr = JSONArray()
        doses.forEach { d ->
            val obj = JSONObject()
            obj.put("doseKey", d.doseKey)
            obj.put("medId", d.medId)
            obj.put("name", d.name)
            obj.put("detail", d.detail)
            obj.put("time", d.time)
            obj.put("days", d.days)
            obj.put("startDate", d.startDate ?: JSONObject.NULL)
            obj.put("endDate", d.endDate ?: JSONObject.NULL)
            obj.put("category", d.category)
            obj.put("instruction", d.instruction)
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).commit()
    }

    fun read(context: Context): List<CachedDose> {
        val str = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        val arr = try { JSONArray(str) } catch (_: Exception) { return emptyList() }
        val out = mutableListOf<CachedDose>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                CachedDose(
                    doseKey = o.getString("doseKey"),
                    medId = o.getString("medId"),
                    name = o.getString("name"),
                    detail = o.getString("detail"),
                    time = o.getString("time"),
                    days = o.getString("days"),
                    startDate = if (o.isNull("startDate")) null else o.getString("startDate"),
                    endDate = if (o.isNull("endDate")) null else o.getString("endDate"),
                    category = o.optString("category", "medication"),
                    instruction = o.optString("instruction", "")
                )
            )
        }
        return out
    }
}
