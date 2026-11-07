package com.xxkg.board

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * SwipeRefreshLayout 的圆形指示器自带 elevation，停靠在顶部时阴影会漏到内容区。
 * 这里只把它的 elevation 清掉；指示器的显示/隐藏仍完全交给 SwipeRefreshLayout，
 * 避免把指示器隐藏掉后第二次下拉不再出现。
 */
class CleanSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SwipeRefreshLayout(context, attrs) {

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
        circleView?.elevation = 0f
    }
}
