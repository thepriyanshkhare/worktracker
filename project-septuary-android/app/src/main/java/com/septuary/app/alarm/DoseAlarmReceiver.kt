package com.septuary.app.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.septuary.app.MainActivity
import com.septuary.app.data.DoneMirror
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fires at each scheduled time. Skips the alert if the item is already ticked today, otherwise
 * posts a notification with "Taken" and "Snooze" buttons. Always arms the next occurrence.
 */
class DoseAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_DOSE_KEY = "doseKey"
        const val EXTRA_SNOOZE = "snooze"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val doseKey = intent.getStringExtra(EXTRA_DOSE_KEY) ?: return
        val isSnooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        val dose = ScheduleCache.read(context).find { it.doseKey == doseKey } ?: return

        // Arm tomorrow's (or the next active day's) reminder first, so it's never lost.
        if (!isSnooze) AlarmScheduler.scheduleNext(context, dose)

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (DoneMirror.isDone(context, today, doseKey)) return

        show(context, dose, today)
    }

    private fun show(context: Context, dose: CachedDose, date: String) {
        Notifications.ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = dose.doseKey.hashCode()

        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fun action(kind: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
            context, code,
            Intent(context, DoseActionReceiver::class.java).apply {
                action = kind
                putExtra(DoseActionReceiver.EXTRA_DOSE_KEY, dose.doseKey)
                putExtra(DoseActionReceiver.EXTRA_MED_ID, dose.medId)
                putExtra(DoseActionReceiver.EXTRA_DATE, date)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channel = if (dose.isMedicine) Notifications.CHANNEL_MEDICINE else Notifications.CHANNEL_FOOD
        val body = listOf(dose.detail, dose.instruction).filter { it.isNotBlank() }.joinToString("\n")

        // What shows on the lock screen: no medicine names until the phone is unlocked.
        val publicVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Project Septuary")
            .setContentText(if (dose.isMedicine) "Medicine reminder" else "Food reminder")
            .build()

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(dose.name)
            .setContentText(dose.detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (dose.isMedicine) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, if (dose.isMedicine) "Taken" else "Done", action(DoseActionReceiver.ACTION_TAKEN, (dose.doseKey + "#taken").hashCode()))
            .addAction(0, "Snooze ${AlarmScheduler.SNOOZE_MINUTES} min", action(DoseActionReceiver.ACTION_SNOOZE, (dose.doseKey + "#snoozeAction").hashCode()))
            .build()

        nm.notify(id, notification)
    }
}
