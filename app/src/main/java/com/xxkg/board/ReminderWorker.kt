package com.xxkg.board

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Constraints
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** 后台兜底：每 15 分钟抓一次并检查提醒。 */
class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs.get(applicationContext)
        if (!prefs.notifyEnabled) return Result.success()

        val online = ArrayList<Task>()
        if (prefs.isCxLoggedIn) {
            try {
                online.addAll(XtApi(prefs).fetchAll().tasks)
            } catch (e: Exception) {
                // 使用缓存
            }
        }
        if (prefs.isYtLoggedIn) {
            try {
                online.addAll(YtApi(prefs).fetchAll().tasks)
            } catch (e: Exception) {
                // 使用缓存
            }
        }
        val tasks = if (online.isEmpty()) {
            Task.decode(prefs.cache) + Task.decode(prefs.ytCache).onEach {
                it.provider = Provider.YUKETANG
            }
        } else {
            online
        }
        val merged = ManualTasks.merge(prefs, tasks, doneDays = prefs.yDays)
        val data = TimetableStore.load(applicationContext)
        ReminderEngine.checkAll(applicationContext, prefs, merged, data)
        return Result.success()
    }
}

object ReminderScheduler {

    private const val WORK = "xxt_reminder_15m"

    fun sync(context: Context) {
        val prefs = Prefs.get(context)
        val wm = WorkManager.getInstance(context)
        if (!prefs.notifyEnabled) {
            wm.cancelUniqueWork(WORK)
            ReminderNotifications.cancelPersistent(context)
            return
        }
        ReminderNotifications.ensureChannels(context)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
