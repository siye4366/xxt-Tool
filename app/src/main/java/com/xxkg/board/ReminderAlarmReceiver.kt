package com.xxkg.board

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 精确闹钟到点：用本地缓存快速检查一次，然后排下一次。 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val prefs = Prefs.get(context)
                if (prefs.notifyEnabled) {
                    val online = Task.decode(prefs.cache) + Task.decode(prefs.ytCache).onEach {
                        it.provider = Provider.YUKETANG
                    }
                    val tasks = ManualTasks.merge(prefs, online, doneDays = prefs.yDays)
                    ReminderEngine.checkAll(
                        context, prefs, tasks, TimetableStore.load(context)
                    )
                }
                ReminderAlarmScheduler.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}

/** 开机/改时间后重新排闹钟。 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.sync(context)
        ReminderAlarmScheduler.schedule(context)
    }
}
