package com.septuary.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.septuary.app.data.MedicationEntity
import java.util.Calendar

object AlarmScheduler {

    const val SNOOZE_MINUTES = 15

    fun isMedActiveOn(med: CachedDose, cal: Calendar): Boolean {
        val dateStr = "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        if (med.startDate != null && dateStr < med.startDate) return false
        if (med.endDate != null && dateStr > med.endDate) return false
        if (med.days == "daily") return true
        if (med.days.startsWith("weekly:")) {
            val dow = med.days.substringAfter(":").toIntOrNull() ?: return false // 0=Sunday
            return cal.get(Calendar.DAY_OF_WEEK) - 1 == dow
        }
        return true
    }

    /**
     * Rebuilds the schedule cache from the (decrypted) medication list and arms the next
     * occurrence of every item. Alarms for items that were removed or moved to a new time are
     * cancelled, so nothing stale ever fires.
     */
    fun rescheduleAll(context: Context, medications: List<MedicationEntity>) {
        val previous = ScheduleCache.read(context).map { it.doseKey }.toSet()
        val cached = medications.map {
            CachedDose(it.id + "@" + it.time, it.id, it.name, it.detail, it.time, it.days, it.startDate, it.endDate, it.category, it.instruction)
        }
        (previous - cached.map { it.doseKey }.toSet()).forEach { cancel(context, it) }
        ScheduleCache.write(context, cached)
        rescheduleFromCache(context)
    }

    fun rescheduleFromCache(context: Context) {
        ScheduleCache.read(context).forEach { scheduleNext(context, it) }
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun scheduleNext(context: Context, dose: CachedDose) {
        val triggerAt = nextOccurrence(dose)
        if (triggerAt == null) {
            cancel(context, dose.doseKey)
            return
        }
        setAlarm(context, triggerAt, pendingIntentFor(context, dose, snooze = false))
    }

    /** One-off repeat of [dose]'s reminder, [SNOOZE_MINUTES] from now. */
    fun scheduleSnooze(context: Context, dose: CachedDose) {
        val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        setAlarm(context, at, pendingIntentFor(context, dose, snooze = true))
    }

    private fun setAlarm(context: Context, triggerAt: Long, pi: PendingIntent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            // Permission missing: still remind (a few minutes late at worst) rather than not at all.
            // The app shows a banner until exact alarms are allowed.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun pendingIntentFor(context: Context, dose: CachedDose, snooze: Boolean): PendingIntent {
        val intent = Intent(context, DoseAlarmReceiver::class.java).apply {
            putExtra(DoseAlarmReceiver.EXTRA_DOSE_KEY, dose.doseKey)
            putExtra(DoseAlarmReceiver.EXTRA_SNOOZE, snooze)
        }
        val requestCode = if (snooze) (dose.doseKey + "#snooze").hashCode() else dose.doseKey.hashCode()
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancel(context: Context, doseKey: String) = cancelCodes(context, listOf(doseKey.hashCode(), (doseKey + "#snooze").hashCode()))

    fun cancelSnoozeOnly(context: Context, doseKey: String) = cancelCodes(context, listOf((doseKey + "#snooze").hashCode()))

    private fun cancelCodes(context: Context, codes: List<Int>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        codes.forEach { code ->
            val pi = PendingIntent.getBroadcast(
                context, code, Intent(context, DoseAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }
    }

    /** Next future timestamp this item should fire at, honouring daily/weekly + start/end dates. */
    private fun nextOccurrence(dose: CachedDose): Long? {
        val parts = dose.time.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size != 2) return null
        val now = Calendar.getInstance()
        val candidate = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, parts[0])
            set(Calendar.MINUTE, parts[1])
        }
        if (candidate.timeInMillis <= now.timeInMillis) candidate.add(Calendar.DAY_OF_YEAR, 1)
        for (i in 0 until 14) {
            if (isMedActiveOn(dose, candidate)) return candidate.timeInMillis
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }
}
