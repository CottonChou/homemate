package com.caidongzhou.homemate

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.*

/**
 * 3D 户型：等距剖切的「建筑模型」风格。
 *
 * 设计约束（对齐参考图的观感）：
 * 1. 竖直方向必须正确：墙顶向上、越高的面越靠近相机，这样家具不会被自己的底座盖住。
 * 2. 远侧外墙保留完整高度，近侧外墙只留矮槛，形成剖面观感。
 * 3. 地板与家具都用大色块，不画木纹/砖缝等高频噪声。
 * 4. 白天接近原色的高明度光照；夜晚冷色环境光 + 暖色灯晕。
 */
class Floor3DRenderer {

    var azimuth = 34f
    var elevation = 47f
    var t = 0f                 // 0 = 白天, 1 = 夜晚
    var topInset = 0f
    var bottomInset = 0f

    private data class P(val x: Float, val y: Float)

    private class Face(
        val xs: FloatArray,
        val ys: FloatArray,
        val zs: FloatArray,
        val base: Int,
        val top: Boolean
    )

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.0f
        strokeJoin = Paint.Join.ROUND
    }
    private val soft = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    // 相机基向量
    private var sa = 0.5f; private var ca = 0.85f; private var se = 0.7f; private var ce = 0.7f
    // 墙高（视觉剖面高度，不是真实层高）
    private var extH = 1.30f
    private var intH = 0.62f
    private var nearH = 0.30f
    private var lights: List<LightDevice> = emptyList()

    // ---------- 投影 ----------
    private fun setup() {
        val az = Math.toRadians(azimuth.toDouble())
        val el = Math.toRadians(elevation.toDouble())
        sa = sin(az).toFloat(); ca = cos(az).toFloat()
        se = sin(el).toFloat(); ce = cos(el).toFloat()
    }

    private fun rot(x: Float, z: Float): Pair<Float, Float> =
        (x * ca - z * sa) to (x * sa + z * ca)

    /** 越大越远。高度会减少深度：越高的点离俯视相机越近。 */
    private fun depthOf(x: Float, y: Float, z: Float): Float {
        val (_, rz) = rot(x, z)
        return rz * ce - y * se
    }

    private fun project(x: Float, z: Float, y: Float, scale: Float, ox: Float, oy: Float): P {
        val (rx, rz) = rot(x, z)
        val persp = 1f / (1f + depthOf(x, y, z) * 0.010f)
        return P(ox + rx * scale * persp, oy - (y * ce + rz * se) * scale * persp)
    }

    // ---------- 光照 ----------
    private fun sunDir(): FloatArray {
        val a = Math.toRadians(azimuth + 150.0)
        return floatArrayOf(cos(a).toFloat() * 0.55f, 0.72f, sin(a).toFloat() * 0.55f)
    }

    private fun shade(base: Int, nx: Float, ny: Float, nz: Float, top: Boolean,
                      cx: Float, cy: Float, cz: Float): Int {
        val (rnx, rnz) = if (abs(nx) < 1e-4f && abs(nz) < 1e-4f) (0f to 0f) else {
            val rx = nx * ca - nz * sa
            val rz = nx * sa + nz * ca
            rx to rz
        }
        val sun = sunDir()
        val ndl = max(0f, rnx * sun[0] + ny * sun[1] + rnz * sun[2])

        // 白天：整体接近原色，只保留很轻的方向感，避免大面积发灰。
        val dayK = (if (top) 1.00f else 0.88f) + ndl * 0.08f
        // 夜晚：冷色环境光，顶面略亮。
        val nightK = (if (top) 0.27f else 0.17f) + ndl * 0.05f
        val k = dayK + (nightK - dayK) * t

        // 夜晚冷色调偏移（偏蓝）
        val tintR = 1.00f - 0.26f * t
        val tintG = 1.00f - 0.13f * t
        val tintB = 1.00f + 0.28f * t

        var r = Color.red(base) * k * tintR
        var g = Color.green(base) * k * tintG
        var b = Color.blue(base) * k * tintB

        if (t > 0.02f) {
            var warm = 0f
            for (l in lights) {
                if (!l.on) continue
                val ly = if (l.type == "floor") 0.55f else extH * 0.82f
                val dist = sqrt((cx - l.pos[0]).pow(2) + (cy - ly).pow(2) + (cz - l.pos[1]).pow(2))
                val fall = max(0f, 1f - dist / 2.7f)
                if (fall <= 0f) continue
                val facing = if (top) 1f else 0.42f
                warm += fall * fall * l.brightness * facing * t
            }
            val w = warm.coerceAtMost(1.15f)
            r += 132f * w
            g += 98f * w
            b += 56f * w
        }
        return Color.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
    }

    // ---------- 主入口 ----------
    fun draw(canvas: Canvas, plan: Plan, viewW: Float, viewH: Float,
             onTapTest: ((MutableList<Triple<Float, Float, LightDevice>>) -> Unit)? = null) {
        setup()
        lights = plan.lights
        extH = plan.wallHeight.coerceIn(0.9f, 1.55f)
        intH = plan.interiorWallHeight.coerceIn(0.32f, extH * 0.75f)
        nearH = (extH * 0.23f).coerceIn(0.18f, 0.42f)

        val b = plan.bounds
        val w0 = b[0]; val z0 = b[1]; val w1 = b[2]; val z1 = b[3]
        val availH = (viewH - topInset - bottomInset).coerceAtLeast(120f)

        val pad = 0.28f
        var minSx = Float.MAX_VALUE; var maxSx = -Float.MAX_VALUE
        var minSy = Float.MAX_VALUE; var maxSy = -Float.MAX_VALUE
        for (px in floatArrayOf(w0 - pad, w1 + pad)) {
            for (pz in floatArrayOf(z0 - pad, z1 + pad)) {
                for (py in floatArrayOf(-0.18f, extH)) {
                    val p = project(px, pz, py, 1f, 0f, 0f)
                    minSx = min(minSx, p.x); maxSx = max(maxSx, p.x)
                    minSy = min(minSy, p.y); maxSy = max(maxSy, p.y)
                }
            }
        }
        val scale = min((viewW - 20f) / (maxSx - minSx), (availH - 16f) / (maxSy - minSy)) * 1.06f
        val ox = viewW / 2f - (minSx + maxSx) / 2f * scale
        val oy = topInset + availH / 2f - (minSy + maxSy) / 2f * scale

        // 背景：白天近白，夜晚深蓝黑
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        bg.shader = LinearGradient(0f, 0f, 0f, viewH,
            lerpColor(Color.rgb(252, 253, 255), Color.rgb(13, 19, 34), t),
            lerpColor(Color.rgb(236, 240, 247), Color.rgb(5, 8, 17), t), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, viewW, viewH, bg)
        bg.shader = null

        // 0) 模型落地阴影：让建筑模型「坐」在背景上，而不是漂浮
        if (t < 0.85f) {
            val pb = plan.bounds
            for (pass in 0..1) {
                val grow = if (pass == 0) 0.34f else 0.16f
                val alpha = if (pass == 0) (16 * (1f - t)).toInt() else (26 * (1f - t)).toInt()
                soft.color = Color.argb(alpha, 64, 78, 104)
                val x0 = pb[0] - 0.22f - grow; val z0 = pb[1] - 0.22f - grow
                val x1 = pb[2] + 0.22f + grow; val z1 = pb[3] + 0.22f + grow
                path.reset()
                val cs = listOf(x0 to z0, x1 to z0, x1 to z1, x0 to z1)
                cs.forEachIndexed { i, c ->
                    val q = project(c.first + 0.10f, c.second + 0.14f, -0.17f, scale, ox, oy)
                    if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
                }
                path.close()
                canvas.drawPath(path, soft)
            }
        }

        // 1) 底座
        drawPlinth(canvas, plan, scale, ox, oy)

        // 2) 各房间地板：纯色块
        for (room in plan.rooms) {
            val r = room.rect
            val cx = r[0] + r[2] / 2f
            val cz = r[1] + r[3] / 2f
            val col = shade(floorBase(room.floor), 0f, 1f, 0f, true, cx, 0f, cz)
            drawFloorQuad(canvas, r[0], r[1], r[0] + r[2], r[1] + r[3], 0f, col, scale, ox, oy)
        }
        // 地板只有极淡的大格，避免噪点
        drawFloorHints(canvas, plan, scale, ox, oy)

        // 3) 白天家具投影
        if (t < 0.85f) {
            for (f in plan.furniture) {
                if (f.type == "rug") continue
                drawFurnitureShadow(canvas, f, scale, ox, oy, ((1f - t) * 30).toInt())
            }
        }

        // 4) 夜晚灯光在地面的暖色光池
        if (t > 0.02f) {
            for (l in plan.lights) {
                if (!l.on) continue
                val p = project(l.pos[0], l.pos[1], 0.01f, scale, ox, oy)
                val rad = scale * (1.05f + 0.75f * l.brightness)
                glow.shader = RadialGradient(p.x, p.y, rad,
                    Color.argb((118 * t).toInt(), 255, 206, 145),
                    Color.argb(0, 255, 190, 120), Shader.TileMode.CLAMP)
                canvas.drawCircle(p.x, p.y, rad, glow)
                glow.shader = null
            }
        }

        // 5) 墙面 + 家具（统一按相机深度从远到近绘制）
        val faces = ArrayList<Face>(260)
        buildWalls(plan, faces)
        buildFurniture(plan, faces)
        faces.sortByDescending { f ->
            var d = 0f
            for (i in 0 until 4) {
                d += depthOf(f.xs[i], f.ys[i], f.zs[i])
            }
            d / 4f
        }
        for (f in faces) {
            val mx = f.xs.average().toFloat()
            val my = f.ys.average().toFloat()
            val mz = f.zs.average().toFloat()
            var nx = 0f; var ny = 0f; var nz = 0f
            if (f.top) {
                ny = 1f
            } else {
                val ux = f.xs[1] - f.xs[0]; val uy = f.ys[1] - f.ys[0]; val uz = f.zs[1] - f.zs[0]
                val vx = f.xs[2] - f.xs[0]; val vy = f.ys[2] - f.ys[0]; val vz = f.zs[2] - f.zs[0]
                nx = uy * vz - uz * vy; ny = uz * vx - ux * vz; nz = ux * vy - uy * vx
                val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f)
                nx /= len; ny /= len; nz /= len
            }
            fill.color = shade(f.base, nx, ny, nz, f.top, mx, my, mz)
            path.reset()
            for (i in 0 until 4) {
                val p = project(f.xs[i], f.zs[i], f.ys[i], scale, ox, oy)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            canvas.drawPath(path, fill)
            if (f.top) {
                edge.color = Color.argb((lerp(40f, 56f, t)).toInt(), 44, 54, 76)
                canvas.drawPath(path, edge)
            }
        }

        // 6) 灯具标记
        val hits = ArrayList<Triple<Float, Float, LightDevice>>()
        for (l in plan.lights) {
            val ly = if (l.type == "floor") 0.5f else extH * 0.86f
            val p = project(l.pos[0], l.pos[1], ly, scale, ox, oy)
            if (l.on) {
                val r = scale * 0.075f
                glow.shader = RadialGradient(p.x, p.y, r * 2.4f,
                    Color.argb((210f * max(t, 0.25f)).toInt(), 255, 222, 168),
                    Color.argb(0, 255, 205, 140), Shader.TileMode.CLAMP)
                canvas.drawCircle(p.x, p.y, r * 2.4f, glow)
                glow.shader = null
                fill.color = Color.rgb(255, 240, 208)
            } else {
                fill.color = lerpColor(Color.rgb(186, 194, 208), Color.rgb(74, 86, 110), t)
            }
            fill.alpha = if (l.on) 255 else 210
            canvas.drawCircle(p.x, p.y, scale * 0.045f, fill)
            fill.alpha = 255
            hits.add(Triple(p.x, p.y, l))
        }
        onTapTest?.invoke(hits)
    }

    // ---------- 材质 ----------
    private fun floorBase(kind: String): Int = when (kind) {
        "oak" -> Color.rgb(222, 194, 155)
        "walnut" -> Color.rgb(196, 158, 124)
        "tile" -> Color.rgb(226, 229, 234)
        "stone" -> Color.rgb(208, 204, 196)
        else -> Color.rgb(216, 190, 152)
    }

    private fun drawPlinth(canvas: Canvas, plan: Plan, scale: Float, ox: Float, oy: Float) {
        val b = plan.bounds
        val m = 0.22f
        val x0 = b[0] - m; val z0 = b[1] - m; val x1 = b[2] + m; val z1 = b[3] + m
        val y0 = -0.16f; val y1 = -0.005f
        val base = lerpColor(Color.rgb(228, 231, 236), Color.rgb(19, 25, 40), t)
        // 侧面（只画朝向相机的一侧）
        val sides = listOf(
            floatArrayOf(x0, x0, x1, x1) to floatArrayOf(z0, z0, z0, z0),
            floatArrayOf(x0, x0, x1, x1) to floatArrayOf(z1, z1, z1, z1),
            floatArrayOf(x0, x0, x0, x0) to floatArrayOf(z0, z1, z1, z0),
            floatArrayOf(x1, x1, x1, x1) to floatArrayOf(z0, z1, z1, z0)
        )
        val normals = listOf(-0f to -1f, 0f to 1f, -1f to -0f, 1f to -0f)
        for ((i, sd) in sides.withIndex()) {
            val n = normals[i]
            if (n.first * sa + n.second * ca > -0.001f) continue
            path.reset()
            for (k in 0 until 4) {
                val yy = if (k < 2) y0 else y1
                val p = project(sd.first[k], sd.second[k], yy, scale, ox, oy)
                if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            fill.color = shade(base, n.first, 0f, n.second, false, (x0 + x1) / 2, y0, (z0 + z1) / 2)
            canvas.drawPath(path, fill)
        }
        drawFloorQuad(canvas, x0, z0, x1, z1, y1, shade(base, 0f, 1f, 0f, true, (x0 + x1) / 2, y1, (z0 + z1) / 2), scale, ox, oy)
    }

    /** 极淡的大格，只用来暗示材质，不做木纹/砖缝噪声。 */
    private fun drawFloorHints(canvas: Canvas, plan: Plan, scale: Float, ox: Float, oy: Float) {
        edge.strokeWidth = 0.9f
        for (room in plan.rooms) {
            if (room.floor != "tile" && room.floor != "stone") continue
            val r = room.rect
            val x0 = r[0]; val z0 = r[1]; val x1 = r[0] + r[2]; val z1 = r[1] + r[3]
            path.reset()
            val c = listOf(
                project(x0, z0, 0.004f, scale, ox, oy), project(x1, z0, 0.004f, scale, ox, oy),
                project(x1, z1, 0.004f, scale, ox, oy), project(x0, z1, 0.004f, scale, ox, oy)
            )
            path.moveTo(c[0].x, c[0].y)
            for (i in 1 until 4) path.lineTo(c[i].x, c[i].y)
            path.close()
            canvas.save()
            canvas.clipPath(path)
            edge.color = Color.argb((lerp(16f, 26f, t)).toInt(), 60, 70, 88)
            var x = x0 + 1.2f
            while (x < x1) {
                val a = project(x, z0, 0.004f, scale, ox, oy)
                val b2 = project(x, z1, 0.004f, scale, ox, oy)
                canvas.drawLine(a.x, a.y, b2.x, b2.y, edge)
                x += 1.2f
            }
            var z = z0 + 1.2f
            while (z < z1) {
                val a = project(x0, z, 0.004f, scale, ox, oy)
                val b2 = project(x1, z, 0.004f, scale, ox, oy)
                canvas.drawLine(a.x, a.y, b2.x, b2.y, edge)
                z += 1.2f
            }
            canvas.restore()
        }
        edge.strokeWidth = 1.0f
    }

    private fun drawFloorQuad(canvas: Canvas, x0: Float, z0: Float, x1: Float, z1: Float, y: Float,
                              col: Int, scale: Float, ox: Float, oy: Float) {
        val p0 = project(x0, z0, y, scale, ox, oy)
        val p1 = project(x1, z0, y, scale, ox, oy)
        val p2 = project(x1, z1, y, scale, ox, oy)
        val p3 = project(x0, z1, y, scale, ox, oy)
        fill.color = col
        path.reset()
        path.moveTo(p0.x, p0.y); path.lineTo(p1.x, p1.y); path.lineTo(p2.x, p2.y); path.lineTo(p3.x, p3.y)
        path.close()
        canvas.drawPath(path, fill)
    }

    private fun drawFurnitureShadow(canvas: Canvas, f: Furniture, scale: Float, ox: Float, oy: Float, alpha: Int) {
        val w = (if (f.size != null) f.size[0] else f.width).coerceAtLeast(0.2f)
        val d = (if (f.size != null) f.size[1] else defaultDepth(f.type)).coerceAtLeast(0.2f)
        val a = Math.toRadians(f.rot.toDouble()).toFloat()
        val c = cos(a); val s = sin(a)
        fun wp(lx: Float, lz: Float): Pair<Float, Float> =
            (f.pos[0] + lx * c - lz * s + 0.06f) to (f.pos[1] + lx * s + lz * c + 0.09f)
        val corners = listOf(wp(-w / 2, -d / 2), wp(w / 2, -d / 2), wp(w / 2, d / 2), wp(-w / 2, d / 2))
        path.reset()
        corners.forEachIndexed { i, p ->
            val q = project(p.first, p.second, 0.002f, scale, ox, oy)
            if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
        }
        path.close()
        soft.color = Color.argb(alpha.coerceIn(0, 60), 46, 56, 76)
        canvas.drawPath(path, soft)
    }

    // ---------- 墙体 ----------
    private class Box(val x0: Float, val z0: Float, val x1: Float, val z1: Float, val y0: Float, val y1: Float)

    private fun buildWalls(plan: Plan, out: MutableList<Face>) {
        val b = plan.bounds
        val segs = ArrayList<FloatArray>()
        // 外墙：x0,z0,x1,z1, interior, nx, nz
        segs.add(floatArrayOf(b[0], b[1], b[2], b[1], 0f, 0f, -1f))
        segs.add(floatArrayOf(b[2], b[1], b[2], b[3], 0f, 1f, 0f))
        segs.add(floatArrayOf(b[2], b[3], b[0], b[3], 0f, 0f, 1f))
        segs.add(floatArrayOf(b[0], b[3], b[0], b[1], 0f, -1f, 0f))

        val eps = 0.06f
        for (i in plan.rooms.indices) for (j in i + 1 until plan.rooms.size) {
            val a = plan.rooms[i].rect; val c = plan.rooms[j].rect
            if (abs(a[0] + a[2] - c[0]) < eps || abs(c[0] + c[2] - a[0]) < eps) {
                val x = if (abs(a[0] + a[2] - c[0]) < eps) a[0] + a[2] else c[0] + c[2]
                val s = max(a[1], c[1]); val e = min(a[1] + a[3], c[1] + c[3])
                if (e - s > 0.15f) segs.add(floatArrayOf(x, s, x, e, 1f, 1f, 0f))
            }
            if (abs(a[1] + a[3] - c[1]) < eps || abs(c[1] + c[3] - a[1]) < eps) {
                val z = if (abs(a[1] + a[3] - c[1]) < eps) a[1] + a[3] else c[1] + c[3]
                val s = max(a[0], c[0]); val e = min(a[0] + a[2], c[0] + c[2])
                if (e - s > 0.15f) segs.add(floatArrayOf(s, z, e, z, 1f, 0f, 1f))
            }
        }

        for (s in segs) {
            val x0 = s[0]; val z0 = s[1]; val x1 = s[2]; val z1 = s[3]
            val interior = s[4] > 0.5f
            val nx = s[5]; val nz = s[6]
            val horiz = abs(z1 - z0) < 0.01f
            val midX = (x0 + x1) / 2f; val midZ = (z0 + z1) / 2f
            // 朝向相机的墙（rz 较小的一侧）做矮槛，形成剖面
            val facing = (midX - (b[0] + b[2]) / 2f) * sa + (midZ - (b[1] + b[3]) / 2f) * ca
            val near = !interior && facing < -0.01f
            val h = if (near) nearH else if (interior) intH else extH
            val th = if (interior) 0.075f else 0.11f
            val body = if (interior) Color.rgb(238, 235, 228) else Color.rgb(247, 245, 239)
            val cap = if (interior) Color.rgb(250, 248, 243) else Color.rgb(255, 253, 248)

            val ops = plan.openings.filter { o ->
                if (horiz) abs(o.from[1] - z0) < 0.08f && abs(o.to[1] - z0) < 0.08f
                else abs(o.from[0] - x0) < 0.08f && abs(o.to[0] - x0) < 0.08f
            }.sortedBy { if (horiz) min(it.from[0], it.to[0]) else min(it.from[1], it.to[1]) }

            val start = if (horiz) min(x0, x1) else min(z0, z1)
            val end = if (horiz) max(x0, x1) else max(z0, z1)
            var cursor = start

            fun box(a: Float, bb: Float, ya: Float = 0f, yb: Float = h, color: Int = body) {
                if (bb - a < 0.02f || yb - ya < 0.015f) return
                val bx = if (horiz) Box(a, z0 - th / 2, bb, z0 + th / 2, ya, yb)
                         else Box(x0 - th / 2, a, x0 + th / 2, bb, ya, yb)
                emitBox(out, bx, color, cap)
            }

            for (o in ops) {
                val s0 = if (horiz) min(o.from[0], o.to[0]) else min(o.from[1], o.to[1])
                val s1 = if (horiz) max(o.from[0], o.to[0]) else max(o.from[1], o.to[1])
                val a = max(cursor, s0); val bb = min(end, s1)
                if (a > cursor) box(cursor, a)
                if (bb > a) {
                    if (o.type == "window" && !near) {
                        // 窗：留出整段玻璃带，不做窗棂，保持干净
                        val sill = min(o.sill, h * 0.42f)
                        if (sill > 0.03f) box(a, bb, 0f, sill)
                        emitGlass(out, if (horiz)
                            Box(a, z0 - 0.025f, bb, z0 + 0.025f, sill, h)
                        else
                            Box(x0 - 0.025f, a, x0 + 0.025f, bb, sill, h))
                    }
                }
                cursor = max(cursor, bb)
            }
            if (end > cursor) box(cursor, end)
        }
    }

    private fun emitBox(out: MutableList<Face>, b: Box, body: Int, cap: Int) {
        // 顶面
        out.add(Face(floatArrayOf(b.x0, b.x1, b.x1, b.x0),
            floatArrayOf(b.y1, b.y1, b.y1, b.y1),
            floatArrayOf(b.z0, b.z0, b.z1, b.z1), cap, true))
        // 只发出朝向相机的侧面：-x 与 -z（随方位角变化）
        val faces = listOf(
            Triple(0, -1, Face(floatArrayOf(b.x0, b.x1, b.x1, b.x0),
                floatArrayOf(b.y0, b.y0, b.y1, b.y1),
                floatArrayOf(b.z0, b.z0, b.z0, b.z0), body, false)),
            Triple(0, 1, Face(floatArrayOf(b.x1, b.x0, b.x0, b.x1),
                floatArrayOf(b.y0, b.y0, b.y1, b.y1),
                floatArrayOf(b.z1, b.z1, b.z1, b.z1), body, false)),
            Triple(-1, 0, Face(floatArrayOf(b.x0, b.x0, b.x0, b.x0),
                floatArrayOf(b.y0, b.y0, b.y1, b.y1),
                floatArrayOf(b.z1, b.z0, b.z0, b.z1), body, false)),
            Triple(1, 0, Face(floatArrayOf(b.x1, b.x1, b.x1, b.x1),
                floatArrayOf(b.y0, b.y0, b.y1, b.y1),
                floatArrayOf(b.z0, b.z1, b.z1, b.z0), body, false))
        )
        for ((nx, nz, f) in faces) {
            if (nx * sa + nz * ca < -0.005f) out.add(f)
        }
    }

    private fun emitGlass(out: MutableList<Face>, b: Box) {
        val glass = lerpColor(Color.rgb(152, 174, 196), Color.rgb(58, 84, 120), t)
        // 玻璃只发出朝向相机的一侧，避免半透明面互相叠加变脏。
        if (-1f * sa + 0f * ca < -0.005f) {
            out.add(Face(floatArrayOf(b.x0, b.x0, b.x0, b.x0),
                floatArrayOf(b.y0, b.y0, b.y1, b.y1),
                floatArrayOf(b.z1, b.z0, b.z0, b.z1), glass, false))
        }
        if (1f * sa + 0f * ca < -0.005f) {
            out.add(Face(floatArrayOf(b.x1, b.x1, b.x1, b.x1),
                floatArrayOf(b.y0, b.y0, b.y1, b.y1),
                floatArrayOf(b.z0, b.z1, b.z1, b.z0), glass, false))
        }
        if (0f * sa + -1f * ca < -0.005f) {
            out.add(Face(floatArrayOf(b.x0, b.x1, b.x1, b.x0),
                floatArrayOf(b.y0, b.y0, b.y1, b.y1),
                floatArrayOf(b.z0, b.z0, b.z0, b.z0), glass, false))
        }
        if (0f * sa + 1f * ca < -0.005f) {
            out.add(Face(floatArrayOf(b.x1, b.x0, b.x0, b.x1),
                floatArrayOf(b.y0, b.y0, b.y1, b.y1),
                floatArrayOf(b.z1, b.z1, b.z1, b.z1), glass, false))
        }
    }

    // ---------- 家具 ----------
    private fun buildFurniture(plan: Plan, out: MutableList<Face>) {
        for (f in plan.furniture) {
            val w = if (f.size != null) f.size[0] else f.width
            val d = if (f.size != null) f.size[1] else defaultDepth(f.type)
            val a = Math.toRadians(f.rot.toDouble()).toFloat()
            val c = cos(a); val s = sin(a)
            fun wpt(lx: Float, lz: Float): Pair<Float, Float> =
                (f.pos[0] + lx * c - lz * s) to (f.pos[1] + lx * s + lz * c)

            fun addBox(lx0: Float, lz0: Float, lx1: Float, lz1: Float, y0: Float, y1: Float,
                       base: Int, cap: Int = base) {
                if (lx1 - lx0 < 0.01f || lz1 - lz0 < 0.01f || y1 - y0 < 0.008f) return
                // 顶面
                val t0 = wpt(lx0, lz0); val t1 = wpt(lx1, lz0)
                val t2 = wpt(lx1, lz1); val t3 = wpt(lx0, lz1)
                out.add(Face(floatArrayOf(t0.first, t1.first, t2.first, t3.first),
                    floatArrayOf(y1, y1, y1, y1),
                    floatArrayOf(t0.second, t1.second, t2.second, t3.second), cap, true))
                // 侧面：只保留朝向相机的一侧（局部 -x / -z 方向经过旋转）
                val sides = listOf(
                    Triple(-1f, 0f, arrayOf(wpt(lx0, lz1), wpt(lx0, lz0))),
                    Triple(1f, 0f, arrayOf(wpt(lx1, lz0), wpt(lx1, lz1))),
                    Triple(0f, -1f, arrayOf(wpt(lx0, lz0), wpt(lx1, lz0))),
                    Triple(0f, 1f, arrayOf(wpt(lx1, lz1), wpt(lx0, lz1)))
                )
                for ((lnx, lnz, pts) in sides) {
                    val wnx = lnx * c - lnz * s
                    val wnz = lnx * s + lnz * c
                    if (wnx * sa + wnz * ca > -0.005f) continue
                    out.add(Face(floatArrayOf(pts[0].first, pts[1].first, pts[1].first, pts[0].first),
                        floatArrayOf(y0, y0, y1, y1),
                        floatArrayOf(pts[0].second, pts[1].second, pts[1].second, pts[0].second), base, false))
                }
            }

            val wood = Color.rgb(203, 174, 138)
            val woodDark = Color.rgb(176, 146, 112)
            val linen = Color.rgb(243, 241, 234)
            val white = Color.rgb(247, 246, 242)
            val fabric = f.color?.let { parseColor(it, Color.rgb(198, 204, 214)) } ?: Color.rgb(198, 204, 214)
            val metal = Color.rgb(122, 132, 146)
            val green = Color.rgb(126, 160, 122)

            when (f.type) {
                "bed" -> {
                    addBox(-w / 2, -d / 2, w / 2, d / 2, 0.04f, 0.30f, woodDark, wood)
                    addBox(-w / 2 + 0.03f, -d / 2 + 0.03f, w / 2 - 0.03f, d / 2 - 0.03f, 0.30f, 0.44f, linen)
                    addBox(-w / 2 + 0.06f, -d / 2 + 0.10f, w / 2 - 0.06f, -d / 2 + 0.52f, 0.44f, 0.53f, white)
                    addBox(-w / 2, -d / 2 - 0.05f, w / 2, -d / 2 + 0.03f, 0.0f, 0.78f, woodDark, wood)
                }
                "sofa" -> {
                    addBox(-w / 2, -d / 2, w / 2, d / 2, 0.06f, 0.30f, fabric)
                    addBox(-w / 2 + 0.10f, -d / 2 + 0.10f, w / 2 - 0.10f, d / 2 - 0.06f, 0.30f, 0.40f, lighten(fabric, 1.05f))
                    addBox(-w / 2, -d / 2, w / 2, -d / 2 + 0.16f, 0.30f, 0.72f, fabric)
                    addBox(-w / 2, -d / 2, -w / 2 + 0.14f, d / 2, 0.30f, 0.56f, fabric)
                    addBox(w / 2 - 0.14f, -d / 2, w / 2, d / 2, 0.30f, 0.56f, fabric)
                }
                "diningSet" -> {
                    addBox(-w / 2, -d / 2, w / 2, d / 2, 0.72f, 0.78f, wood, lighten(wood, 1.08f))
                    addBox(-w * 0.18f, -d * 0.18f, w * 0.18f, d * 0.18f, 0f, 0.72f, woodDark)
                    addBox(-w * 0.34f, -d / 2 - 0.30f, w * 0.34f, -d / 2 - 0.02f, 0f, 0.42f, fabric)
                    addBox(-w * 0.34f, d / 2 + 0.02f, w * 0.34f, d / 2 + 0.30f, 0f, 0.42f, fabric)
                }
                "coffeeTable" -> {
                    addBox(-w / 2, -d / 2, w / 2, d / 2, 0.34f, 0.40f, wood, lighten(wood, 1.06f))
                    addBox(-w * 0.30f, -d * 0.30f, w * 0.30f, d * 0.30f, 0f, 0.34f, woodDark)
                }
                "tv" -> {
                    addBox(-w / 2, -0.20f, w / 2, 0.20f, 0f, 0.30f, woodDark, wood)
                    addBox(-w * 0.40f, -0.10f, w * 0.40f, -0.04f, 0.52f, 1.10f, Color.rgb(46, 52, 62))
                }
                "kitchen" -> {
                    addBox(-w / 2, -d / 2, w / 2, d / 2, 0f, 0.82f, white)
                    addBox(-w / 2 - 0.01f, -d / 2 - 0.01f, w / 2 + 0.01f, d / 2 + 0.01f, 0.82f, 0.88f, metal)
                }
                "wardrobe" -> addBox(-w / 2, -d / 2, w / 2, d / 2, 0f, 1.92f, lighten(white, 0.98f))
                "bookshelf" -> addBox(-w / 2, -d / 2, w / 2, d / 2, 0f, 1.70f, wood, lighten(wood, 1.06f))
                "nightstand" -> addBox(-0.24f, -0.20f, 0.24f, 0.20f, 0.06f, 0.48f, wood, lighten(wood, 1.06f))
                "desk" -> {
                    addBox(-w / 2, -d / 2, w / 2, d / 2, 0.70f, 0.75f, wood, lighten(wood, 1.06f))
                    addBox(-w / 2 + 0.05f, -d / 2 + 0.05f, -w / 2 + 0.11f, d / 2 - 0.05f, 0f, 0.70f, woodDark)
                    addBox(w / 2 - 0.11f, -d / 2 + 0.05f, w / 2 - 0.05f, d / 2 - 0.05f, 0f, 0.70f, woodDark)
                }
                "plant" -> {
                    addBox(-0.17f, -0.17f, 0.17f, 0.17f, 0f, 0.30f, Color.rgb(212, 205, 193))
                    addBox(-0.25f, -0.25f, 0.25f, 0.25f, 0.30f, 1.00f, green, lighten(green, 1.08f))
                }
                "rug" -> {
                    val col = f.color?.let { parseColor(it, Color.rgb(214, 208, 196)) } ?: Color.rgb(214, 208, 196)
                    val base = lerpColor(lighten(col, 1.0f), col, t)
                    addBox(-w / 2, -d / 2, w / 2, d / 2, 0f, 0.015f, base, lighten(base, 1.03f))
                }
                "island" -> {
                    addBox(-w / 2 + 0.05f, -d / 2 + 0.05f, w / 2 - 0.05f, d / 2 - 0.05f, 0f, 0.86f, Color.rgb(150, 156, 166))
                    addBox(-w / 2, -d / 2, w / 2, d / 2, 0.86f, 0.92f, white)
                }
                "armchair" -> {
                    addBox(-0.44f, -0.40f, 0.44f, 0.40f, 0.06f, 0.36f, fabric)
                    addBox(-0.44f, -0.40f, 0.44f, -0.24f, 0.36f, 0.80f, fabric)
                }
                "sideboard", "dresser" -> addBox(-w / 2, -d / 2, w / 2, d / 2, 0.06f, 0.72f, wood, lighten(wood, 1.06f))
                "fridge" -> addBox(-0.36f, -0.34f, 0.36f, 0.34f, 0f, 1.86f, Color.rgb(214, 218, 224))
                "vanity" -> {
                    addBox(-w / 2, -d / 2, w / 2, d / 2, 0.18f, 0.80f, wood)
                    addBox(-w / 2 - 0.01f, -d / 2 - 0.01f, w / 2 + 0.01f, d / 2 + 0.01f, 0.80f, 0.84f, white)
                }
                "bathtub" -> addBox(-0.82f, -0.38f, 0.82f, 0.38f, 0f, 0.52f, white)
                "toilet" -> {
                    addBox(-0.19f, 0.00f, 0.19f, 0.32f, 0f, 0.38f, white)
                    addBox(-0.19f, -0.28f, 0.19f, -0.02f, 0f, 0.70f, white)
                }
                else -> addBox(-w / 2, -d / 2, w / 2, d / 2, 0f, 0.48f, wood)
            }
        }
    }

    private fun defaultDepth(type: String): Float = when (type) {
        "bed" -> 2.05f
        "sofa" -> 0.92f
        "diningSet" -> 0.95f
        "coffeeTable" -> 0.60f
        "kitchen" -> 0.66f
        "wardrobe" -> 0.60f
        "tv" -> 0.40f
        "nightstand" -> 0.45f
        "rug" -> 1.60f
        "island" -> 1.10f
        "sideboard" -> 0.42f
        "dresser" -> 0.45f
        "vanity" -> 0.50f
        "bathtub" -> 0.78f
        "toilet" -> 0.66f
        "fridge" -> 0.68f
        else -> 0.60f
    }

    private fun parseColor(hex: String, fallback: Int): Int =
        try { Color.parseColor(hex) } catch (_: Exception) { fallback }

    // ---------- 工具 ----------
    private fun lerp(a: Float, b: Float, x: Float) = a + (b - a) * x

    private fun lighten(c: Int, amount: Float): Int = Color.rgb(
        (Color.red(c) * amount).toInt().coerceIn(0, 255),
        (Color.green(c) * amount).toInt().coerceIn(0, 255),
        (Color.blue(c) * amount).toInt().coerceIn(0, 255)
    )

    private fun lerpColor(c1: Int, c2: Int, x: Float): Int = Color.argb(
        (Color.alpha(c1) + (Color.alpha(c2) - Color.alpha(c1)) * x).toInt(),
        (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * x).toInt(),
        (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * x).toInt(),
        (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * x).toInt()
    )
}
