package com.xxkg.board

import android.content.Context

/** 作业到期、上课前、常驻通知的判断逻辑。 */
object ReminderEngine {

    fun checkAll(context: Context, prefs: Prefs, tasks: List<Task>, data: TimetableData) {
        if (!prefs.notifyEnabled) {
            ReminderNotifications.cancelPersistent(context)
            return
        }
        if (!ReminderNotifications.canPost(context)) return
        ReminderNotifications.ensureChannels(context)
        val now = System.currentTimeMillis()
        checkHomework(context, prefs, tasks, now)
        checkCourses(context, prefs, data, now)
        updatePersistent(context, prefs, tasks, data, now)
    }

    /** 下一次需要检查提醒的时刻（毫秒）；没有则返回 null。 */
    fun nextTrigger(prefs: Prefs, tasks: List<Task>, data: TimetableData, now: Long): Long? {
        if (!prefs.notifyEnabled) return null
        var best = Long.MAX_VALUE
        val known = prefs.notifiedKeys()
        val deleted = prefs.deletedKeys()
        for (t in tasks) {
            if (t.manual && t.expiryAction == ManualExpiry.DELETE) continue
            if (t.key in deleted) continue
            if (t.status != TaskStatus.TODO || t.endTime <= 0L || t.endTime <= now) continue
            for (h in offsets(prefs)) {
                val trigger = t.endTime - h * TimeFmt.HOUR
                val key = "hw|${t.key}|$h"
                if (trigger > now && key !in known && trigger < best) best = trigger
            }
        }

        val week = TimetableLogic.weekNumber(data.term, now)
        if (week in 1..data.term.totalWeeks.coerceAtLeast(1)) {
            val day = TimetableLogic.dayOfWeek(now)
            val nowMin = TimetableLogic.nowMinutes()
            val offset = prefs.courseMinutes.coerceAtLeast(1)
            val today = data.segments
                .filter { !it.hidden && it.weeks.contains(week) && it.day == day }
                .sortedWith(compareBy({ it.startPeriod }, { it.endPeriod }))
            var lastEnd = -1
            for (s in today) {
                val p = data.periods.firstOrNull { it.no == s.startPeriod } ?: continue
                val endP = data.periods.firstOrNull { it.no == s.endPeriod } ?: p
                val start = TimetableLogic.minutesOfDay(p.start)
                val end = TimetableLogic.minutesOfDay(endP.end)
                if (start < 0 || end < start) continue
                val gap = if (lastEnd in 0 until start) start - lastEnd else Int.MAX_VALUE
                val trigger = if (!prefs.ignoreBreak && lastEnd in 0 until start && gap <= offset) {
                    lastEnd
                } else {
                    start - offset
                }
                lastEnd = maxOf(lastEnd, end)
                val key = "course|${s.id}|$week|$day|${s.startPeriod}"
                if (key in known) continue
                val nowDateTime = now
                val triggerMs = nowDateTime - nowMin * 60_000L + trigger * 60_000L
                if (triggerMs > now && triggerMs < best) {
                    best = triggerMs
                } else if (nowMin in trigger..start) {
                    // 已经进入提醒窗口但还没发：尽快检查
                    if (now + 1000L < best) best = now + 1000L
                }
            }
        }
        return if (best == Long.MAX_VALUE) null else best
    }

    fun offsets(prefs: Prefs): List<Int> =
        prefs.homeworkOffsets.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it >= 1 }
            .distinct()
            .sortedDescending()

    private fun checkHomework(context: Context, prefs: Prefs, tasks: List<Task>, now: Long) {
        val hours = offsets(prefs)
        if (hours.isEmpty()) return
        val deleted = prefs.deletedKeys()
        for (t in tasks) {
            if (t.manual && t.expiryAction == ManualExpiry.DELETE) continue
            if (t.key in deleted) continue
            if (t.status != TaskStatus.TODO || t.endTime <= 0L || t.endTime <= now) continue
            for (h in hours) {
                val trigger = t.endTime - h * TimeFmt.HOUR
                if (now < trigger) continue
                val key = "hw|${t.key}|$h"
                val known = prefs.notifiedKeys()
                if (key in known) continue
                prefs.markNotified(key)
                val r = TimeFmt.remain(now, t.endTime, prefs.greenDays, prefs.orangeDays)
                val course = t.courseName.ifEmpty { "未知课程" }
                ReminderNotifications.homework(
                    context = context,
                    key = key,
                    title = t.title,
                    course = course,
                    deadline = TimeFmt.dateTime(t.endTime),
                    remain = r.text,
                    coreColor = r.color,
                )
            }
        }
    }

    private fun checkCourses(context: Context, prefs: Prefs, data: TimetableData, now: Long) {
        val week = TimetableLogic.weekNumber(data.term, now)
        if (week !in 1..data.term.totalWeeks.coerceAtLeast(1)) return
        val day = TimetableLogic.dayOfWeek(now)
        val nowMin = TimetableLogic.nowMinutes()
        val offset = prefs.courseMinutes.coerceAtLeast(1)
        val periods = data.periods
        if (periods.isEmpty()) return

        val today = data.segments
            .filter { !it.hidden && it.weeks.contains(week) && it.day == day }
            .sortedWith(compareBy({ it.startPeriod }, { it.endPeriod }))
        if (today.isEmpty()) return

        var lastEnd = -1
        for (s in today) {
            val p = periods.firstOrNull { it.no == s.startPeriod } ?: continue
            val endP = periods.firstOrNull { it.no == s.endPeriod } ?: p
            val start = TimetableLogic.minutesOfDay(p.start)
            val end = TimetableLogic.minutesOfDay(endP.end)
            if (start < 0 || end < start) continue
            val gap = if (lastEnd in 0 until start) start - lastEnd else Int.MAX_VALUE
            val trigger = if (!prefs.ignoreBreak && lastEnd in 0 until start && gap <= offset) {
                lastEnd
            } else {
                start - offset
            }
            lastEnd = maxOf(lastEnd, end)
            if (nowMin < trigger || nowMin > start) continue
            val course = data.course(s.courseId) ?: continue
            val key = "course|${s.id}|$week|$day|${s.startPeriod}"
            if (key in prefs.notifiedKeys()) continue
            prefs.markNotified(key)
            val location = listOf(s.campus, s.location)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "地点未填" }
            ReminderNotifications.course(
                context = context,
                key = key,
                title = course.name,
                location = location,
                time = "${p.start} 上课",
            )
        }
    }

    fun refreshPersistent(context: Context) {
        val prefs = Prefs.get(context)
        if (!prefs.notifyEnabled || !prefs.persistentNotification) {
            ReminderNotifications.cancelPersistent(context)
            return
        }
        val online = Task.decode(prefs.cache) + Task.decode(prefs.ytCache).onEach {
            it.provider = Provider.YUKETANG
        }
        val tasks = ManualTasks.merge(prefs, online, doneDays = prefs.yDays)
        val data = TimetableStore.load(context)
        updatePersistent(context, prefs, tasks, data, System.currentTimeMillis())
    }

    private fun updatePersistent(
        context: Context,
        prefs: Prefs,
        tasks: List<Task>,
        data: TimetableData,
        now: Long,
    ) {
        if (!prefs.persistentNotification) {
            ReminderNotifications.cancelPersistent(context)
            return
        }
        val deleted = prefs.deletedKeys()
        val nextTask = tasks
            .filter {
                !(it.key in deleted) &&
                    it.status == TaskStatus.TODO &&
                    it.endTime > now
            }
            .minByOrNull { it.endTime }

        val hwRemain = nextTask?.let {
            TimeFmt.remain(now, it.endTime, prefs.greenDays, prefs.orangeDays)
        }
        val hwTime = hwRemain?.text.orEmpty()
        val hwTitle = if (nextTask != null) {
            val course = nextTask.courseName.ifEmpty { "未知课程" }
            "${nextTask.title} · $course"
        } else {
            "暂无待办"
        }
        val hwColor = hwRemain?.color ?: Palette.MUTED2

        val week = TimetableLogic.weekNumber(data.term, now)
        val day = TimetableLogic.dayOfWeek(now)
        val nowMin = TimetableLogic.nowMinutes()
        var chosen: Triple<Segment, TtCourse, Period>? = null
        var chosenStart = Int.MAX_VALUE
        if (week in 1..data.term.totalWeeks.coerceAtLeast(1)) {
            for (s in data.segments) {
                if (s.hidden || !s.weeks.contains(week) || s.day != day) continue
                val p = data.periods.firstOrNull { it.no == s.startPeriod } ?: continue
                val start = TimetableLogic.minutesOfDay(p.start)
                val endP = data.periods.firstOrNull { it.no == s.endPeriod } ?: p
                val end = TimetableLogic.minutesOfDay(endP.end)
                if (start < 0 || end < start) continue
                val c = data.course(s.courseId) ?: continue
                // 正在上的课优先；否则取最近一节还没开始的课
                if (nowMin in start..end) {
                    chosen = Triple(s, c, p)
                    chosenStart = 0
                    break
                }
                if (start > nowMin && start < chosenStart) {
                    chosen = Triple(s, c, p)
                    chosenStart = start
                }
            }
        }

        var courseTime = ""
        var courseName = "暂无课程"
        var courseLocation = ""
        if (chosen != null) {
            val (s, c, p) = chosen
            courseTime = p.start
            courseName = c.name
            courseLocation = listOf(s.campus, s.location)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "地点未填" }
        }

        ReminderNotifications.persistent(
            context = context,
            hwTime = hwTime,
            hwTitle = hwTitle,
            hwColor = hwColor,
            courseTime = courseTime,
            courseName = courseName,
            courseLocation = courseLocation,
        )
    }
}
