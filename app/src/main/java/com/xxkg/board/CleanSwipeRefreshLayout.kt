package com.xxkg.board

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * SwipeRefreshLayout 在“下拉但未触发刷新、又原路推回”时，圆形指示器偶尔会留下
 * 一点阴影/灰边。这里在手指抬起且没有真正刷新时，把手势结束后残留的
 * CircleImageView 强制隐藏；下次下拉时 SwipeRefreshLayout 会重新显示它。
 */
class CleanSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SwipeRefreshLayout(context, attrs) {

    private val hideRunnable = Runnable {
        if (!isRefreshing) {
            circleView?.let {
                it.animate().cancel()
                it.alpha = 0f
                it.visibility = View.GONE
            }
        }
    }

    private val circleView: View? by lazy {
        try {
            val f = SwipeRefreshLayout::class.java.getDeclaredField("mCircleView")
            f.isAccessible = true
            f.get(this) as? View
        } catch (e: Exception) {
            null
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        // CircleImageView 自带 elevation，停在顶部时阴影会漏到内容区；去掉阴影
        circleView?.elevation = 0f
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            postDelayed(hideRunnable, 280L)
        }
        return handled
    }

    override fun setRefreshing(refreshing: Boolean) {
        super.setRefreshing(refreshing)
        if (!refreshing) postDelayed(hideRunnable, 280L)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hideRunnable)
        super.onDetachedFromWindow()
    }
}
