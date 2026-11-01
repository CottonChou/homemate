package com.caidongzhou.homemate

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.*

// 轻量 3D 户型渲染：等距透视 + 面法线光照 + 昼夜光影。
// 白天：太阳方向光从窗户方向打进来，墙面亮暗分明，地面投家具阴影。
// 夜晚：环境光很暗，开的灯在地板上形成暖色光晕，窗户透出室内光。
private class Seg(val x0: Float, val z0: Float, val x1: Float, val z1: Float, val interior: Boolean)

class Floor3DRenderer {

    var azimuth = 34f          // 相机方位角（度）
    var elevation = 47f        // 相机仰角（度）
    var night = false          // false 白天 / true 黑夜
    var t = 0f                 // 0..1 昼夜过渡动画

    // 屏幕上的 3D 点
    private data class P(val x: Float, val y: Float)

    private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.2f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(50, 0, 0, 0) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 30f; textAlign = Paint.Align.CENTER
    }

    // 把世界坐标 (x, z 平面, y 高度) 投影到屏幕
    private fun project(wx: Float, wz: Float, wy: Float, cx: Float, cy: Float, scale: Float, ox: Float, oy: Float): P {
        val az = Math.toRadians(azimuth.toDouble())
        val el = Math.toRadians(elevation.toDouble())
        val ca = cos(az).toFloat(); val sa = sin(az).toFloat()
        val ce = cos(el).toFloat(); val se = sin(el).toFloat()
        // 绕 y 轴旋转（方位角）
        val rx = wx * ca - wz * sa
        val rz = wx * sa + wz * ca
        // 绕 x 轴俯仰
        val ry = wy * ce - rz * se
        val depth = wy * se + rz * ce
        val persp = 1f / (1f + depth * 0.04f)
        return P(ox + rx * scale * persp, oy + ry * scale * persp * 0.9f)
    }

    fun draw(canvas: Canvas, plan: Plan, viewW: Float, viewH: Float, onTapTest: ((MutableList<Triple<Float, Float, LightDevice>>) -> Unit)? = null) {
        val b = plan.bounds
        val worldW = b[2] - b[0]; val worldD = b[3] - b[1]
        val cx = (b[0] + b[2]) / 2f; val cz = (b[1] + b[3]) / 2f
        val scale = min(viewW / (worldW + 2.4f), viewH / (worldD + 3.2f)) * 0.98f
        val ox = viewW / 2f; val oy = viewH / 2f

        val dayAmt = 1f - t   // 1=白天 0=夜晚

        canvas.drawColor(lerpColor(Color.rgb(26, 34, 56), Color.rgb(9, 12, 22), t))

        val hitLights = mutableListOf<Triple<Float, Float, LightDevice>>()
        val wallsBack = mutableListOf<DrawCmd>()
        val wallsFront = mutableListOf<DrawCmd>()

        // ---- 地板 ----
        for (room in plan.rooms) {
            val (x0, z0, w, d) = room.rect.toList()
            val corners = listOf(
                Triple(x0, z0, 0f), Triple(x0 + w, z0, 0f),
                Triple(x0 + w, z0 + d, 0f), Triple(x0, z0 + d, 0f)
            )
            val pts = corners.map { project(it.first, it.second, it.third, cx, cz, scale, ox, oy) }
            floorPaint.shader = null
            floorPaint.color = floorColor(room.floor, dayAmt)
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                close()
            }
            canvas.drawPath(path, floorPaint)

            // 家具投影（白天才有太阳阴影）
            if (dayAmt > 0.05f) {
                for (f in plan.furniture) {
                    if (f.pos[0] >= x0 && f.pos[0] <= x0 + w && f.pos[1] >= z0 && f.pos[1] <= z0 + d) {
                        val c = project(f.pos[0], f.pos[1], 0.01f, cx, cz, scale, ox, oy)
                        val fw = f.width * scale * 0.45f
                        canvas.save(); canvas.translate(c.x + 6f * dayAmt, c.y + 4f * dayAmt); canvas.rotate(azimuth)
                        canvas.drawOval(-fw, -fw * 0.6f, fw, fw * 0.6f, shadowPaint)
                        canvas.restore()
                    }
                }
            }

            // 夜晚灯光光晕
            if (t > 0.02f) {
                for (l in plan.lights) {
                    if (l.room == room.id && l.on) {
                        val c = project(l.pos[0], l.pos[1], 0.02f, cx, cz, scale, ox, oy)
                        val r = scale * (0.9f + 0.8f * l.brightness) * t
                        glowPaint.shader = RadialGradient(c.x, c.y, r,
                            Color.argb((190 * t * l.brightness).toInt().coerceAtMost(220), 255, 214, 150),
                            Color.argb(0, 255, 190, 110), Shader.TileMode.CLAMP)
                        canvas.drawCircle(c.x, c.y, r, glowPaint)
                        glowPaint.shader = null
                    }
                }
            }
        }

        // ---- 墙 ----
        drawWalls(canvas, plan, cx, cz, scale, ox, oy, dayAmt, wallsBack, wallsFront)
        wallsBack.forEach { it.run(canvas) }
        wallsFront.forEach { it.run(canvas) }

        // ---- 灯具标记 ----
        for (l in plan.lights) {
            val c = project(l.pos[0], l.pos[1], if (l.type == "floor") 0.5f else plan.wallHeight * 0.75f, cx, cz, scale, ox, oy)
            val bright = if (l.on) 1f else 0.35f
            glowPaint.color = if (l.on) lerpColor(Color.rgb(255, 210, 130), Color.rgb(255, 230, 170), 0f)
            else lerpColor(Color.rgb(90, 100, 120), Color.rgb(80, 90, 110), 0f)
            glowPaint.alpha = (255 * bright * (0.4f + 0.6f * t)).toInt().coerceIn(0, 255)
            canvas.drawCircle(c.x, c.y, scale * 0.085f, glowPaint)
            hitLights.add(Triple(c.x, c.y, l))
        }
        onTapTest?.invoke(hitLights)
    }

    private class DrawCmd(val run: (Canvas) -> Unit)

    private fun drawWalls(
        canvas: Canvas, plan: Plan, cx: Float, cz: Float, scale: Float, ox: Float, oy: Float, dayAmt: Float,
        back: MutableList<DrawCmd>, front: MutableList<DrawCmd>
    ) {
        val az = Math.toRadians(azimuth.toDouble())
        val viewDirX = sin(az).toFloat(); val viewDirZ = cos(az).toFloat()


        val segs = mutableListOf<Seg>()
        // 外墙：整个户型外轮廓
        val b = plan.bounds
        segs.add(Seg(b[0], b[1], b[2], b[1], false))
        segs.add(Seg(b[2], b[1], b[2], b[3], false))
        segs.add(Seg(b[2], b[3], b[0], b[3], false))
        segs.add(Seg(b[0], b[3], b[0], b[1], false))
        // 内墙：房间相邻边
        for (i in plan.rooms.indices) {
            for (j in i + 1 until plan.rooms.size) {
                val a = plan.rooms[i].rect; val c = plan.rooms[j].rect
                val eps = 0.05f
                // 垂直共享边
                if (abs(a[0] + a[2] - c[0]) < eps || abs(c[0] + c[2] - a[0]) < eps) {
                    val x = if (abs(a[0] + a[2] - c[0]) < eps) a[0] + a[2] else c[0] + c[2]
                    val z0 = max(a[1], c[1]); val z1 = min(a[1] + a[3], c[1] + c[3])
                    if (z1 - z0 > 0.1f) segs.add(Seg(x, z0, x, z1, true))
                }
                // 水平共享边
                if (abs(a[1] + a[3] - c[1]) < eps || abs(c[1] + c[3] - a[1]) < eps) {
                    val z = if (abs(a[1] + a[3] - c[1]) < eps) a[1] + a[3] else c[1] + c[3]
                    val x0 = max(a[0], c[0]); val x1 = min(a[0] + a[2], c[0] + c[2])
                    if (x1 - x0 > 0.1f) segs.add(Seg(x0, z, x1, z, true))
                }
            }
        }

        for (s in segs) {
            val mx = (s.x0 + s.x1) / 2f; val mz = (s.z0 + s.z1) / 2f
            val facing = (mx - cx) * viewDirX + (mz - cz) * viewDirZ
            // 面向相机的外墙整段裁掉，露出屋内（参考 house-control 的 cutaway）
            if (!s.interior && facing > 0.15f * ((b[2] - b[0]) + (b[3] - b[1])) * 0.25f) continue
            val cmd = buildWallCmd(s, plan, cx, cz, scale, ox, oy, dayAmt)
            if (facing > 0.15f) back.add(cmd) else front.add(cmd)
        }
    }

    private fun buildWallCmd(
        s: Seg?, plan: Plan, cx: Float, cz: Float, scale: Float, ox: Float, oy: Float, dayAmt: Float
    ): DrawCmd {
        val seg = s!!
        val h = if (seg.interior) plan.interiorWallHeight else plan.wallHeight
        // 墙面法线朝向决定亮度
        val nx = (seg.z1 - seg.z0); val nz = -(seg.x1 - seg.x0)
        val nl = sqrt(nx * nx + nz * nz)
        val sun = sunDir()
        val ndl = max(0.25f, (nx * sun[0] + nz * sun[1]) / nl)
        val baseCol = if (seg.interior) Color.rgb(96, 104, 128) else Color.rgb(150, 158, 180)
        val shade = if (night) 0.35f + 0.2f * (1f - dayAmt) else 0.55f + 0.45f * ndl
        val col = Color.rgb(
            (Color.red(baseCol) * shade).toInt().coerceIn(0, 255),
            (Color.green(baseCol) * shade).toInt().coerceIn(0, 255),
            (Color.blue(baseCol) * shade).toInt().coerceIn(0, 255) + 8
        )

        // 找这段墙上的开口
        val horizontal = abs(seg.z1 - seg.z0) < 0.05f
        val openings = plan.openings.filter { o ->
            val onLine = if (horizontal) abs(o.from[1] - seg.z0) < 0.05f && abs(o.to[1] - seg.z0) < 0.05f
                         else abs(o.from[0] - seg.x0) < 0.05f && abs(o.to[0] - seg.x0) < 0.05f
            if (!onLine) false else {
                val s0 = if (horizontal) min(o.from[0], o.to[0]) else min(o.from[1], o.to[1])
                val s1 = if (horizontal) max(o.from[0], o.to[0]) else max(o.from[1], o.to[1])
                val a0 = if (horizontal) min(seg.x0, seg.x1) else min(seg.z0, seg.z1)
                val a1 = if (horizontal) max(seg.x0, seg.x1) else max(seg.z0, seg.z1)
                s0 < a1 - 0.05f && s1 > a0 + 0.05f
            }
        }

        return DrawCmd { canvas ->
            if (openings.isEmpty()) {
                drawWallQuad(canvas, seg.x0, seg.z0, seg.x1, seg.z1, 0f, h, col, cx, cz, scale, ox, oy)
            } else {
                // 带门窗的墙拆成多段
                val sorted = openings.sortedBy { o -> if (horizontal) min(o.from[0], o.to[0]) else min(o.from[1], o.to[1]) }
                var cursor = if (horizontal) min(seg.x0, seg.x1) else min(seg.z0, seg.z1)
                val end = if (horizontal) max(seg.x0, seg.x1) else max(seg.z0, seg.z1)
                for (o in sorted) {
                    val s0 = if (horizontal) min(o.from[0], o.to[0]) else min(o.from[1], o.to[1])
                    val s1 = if (horizontal) max(o.from[0], o.to[0]) else max(o.from[1], o.to[1])
                    val a = maxOf(cursor, s0); val b2 = minOf(end, s1)
                    if (a > cursor + 0.03f) {
                        if (horizontal) drawWallQuad(canvas, cursor, seg.z0, a, seg.z1, 0f, h, col, cx, cz, scale, ox, oy)
                        else drawWallQuad(canvas, seg.x0, cursor, seg.x1, a, 0f, h, col, cx, cz, scale, ox, oy)
                    }
                    if (b2 > a + 0.03f) {
                        when (o.type) {
                            "window" -> {
                                // 窗户：留玻璃透光
                                if (o.sill > 0.03f) drawWallQuad(canvas, seg.x0, seg.z0, seg.x1, seg.z1, 0f, o.sill, col, cx, cz, scale, ox, oy, fixedAxis = horizontal)
                                drawWallQuad(canvas, seg.x0, seg.z0, seg.x1, seg.z1, o.head, h, col, cx, cz, scale, ox, oy)
                                val glass = Color.argb(if (night) 90 else 60, if (night) 255 else 200, if (night) 230 else 225, 255)
                                drawGlassQuad(canvas, seg, o, glass, cx, cz, scale, ox, oy)
                            }
                            "door" -> { /* 门洞：直接留空 */ }
                            "open" -> { /* 敞开：留空 */ }
                        }
                    }
                    cursor = maxOf(cursor, b2)
                }
                if (end > cursor + 0.03f) {
                    if (horizontal) drawWallQuad(canvas, cursor, seg.z0, end, seg.z1, 0f, h, col, cx, cz, scale, ox, oy)
                    else drawWallQuad(canvas, seg.x0, cursor, seg.x1, end, 0f, h, col, cx, cz, scale, ox, oy)
                }
            }
        }
    }

    private fun drawWallQuad(
        canvas: Canvas, x0: Float, z0: Float, x1: Float, z1: Float,
        yLow: Float, yHigh: Float, col: Int, cx: Float, cz: Float, scale: Float, ox: Float, oy: Float,
        fixedAxis: Boolean? = null
    ) {
        val p1 = project(x0, z0, yHigh, cx, cz, scale, ox, oy)
        val p2 = project(x1, z1, yHigh, cx, cz, scale, ox, oy)
        val p3 = project(x1, z1, yLow, cx, cz, scale, ox, oy)
        val p4 = project(x0, z0, yLow, cx, cz, scale, ox, oy)
        wallPaint.color = col
        canvas.drawPath(Path().apply {
            moveTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); lineTo(p4.x, p4.y); close()
        }, wallPaint)
    }

    private fun drawGlassQuad(canvas: Canvas, s: Seg, o: Opening, col: Int, cx: Float, cz: Float, scale: Float, ox: Float, oy: Float) {
        val horizontal = abs(s.z1 - s.z0) < 0.05f
        val x0 = if (horizontal) min(o.from[0], o.to[0]) else s.x0
        val x1 = if (horizontal) max(o.from[0], o.to[0]) else s.x1
        val z0 = if (horizontal) s.z0 else min(o.from[1], o.to[1])
        val z1 = if (horizontal) s.z1 else max(o.from[1], o.to[1])
        val p1 = project(x0, z0, o.head, cx, cz, scale, ox, oy)
        val p2 = project(x1, z1, o.head, cx, cz, scale, ox, oy)
        val p3 = project(x1, z1, o.sill, cx, cz, scale, ox, oy)
        val p4 = project(x0, z0, o.sill, cx, cz, scale, ox, oy)
        wallPaint.color = col
        canvas.drawPath(Path().apply {
            moveTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); lineTo(p4.x, p4.y); close()
        }, wallPaint)
    }


    // 太阳方向（世界坐标，随方位角缓慢变化）
    private fun sunDir(): FloatArray {
        val a = Math.toRadians((azimuth + 120.0))
        return floatArrayOf(cos(a).toFloat() * 0.8f, sin(a).toFloat() * 0.6f)
    }

    private fun floorColor(kind: String, dayAmt: Float): Int {
        val day = when (kind) {
            "oak" -> Triple(196, 164, 122); "walnut" -> Triple(146, 112, 86)
            "tile" -> Triple(198, 200, 206); "stone" -> Triple(160, 158, 150)
            else -> Triple(180, 160, 130)
        }
        val n = Triple(38, 42, 56)
        return Color.rgb(
            lerp(n.first, day.first, dayAmt),
            lerp(n.second, day.second, dayAmt),
            lerp(n.third, day.third, dayAmt)
        )
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).roundToInt()
    private fun lerpColor(c1: Int, c2: Int, t: Float): Int = Color.argb(
        lerp(Color.alpha(c1), Color.alpha(c2), t),
        lerp(Color.red(c1), Color.red(c2), t),
        lerp(Color.green(c1), Color.green(c2), t),
        lerp(Color.blue(c1), Color.blue(c2), t)
    )
}
