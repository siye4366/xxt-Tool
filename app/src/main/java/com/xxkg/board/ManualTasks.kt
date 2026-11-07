package com.xxkg.board

/** 手动添加的作业/考试：独立存储，并与学习通/雨课堂任务合并展示。 */
object ManualTasks {

    const val DEFAULT_SOURCE = "手动添加"

    fun load(prefs: Prefs): MutableList<Task> {
        val list = Task.decode(prefs.manualTasks).toMutableList()
        for (t in list) {
            t.manual = true
            t.source = DEFAULT_SOURCE
        }
        return list
    }

    fun save(prefs: Prefs, list: List<Task>) {
        prefs.manualTasks = Task.encode(list)
    }

    fun find(prefs: Prefs, key: String): Task? = load(prefs).firstOrNull { it.key == key }

    fun remove(prefs: Prefs, key: String) {
        save(prefs, load(prefs).filterNot { it.key == key })
    }

    fun upsert(prefs: Prefs, task: Task) {
        val list = load(prefs)
        val index = list.indexOfFirst { it.key == task.key }
        if (index >= 0) list[index] = task else list.add(task)
        save(prefs, list)
    }

    /**
     * 合并线上任务和手动任务，并处理手动任务到期后的去向。
     * DONE: 已完成；EXPIRED: 已过期；DELETE: 从本地删除。
     */
    fun merge(
        prefs: Prefs,
        online: List<Task>,
        now: Long = System.currentTimeMillis(),
        doneDays: Int = 7,
    ): List<Task> {
        val manual = load(prefs)
        var changed = false
        val keep = ArrayList<Task>()
        for (t in manual) {
            if (t.status == TaskStatus.TODO && t.endTime > 0L && t.endTime < now) {
                when (t.expiryAction) {
                    ManualExpiry.DONE -> {
                        t.status = TaskStatus.GRADED
                        t.stageTime = now
                        t.statusText = "已完成"
                    }
                    ManualExpiry.EXPIRED -> {
                        t.status = TaskStatus.EXPIRED
                    }
                    ManualExpiry.DELETE -> {
                        changed = true
                        continue
                    }
                }
                changed = true
            }
            // 到期移入「已完成」的手动任务，超过「已完成保留」时间后直接从本地删除
            if (t.status == TaskStatus.GRADED && t.stageTime > 0L &&
                now - t.stageTime > doneDays.coerceAtLeast(1) * TimeFmt.DAY
            ) {
                changed = true
                continue
            }
            keep.add(t)
        }
        if (changed) save(prefs, keep)
        return online.filterNot { it.manual } + keep
    }
}
