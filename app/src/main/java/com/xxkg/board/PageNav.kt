package com.xxkg.board

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

/** 作业 / 课表 / 个人三个页面的横向切换。 */
object PageNav {

    const val HOMEWORK = 0
    const val TIMETABLE = 1
    const val ME = 2

    fun tabOf(activity: Activity): Int = when (activity) {
        is TimetableActivity -> TIMETABLE
        is SettingsActivity -> ME
        else -> HOMEWORK
    }

    fun go(activity: Activity, target: Int) {
        val from = tabOf(activity)
        if (from == target) return

        val cls = when (target) {
            TIMETABLE -> TimetableActivity::class.java
            ME -> SettingsActivity::class.java
            else -> MainActivity::class.java
        }
        PageTransition.capture(activity)
        PageTransition.direction = if (target > from) 1 else -1
        val intent = Intent(activity, cls).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
        )
        activity.startActivity(intent)
        activity.overridePendingTransition(0, 0)
        // 作业页是根页面，始终保留；课表/个人之间切换时只保留一个，避免栈越叠越深
        if (from != HOMEWORK) activity.finish()
    }
}

/** 底部导航：三项统一尺寸、点击区域和选中态。 */
object BottomNav {

    private val itemIds = intArrayOf(
        R.id.navHomework, R.id.navTimetable, R.id.navMe
    )
    private val iconIds = intArrayOf(
        R.id.navHomeworkIcon, R.id.navTimetableIcon, R.id.navMeIcon
    )
    private val labelIds = intArrayOf(
        R.id.navHomeworkLabel, R.id.navTimetableLabel, R.id.navMeLabel
    )

    fun bind(activity: Activity, current: Int, onReselect: (() -> Unit)? = null) {
        val selected = ContextCompat.getColor(activity, R.color.tt_ink)
        val normal = ContextCompat.getColor(activity, R.color.tt_muted2)
        for (i in itemIds.indices) {
            val item = activity.findViewById<View>(itemIds[i])
            val icon = activity.findViewById<ImageView>(iconIds[i])
            val label = activity.findViewById<TextView>(labelIds[i])
            val color = if (i == current) selected else normal
            icon.imageTintList = ColorStateList.valueOf(color)
            label.setTextColor(color)
            item.setOnClickListener {
                if (i == current) onReselect?.invoke() else PageNav.go(activity, i)
            }
            // 点图标/文字也走同一个动作，避免小图标自己吃掉点击
            icon.setOnClickListener { item.performClick() }
            label.setOnClickListener { item.performClick() }
        }
    }
}
