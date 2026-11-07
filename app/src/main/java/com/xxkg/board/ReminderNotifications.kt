package com.xxkg.board

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs

/** 通知渠道、普通提醒和常驻通知。 */
object ReminderNotifications {

    private const val CH_HOMEWORK = "hw_reminder"
    private const val CH_COURSE = "course_reminder"
    private const val CH_PERSISTENT = "persistent_reminder"
    private const val ID_PERSISTENT = 10001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_HOMEWORK, "作业提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "作业截止前的提醒"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_COURSE, "课程提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "上课前的提醒"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_PERSISTENT, "常驻提醒", NotificationManager.IMPORTANCE_LOW).apply {
                description = "常驻显示最近截止作业和下节课"
                setShowBadge(false)
            }
        )
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun contentIntent(context: Context): PendingIntent {
        val i = Intent(context, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        return PendingIntent.getActivity(
            context, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun homework(
        context: Context,
        key: String,
        title: String,
        course: String,
        deadline: String,
        remain: String,
        coreColor: Int,
        preview: Boolean = false,
    ) {
        if (!canPost(context)) return
        ensureChannels(context)
        val label = if (preview) "作业提醒 · 预览" else "作业提醒"
        val accent = ContextCompat.getColor(context, R.color.tt_accent)
        val views = alertViews(
            context = context,
            label = label,
            left = "$title · $course",
            leftColor = Palette.INK,
            right = remain,
            rightColor = coreColor,
        )
        val n = NotificationCompat.Builder(context, CH_HOMEWORK)
            .setSmallIcon(R.drawable.ic_notification_app)
            .setContentTitle(label)
            .setContentText(remain)
            .setCustomBigContentView(views)
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        notify(context, key.hashCode(), n)
    }

    fun course(
        context: Context,
        key: String,
        title: String,
        location: String,
        time: String,
        preview: Boolean = false,
    ) {
        if (!canPost(context)) return
        ensureChannels(context)
        val place = location.ifBlank { "地点未填" }
        val label = if (preview) "上课提醒 · 预览" else "上课提醒"
        val accent = ContextCompat.getColor(context, R.color.tt_accent)
        val views = alertViews(
            context = context,
            label = label,
            left = "$time · $title",
            leftColor = Palette.INK,
            right = place,
            rightColor = accent,
        )
        val n = NotificationCompat.Builder(context, CH_COURSE)
            .setSmallIcon(R.drawable.ic_notification_app)
            .setContentTitle(label)
            .setContentText(place)
            .setCustomBigContentView(views)
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        notify(context, key.hashCode(), n)
    }

    fun persistent(
        context: Context,
        hwTime: String,
        hwTitle: String,
        hwColor: Int,
        courseTime: String,
        courseName: String,
        courseLocation: String,
    ) {
        if (!canPost(context)) return
        ensureChannels(context)
        val accent = ContextCompat.getColor(context, R.color.tt_accent)
        val courseInfo = if (courseTime.isNotBlank()) "$courseTime 上课 · $courseName" else courseName
        val views = RemoteViews(context.packageName, R.layout.notification_persistent).apply {
            setTextViewText(R.id.tvHwTitle, hwTitle)
            setTextViewText(R.id.tvHwTime, hwTime)
            setTextColor(R.id.tvHwTime, hwColor)
            setTextViewTextSize(R.id.tvHwTime, TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextViewText(R.id.tvCourseInfo, courseInfo)
            setTextViewText(R.id.tvCourseLocation, courseLocation)
            setTextColor(R.id.tvCourseLocation, accent)
            setTextViewTextSize(R.id.tvCourseLocation, TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        val small = RemoteViews(context.packageName, R.layout.notification_persistent_small).apply {
            setTextViewText(R.id.tvSmallHwTime, hwTime.ifBlank { "--" })
            setTextViewText(
                R.id.tvSmallCourseLocation,
                courseLocation.ifBlank { courseName }
            )
            setTextColor(R.id.tvSmallHwTime, hwColor)
            setTextColor(R.id.tvSmallCourseLocation, accent)
        }
        val n = NotificationCompat.Builder(context, CH_PERSISTENT)
            .setSmallIcon(R.drawable.ic_notification_app)
            .setCustomContentView(small)
            .setCustomBigContentView(views)
            .setContentIntent(contentIntent(context))
            .setOngoing(false)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        n.flags = n.flags or Notification.FLAG_NO_CLEAR
        notify(context, ID_PERSISTENT, n)
    }

    fun preview(context: Context): Boolean {
        if (!canPost(context)) return false
        ensureChannels(context)
        val now = System.currentTimeMillis()
        val deadline = now + 2 * TimeFmt.DAY + 3 * TimeFmt.HOUR
        homework(
            context = context,
            key = "preview_hw",
            title = "第三章课后作业",
            course = "高等数学",
            deadline = TimeFmt.dateTime(deadline),
            remain = "剩 2天3时",
            coreColor = TimeFmt.C_SOON,
            preview = true,
        )
        course(
            context = context,
            key = "preview_course",
            title = "高等数学",
            location = "教三-201",
            time = "10:20 上课",
            preview = true,
        )
        return true
    }

    private fun alertViews(
        context: Context,
        label: String,
        left: String,
        leftColor: Int,
        right: String,
        rightColor: Int,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.notification_alert).apply {
        setTextViewText(R.id.tvLabel, label)
        setTextViewText(R.id.tvLeft, left)
        setTextColor(R.id.tvLeft, leftColor)
        setTextViewTextSize(R.id.tvLeft, TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextViewText(R.id.tvRight, right)
        setTextColor(R.id.tvRight, rightColor)
        setTextViewTextSize(R.id.tvRight, TypedValue.COMPLEX_UNIT_SP, 15f)
    }

    fun cancelPersistent(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_PERSISTENT)
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    private fun notify(context: Context, id: Int, n: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(abs(id), n)
        } catch (e: SecurityException) {
            // 权限被撤销时静默失败
        }
    }
}
