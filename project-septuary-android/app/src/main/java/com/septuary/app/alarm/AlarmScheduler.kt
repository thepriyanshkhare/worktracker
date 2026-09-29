package com.septuary.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.septuary.app.data.MedicationEntity
import java.util.Calendar

object AlarmScheduler {

    fun isMedActiveOn(med: CachedDose, cal: Calendar): Boolean {
        val dateStr = "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        if (med.startDate != null && dateStr < med.startDate) return false
        if (med.endDate != null && dateStr > med.endDate) return false
        if (med.days == "daily") return true
        if (med.days.startsWith("weekly:")) {
            val dow = med.days.substringAfter(":").toInt() // 0=Sunday
            // Calendar.DAY_OF_WEEK: 1=Sunday..7=Saturday
            return cal.get(Calendar.DAY_OF_WEEK) - 1 == dow
        }
        return true
    }

    /** Rebuilds the plaintext schedule cache and (re)schedules the next occurrence of every dose. */
    fun rescheduleAll(context: Context, medications: List<MedicationEntity>) {
        val cached = medications.map {
            CachedDose(it.id + "@" + it.time, it.id, it.name, it.detail, it.time, it.days, it.startDate, it.endDate)
        }
        ScheduleCache.write(context, cached)
        rescheduleFromCache(context)
    }

    fun rescheduleFromCache(context: Context) {
        val doses = ScheduleCache.read(context)
        doses.forEach { scheduleNext(context, it) }
    }

    fun scheduleNext(context: Context, dose: CachedDose) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextOccurrence(dose)
        if (triggerAt == null) {
            cancel(context, dose.doseKey)
            return
        }

        val intent = Intent(context, DoseAlarmReceiver::class.java).apply {
            putExtra("doseKey", dose.doseKey)
            putExtra("medId", dose.medId)
            putExtra("name", dose.name)
            putExtra("detail", dose.detail)
            putExtra("time", dose.time)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, dose.doseKey.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Permission not granted — Settings screen surfaces this. Falling back to an
            // inexact alarm would drift by minutes to hours; better to visibly not schedule
            // than to silently under-promise.
            return
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    fun cancel(context: Context, doseKey: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DoseAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, doseKey.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /** Next future timestamp (millis) this dose should fire at, honoring daily/weekly + start/end dates. */
    private fun nextOccurrence(dose: CachedDose): Long? {
        val (h, m) = dose.time.split(":").map { it.toInt() }
        val now = Calendar.getInstance()
        val candidate = Calendar.getInstance()
        candidate.set(Calendar.SECOND, 0)
        candidate.set(Calendar.MILLISECOND, 0)
        candidate.set(Calendar.HOUR_OF_DAY, h)
        candidate.set(Calendar.MINUTE, m)
        if (candidate.timeInMillis <= now.timeInMillis) {
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
        // Search up to 14 days ahead for the next day this dose is actually active
        // (handles weekly cadence and start/end-dated courses).
        for (i in 0 until 14) {
            if (isMedActiveOn(dose, candidate)) return candidate.timeInMillis
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }
}
