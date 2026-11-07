package com.xxkg.board

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/** 手动添加 / 编辑一条作业或考试。 */
class ManualTaskEditActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var etTitle: EditText
    private lateinit var etCourse: EditText
    private lateinit var rgType: android.widget.RadioGroup
    private lateinit var tvPublish: TextView
    private lateinit var tvDeadline: TextView
    private lateinit var spExpiry: Spinner

    private var existingKey = ""
    private var publishTime = 0L
    private var deadline = 0L

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val expiryLabels = arrayOf("移入已完成", "移入已过期", "自动删除")
    private val expiryValues = arrayOf(
        ManualExpiry.DONE, ManualExpiry.EXPIRED, ManualExpiry.DELETE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        setContentView(R.layout.activity_manual_task)

        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        etTitle = findViewById(R.id.etTitle)
        etCourse = findViewById(R.id.etCourse)
        rgType = findViewById(R.id.rgType)
        tvPublish = findViewById(R.id.tvPublish)
        tvDeadline = findViewById(R.id.tvDeadline)
        spExpiry = findViewById(R.id.spExpiry)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSave).setOnClickListener { save() }

        spExpiry.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            expiryLabels
        )

        val now = System.currentTimeMillis()
        publishTime = now
        deadline = now + 24L * 60 * 60 * 1000

        existingKey = intent.getStringExtra(EXTRA_KEY).orEmpty()
        if (existingKey.isNotEmpty()) {
            val task = ManualTasks.find(prefs, existingKey)
            if (task != null) {
                etTitle.setText(task.title)
                etCourse.setText(task.courseName)
                if (task.type == TaskType.EXAM) rgType.check(R.id.rbExam)
                publishTime = if (task.publishTime > 0) task.publishTime else now
                deadline = if (task.endTime > 0) task.endTime else now + 24L * 60 * 60 * 1000
                val idx = expiryValues.indexOf(task.expiryAction).coerceAtLeast(0)
                spExpiry.setSelection(idx)
            }
        }
        refreshTimeTexts()

        tvPublish.setOnClickListener { pickDateTime(publishTime) { publishTime = it; refreshTimeTexts() } }
        tvDeadline.setOnClickListener { pickDateTime(deadline) { deadline = it; refreshTimeTexts() } }
    }

    private fun refreshTimeTexts() {
        tvPublish.text = dateFmt.format(Date(publishTime))
        tvDeadline.text = dateFmt.format(Date(deadline))
    }

    private fun pickDateTime(initial: Long, onPicked: (Long) -> Unit) {
        val c = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        val picked = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, day)
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onPicked(picked.timeInMillis)
                    },
                    c.get(Calendar.HOUR_OF_DAY),
                    c.get(Calendar.MINUTE),
                    true
                ).show()
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun save() {
        val title = etTitle.text.toString().trim()
        if (title.isEmpty()) {
            toast("请填写作业 / 考试名称")
            return
        }
        val now = System.currentTimeMillis()
        if (deadline <= now) {
            toast("截止时间需要晚于现在")
            return
        }
        if (deadline <= publishTime) {
            toast("截止时间需要晚于发布时间")
            return
        }
        val type = if (rgType.checkedRadioButtonId == R.id.rbExam) TaskType.EXAM else TaskType.WORK
        val action = expiryValues[spExpiry.selectedItemPosition.coerceIn(0, expiryValues.size - 1)]
        val source = ManualTasks.DEFAULT_SOURCE
        val key = existingKey.ifEmpty { "MANUAL|" + UUID.randomUUID().toString().replace("-", "") }
        val task = Task(
            key = key,
            title = title,
            courseName = etCourse.text.toString().trim(),
            type = type,
            status = TaskStatus.TODO,
            endTime = deadline,
            publishTime = publishTime,
            startTime = publishTime,
            stageTime = publishTime,
            manual = true,
            source = source,
            expiryAction = action,
            statusText = source,
        )
        ManualTasks.upsert(prefs, task)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_KEY = "manual_key"
    }
}
