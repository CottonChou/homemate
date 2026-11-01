package com.caidongzhou.homemate

import android.graphics.Canvas
import android.view.MotionEvent
import kotlin.math.*

// 3D 户型画布视图：拖动旋转、点击灯切换，昼夜由属性控制后 invalidate。
class Home3DView(context: android.content.Context) : android.view.View(context) {

    var plan: Plan = Plan.demo()
        set(value) { field = value; renderer.azimuth = value.cameraAzimuth; renderer.elevation = value.cameraElevation; invalidate() }
    var onLightToggle: ((LightDevice) -> Unit)? = null
    var onRoomTap: ((Room) -> Unit)? = null

    private val renderer = Floor3DRenderer()
    private var lastX = 0f; private var lastY = 0f
    private var dragging = false
    private var nightAnimFrom = 0f
    private var nightAnimTo = 0f
    private var nightAnimStart = 0L
    private var lastHits = listOf<Triple<Float, Float, LightDevice>>()

    fun setNight(n: Boolean, animate: Boolean = true) {
        val target = if (n) 1f else 0f
        if (nightAnimTo == target && nightAnimStart == 0L && renderer.t == target) return
        nightAnimFrom = renderer.t
        nightAnimTo = target
        nightAnimStart = if (animate) System.currentTimeMillis() else 0L
        if (!animate) renderer.t = target
        invalidate()
    }

    fun toggleNight() = setNight(renderer.t < 0.5f)

    override fun onDraw(canvas: Canvas) {
        if (nightAnimStart > 0L) {
            val p = ((System.currentTimeMillis() - nightAnimStart) / 900f).coerceIn(0f, 1f)
            val e = p * p * (3f - 2f * p)  // smoothstep
            renderer.t = nightAnimFrom + (nightAnimTo - nightAnimFrom) * e
            if (p < 1f) postInvalidateOnAnimation() else nightAnimStart = 0L
        }
        renderer.draw(canvas, plan, width.toFloat(), height.toFloat()) { lastHits = it }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { lastX = e.x; lastY = e.y; dragging = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lastX; val dy = e.y - lastY
                if (abs(dx) > 3 || abs(dy) > 3) {
                    dragging = true
                    renderer.azimuth += dx * 0.35f
                    renderer.elevation = (renderer.elevation - dy * 0.25f).coerceIn(15f, 80f)
                    lastX = e.x; lastY = e.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    var best: LightDevice? = null; var bestD = Float.MAX_VALUE
                    for ((hx, hy, l) in lastHits) {
                        val d = (hx - e.x) * (hx - e.x) + (hy - e.y) * (hy - e.y)
                        if (d < bestD && d < 60f * 60f) { bestD = d; best = l }
                    }
                    best?.let { onLightToggle?.invoke(it) }
                }
            }
        }
        return true
    }
}
