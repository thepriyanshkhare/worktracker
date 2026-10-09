package com.septuary.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Alarms are wiped on reboot and can shift on a clock/time-zone change or app update. This puts
 * every reminder back from the schedule cache (see ScheduleCache.kt) without needing an unlock.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> {
                Notifications.ensureChannels(context)
                AlarmScheduler.rescheduleFromCache(context)
            }
        }
    }
}
