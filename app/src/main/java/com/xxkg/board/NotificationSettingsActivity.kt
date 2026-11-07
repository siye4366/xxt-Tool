package com.xxkg.board

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

/** 消息提醒设置。 */
class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var swEnabled: SwitchCompat
    private lateinit var contentGroup: ViewGroup
    private lateinit var offsetList: LinearLayout
    private lateinit var tvCourseMinutes: TextView
    private lateinit var swIgnoreBreak: SwitchCompat
    private lateinit var swPersistent: SwitchCompat
    private var binding = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                setEnabled(true)
            } else {
                setEnabled(false)
                toast("没有通知权限，消息提醒未开启")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        setContentView(R.layout.activity_notification_settings)
        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        swEnabled = findViewById(R.id.swEnabled)
        contentGroup = findViewById(R.id.contentGroup)
        offsetList = findViewById(R.id.offsetList)
        tvCourseMinutes = findViewById(R.id.tvCourseMinutes)
        swIgnoreBreak = findViewById(R.id.swIgnoreBreak)
        swPersistent = findViewById(R.id.swPersistent)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAddOffset).setOnClickListener { addOffset() }
        tvCourseMinutes.setOnClickListener { editCourseMinutes() }
        findViewById<View>(R.id.btnPreview).setOnClickListener {
            if (!prefs.notifyEnabled) return@setOnClickListener
            if (!ReminderNotifications.preview(this)) {
                toast("请先授予通知权限")
            }
        }

        swEnabled.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            if (checked && !hasPermission()) {
                binding = true
                swEnabled.isChecked = false
                binding = false
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                setEnabled(checked)
            }
        }
        swIgnoreBreak.setOnCheckedChangeListener { _, checked ->
            if (!binding) {
                prefs.ignoreBreak = checked
                ReminderScheduler.sync(this)
            }
        }
        swPersistent.setOnCheckedChangeListener { _, checked ->
            if (!binding) {
                prefs.persistentNotification = checked
                if (!checked) {
                    ReminderNotifications.cancelPersistent(this)
                } else if (prefs.notifyEnabled) {
                    ReminderEngine.refreshPersistent(this)
                }
                ReminderScheduler.sync(this)
            }
        }

        bind()
        if (prefs.notifyEnabled && prefs.persistentNotification) {
            ReminderEngine.refreshPersistent(this)
        }
    }

    private fun bind() {
        binding = true
        swEnabled.isChecked = prefs.notifyEnabled
        swIgnoreBreak.isChecked = prefs.ignoreBreak
        swPersistent.isChecked = prefs.persistentNotification
        binding = false
        tvCourseMinutes.text = "${prefs.courseMinutes} 分钟"
        applyEnabledState(prefs.notifyEnabled)
        renderOffsets()
    }

    private fun setEnabled(enabled: Boolean) {
        if (swEnabled.isChecked != enabled) {
            binding = true
            swEnabled.isChecked = enabled
            binding = false
        }
        prefs.notifyEnabled = enabled
        if (enabled) {
            prefs.clearNotified()
            ReminderNotifications.ensureChannels(this)
            ReminderScheduler.sync(this)
        } else {
            ReminderScheduler.sync(this)
            ReminderNotifications.cancelAll(this)
        }
        if (enabled) ReminderEngine.refreshPersistent(this)
        applyEnabledState(enabled)
        toast(if (enabled) "消息提醒已开启" else "消息提醒已关闭")
    }

    private fun applyEnabledState(enabled: Boolean) {
        contentGroup.alpha = if (enabled) 1f else 0.38f
        setChildrenEnabled(contentGroup, enabled)
    }

    private fun setChildrenEnabled(group: ViewGroup, enabled: Boolean) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            child.isEnabled = enabled
            if (child is ViewGroup) setChildrenEnabled(child, enabled)
        }
    }

    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun renderOffsets() {
        offsetList.removeAllViews()
        val hours = ReminderEngine.offsets(prefs)
        if (hours.isEmpty()) {
            val empty = TextView(this).apply {
                text = "未设置（不会发送作业提醒）"
                setTextColor(Palette.MUTED2)
                textSize = 12f
                setPadding(0, dp(8), 0, dp(8))
            }
            offsetList.addView(empty)
            return
        }
        for (h in hours) {
            val row = layoutInflater.inflate(R.layout.item_reminder_offset, offsetList, false)
            row.findViewById<TextView>(R.id.tvOffset).text = "截止前 $h 小时"
            row.findViewById<TextView>(R.id.btnDeleteOffset).setOnClickListener {
                if (!prefs.notifyEnabled) return@setOnClickListener
                val next = ReminderEngine.offsets(prefs).filterNot { it == h }
                prefs.homeworkOffsets = next.joinToString(",")
                renderOffsets()
            }
            offsetList.addView(row)
        }
    }

    private fun addOffset() {
        if (!prefs.notifyEnabled) return
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "例如 24（最低 1 小时）"
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("添加作业提醒")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("添加") { _, _ ->
                val h = input.text.toString().trim().toIntOrNull()
                if (h == null || h < 1) {
                    toast("请输入不小于 1 的整数小时")
                    return@setPositiveButton
                }
                val next = (ReminderEngine.offsets(prefs) + h).distinct().sortedDescending()
                prefs.homeworkOffsets = next.joinToString(",")
                renderOffsets()
            }
            .show()
    }

    private fun editCourseMinutes() {
        if (!prefs.notifyEnabled) return
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.courseMinutes.toString())
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("上课前多少分钟提醒")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val m = input.text.toString().trim().toIntOrNull()
                if (m == null || m < 1) {
                    toast("请输入不小于 1 的分钟数")
                    return@setPositiveButton
                }
                prefs.courseMinutes = m.coerceAtMost(720)
                tvCourseMinutes.text = "${prefs.courseMinutes} 分钟"
            }
            .show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
