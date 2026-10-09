package com.septuary.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** Separate channels so Medicine, Food and family nudges can each be tuned in system settings. */
object Notifications {
    const val CHANNEL_MEDICINE = "septuary_medicine"
    const val CHANNEL_FOOD = "septuary_food"
    const val CHANNEL_FAMILY = "septuary_family"
    private const val LEGACY_CHANNEL = "septuary_doses"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MEDICINE, "Medicine reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "On-time alerts for each medicine"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_FOOD, "Food reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Meals, coffee and green tea"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_FAMILY, "Family reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Nudges sent from the family app"
            }
        )
        if (nm.getNotificationChannel(LEGACY_CHANNEL) != null) nm.deleteNotificationChannel(LEGACY_CHANNEL)
    }
}
