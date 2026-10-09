package com.septuary.app.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.septuary.app.Session
import com.septuary.app.data.DoneMirror
import com.septuary.app.data.PendingActions
import kotlinx.coroutines.launch

/** Handles the "Taken"/"Done" and "Snooze" buttons on a reminder notification. */
class DoseActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TAKEN = "com.septuary.app.action.TAKEN"
        const val ACTION_SNOOZE = "com.septuary.app.action.SNOOZE"
        const val EXTRA_DOSE_KEY = "doseKey"
        const val EXTRA_MED_ID = "medId"
        const val EXTRA_DATE = "date"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val doseKey = intent.getStringExtra(EXTRA_DOSE_KEY) ?: return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(doseKey.hashCode())

        when (intent.action) {
            ACTION_TAKEN -> {
                val medId = intent.getStringExtra(EXTRA_MED_ID) ?: return
                val date = intent.getStringExtra(EXTRA_DATE) ?: return
                PendingActions.add(context, medId, date)
                DoneMirror.add(context, date, doseKey)
                AlarmScheduler.cancelSnoozeOnly(context, doseKey)
                // If the app is unlocked right now, apply immediately so the screen updates.
                val repo = Session.repo
                if (repo != null) {
                    val pending = goAsync()
                    Session.scope.launch {
                        try {
                            if (repo.applyPendingActions()) {
                                com.septuary.app.data.SyncRepository.schedulePush(repo)
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { Session.refresh() }
                            }
                        } catch (_: Exception) {
                            // Stays queued; merged on next unlock/resume.
                        } finally {
                            pending.finish()
                        }
                    }
                }
            }
            ACTION_SNOOZE -> {
                ScheduleCache.read(context).find { it.doseKey == doseKey }?.let {
                    AlarmScheduler.scheduleSnooze(context, it)
                }
            }
        }
    }
}
