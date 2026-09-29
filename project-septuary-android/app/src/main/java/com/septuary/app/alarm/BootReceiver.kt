package com.septuary.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** All AlarmManager alarms are wiped on reboot — this puts them back from the plaintext
 *  schedule cache (see ScheduleCache.kt for why it's plaintext and what it deliberately excludes). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmScheduler.rescheduleFromCache(context)
        }
    }
}
