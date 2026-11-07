package com.xxkg.board

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * 只让底部导航栏以上的内容左右滑动，底栏保持不动。
 * 页面仍是 Activity；切换时关闭窗口动画，进入页把上一页截图作为底层一起滑出。
 */
object PageTransition {

    /** 1：向右翻页（新页从右侧进）；-1：向左翻页；0：不做动画。 */
    var direction: Int = 0

    private var snapshot: Bitmap? = null

    /** 在 startActivity 之前调用，截下当前页底栏以上的内容。 */
    fun capture(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root = content.getChildAt(0) as? ViewGroup ?: return
        val nav = findNav(root) ?: return
        val w = root.width
        val h = navTop(root, nav)
        if (w <= 0 || h <= 0) return
        snapshot?.recycle()
        snapshot = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                root.draw(Canvas(bmp))
            }
        } catch (e: Exception) {
            null
        }
    }

    fun enter(activity: Activity) {
        val dir = direction
        direction = 0
        if (dir == 0) return

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root = content.getChildAt(0) as? ViewGroup ?: return
        val nav = findNav(root) ?: return
        val width = activity.resources.displayMetrics.widthPixels.toFloat()
        val dp2 = 2 * activity.resources.displayMetrics.density
        val old = snapshot
        snapshot = null

        val views = ArrayList<View>()
        val navParent = nav.parent as? View ?: nav
        for (i in 0 until root.childCount) {
            val v = root.getChildAt(i)
            if (v === navParent || v === nav) continue
            if (i == root.childCount - 1) continue
            if (v.layoutParams?.height in 1..dp2.toInt()) continue
            views.add(v)
        }
        val offset = if (dir > 0) width else -width
        for (v in views) {
            v.translationX = offset
            v.animate()
                .translationX(0f)
                .setDuration(240L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        if (old == null) return
        root.post {
            val barTop = navTop(root, nav)
            if (barTop <= 0 || old.isRecycled) {
                old.recycle()
                return@post
            }
            val overlay = ImageView(activity).apply {
                setImageBitmap(old)
                scaleType = ImageView.ScaleType.FIT_XY
            }
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                barTop
            )
            content.addView(overlay, lp)
            overlay.animate()
                .translationX(if (dir > 0) -width else width)
                .setDuration(240L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    content.removeView(overlay)
                    if (!old.isRecycled) old.recycle()
                }
                .start()
        }
    }

    private fun navTop(root: View, nav: View): Int {
        val rootLoc = IntArray(2)
        val navLoc = IntArray(2)
        root.getLocationInWindow(rootLoc)
        nav.getLocationInWindow(navLoc)
        return navLoc[1] - rootLoc[1]
    }

    private fun findNav(root: ViewGroup): View? {
        for (i in 0 until root.childCount) {
            val v = root.getChildAt(i)
            v.findViewById<View>(R.id.navHomework)?.let { return it }
        }
        return root.findViewById(R.id.navHomework)
    }
}
