package com.xxkg.board

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** 用精确闹钟补足 WorkManager 在清后台后的延迟；进程被杀后闹钟仍能拉起检查。 */
object ReminderAlarmScheduler {

    private const val REQUEST = 7101

    fun schedule(context: Context) {
        val prefs = Prefs.get(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context)
        if (!prefs.notifyEnabled) {
            am.cancel(pi)
            return
        }
        val online = Task.decode(prefs.cache) + Task.decode(prefs.ytCache).onEach {
            it.provider = Provider.YUKETANG
        }
        val tasks = ManualTasks.merge(prefs, online, doneDays = prefs.yDays)
        val data = TimetableStore.load(context)
        val next = ReminderEngine.nextTrigger(prefs, tasks, data, System.currentTimeMillis())
        if (next == null) {
            am.cancel(pi)
            return
        }
        val at = maxOf(next, System.currentTimeMillis() + 1000L)
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent {
        val i = Intent(context, ReminderAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
