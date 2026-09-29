package com.septuary.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.septuary.app.MainActivity

class DoseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val doseKey = intent.getStringExtra("doseKey") ?: return
        val name = intent.getStringExtra("name") ?: "Medication"
        val detail = intent.getStringExtra("detail") ?: ""
        val time = intent.getStringExtra("time") ?: ""

        showNotification(context, doseKey, name, detail)

        // Reschedule this same dose for its next occurrence (tomorrow, or next active day).
        val cached = ScheduleCache.read(context).find { it.doseKey == doseKey }
        if (cached != null) {
            AlarmScheduler.scheduleNext(context, cached)
        }
    }

    private fun showNotification(context: Context, doseKey: String, name: String, detail: String) {
        val channelId = "septuary_doses"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Medication reminders", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            context, doseKey.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(name)
            .setContentText(detail)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        nm.notify(doseKey.hashCode(), notification)
    }
}
