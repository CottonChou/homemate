package com.caidongzhou.homemate

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.*

// 3D 户型渲染：等距透视 + 有厚度的墙体 + 可见家具 + 昼夜光照。
// 风格参考「建筑模型」：薄墙、可见墙面厚度、深色玻璃、暖木地板、家具投影。
class Floor3DRenderer {

    var azimuth = 34f
    var elevation = 47f
    var night = false
    var t = 0f                 // 0 = 白天 1 = 夜晚
    var topInset = 0f
    var bottomInset = 0f

    private data class P(val x: Float, val y: Float)
    private class Face(
        val xs: FloatArray, val ys: FloatArray, val zs: FloatArray,   // 4 个角
        val base: Int, val kind: Int                                  // 0 墙 2 家具 3 玻璃 4 家具顶面 10 墙顶
    )

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.1f }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private val exteriorWallThickness = 0.18f
    private val interiorWallThickness = 0.10f
    private val nearWallHeight = 0.18f

    // ---------- 投影 ----------
    private fun rot(wx: Float, wz: Float): Pair<Float, Float> {
        val az = Math.toRadians(azimuth.toDouble())
        val ca = cos(az).toFloat(); val sa = sin(az).toFloat()
        return (wx * ca - wz * sa) to (wx * sa + wz * ca)
    }

    private fun depthOf(wx: Float, wz: Float, wy: Float): Float {
        val (_, rz) = rot(wx, wz)
        val el = Math.toRadians(elevation.toDouble())
        return wy * sin(el).toFloat() + rz * cos(el).toFloat()
    }

    private fun project(wx: Float, wz: Float, wy: Float, scale: Float, ox: Float, oy: Float): P {
        val (rx, rz) = rot(wx, wz)
        val el = Math.toRadians(elevation.toDouble())
        val ce = cos(el).toFloat(); val se = sin(el).toFloat()
        val ry = wy * ce - rz * se
        val depth = wy * se + rz * ce
        val persp = 1f / (1f + depth * 0.035f)
        return P(ox + rx * scale * persp, oy + ry * scale * persp * 0.92f)
    }

    // ---------- 光照 ----------
    private fun sunlight(): FloatArray {
        val a = Math.toRadians(azimuth + 135.0)
        return floatArrayOf(cos(a).toFloat() * 0.75f, 0.55f, sin(a).toFloat() * 0.75f)
    }

    private fun shade(base: Int, nx: Float, ny: Float, nz: Float, cx: Float, cy: Float, cz: Float, lights: List<LightDevice>): Int {
        val dayAmt = 1f - t
        val sun = sunlight()
        val ndl = max(0f, nx * sun[0] + ny * sun[1] + nz * sun[2])
        // 环境光：白天来自天空（偏冷），夜晚很低（偏蓝）
        var ambR = lerp(148f, 86f, t) / 255f
        var ambG = lerp(157f, 95f, t) / 255f
        var ambB = lerp(176f, 130f, t) / 255f
        var lamR = 0f; var lamG = 0f; var lamB = 0f
        if (dayAmt > 0.02f) {
            val s = ndl * 0.9f * dayAmt
            lamR += s * 1.0f; lamG += s * 0.97f; lamB += s * 0.9f
        }
        if (t > 0.02f) {
            for (l in lights) {
                if (!l.on) continue
                val ly = if (l.type == "floor") 0.5f else 2.35f
                val dist = sqrt((cx - l.pos[0]).pow(2) + (cy - ly).pow(2) + (cz - l.pos[1]).pow(2))
                val fall = max(0f, 1f - dist / 4.4f)
                val i = fall * fall * l.brightness * t * 2.2f
                if (i <= 0.001f) continue
                val facing = max(0.25f, ny * 0.65f + 0.35f)
                lamR += i * 1.0f * facing
                lamG += i * 0.74f * facing
                lamB += i * 0.48f * facing
            }
        }
        val r = (Color.red(base) * (ambR + lamR)).coerceIn(0f, 255f)
        val g = (Color.green(base) * (ambG + lamG)).coerceIn(0f, 255f)
        val b = (Color.blue(base) * (ambB + lamB)).coerceIn(0f, 255f)
        return Color.rgb(r.toInt(), g.toInt(), b.toInt())
    }

    // ---------- 主入口 ----------
    fun draw(canvas: Canvas, plan: Plan, viewW: Float, viewH: Float,
             onTapTest: ((MutableList<Triple<Float, Float, LightDevice>>) -> Unit)? = null) {
        val b = plan.bounds
        val w0 = b[0]; val z0 = b[1]; val w1 = b[2]; val z1 = b[3]
        val cx = (w0 + w1) / 2f; val cz = (z0 + z1) / 2f
        val availH = (viewH - topInset - bottomInset).coerceAtLeast(120f)

        // 按模型投影后的真实包围盒适配，而不是按未旋转的矩形估算。
        // 这样白天/夜晚、横屏和不同户型下都能稳定占满画布的可视区。
        val padX = (w1 - w0) * 0.08f + 0.24f
        val padZ = (z1 - z0) * 0.08f + 0.24f
        var minSx = Float.MAX_VALUE; var maxSx = -Float.MAX_VALUE
        var minSy = Float.MAX_VALUE; var maxSy = -Float.MAX_VALUE
        for (px in floatArrayOf(w0 - padX, w1 + padX)) {
            for (pz in floatArrayOf(z0 - padZ, z1 + padZ)) {
                for (py in floatArrayOf(-0.18f, plan.wallHeight)) {
                    val p = project(px, pz, py, 1f, 0f, 0f)
                    minSx = min(minSx, p.x); maxSx = max(maxSx, p.x)
                    minSy = min(minSy, p.y); maxSy = max(maxSy, p.y)
                }
            }
        }
        val scale = min((viewW - 30f) / (maxSx - minSx), (availH - 16f) / (maxSy - minSy))
        val ox = viewW / 2f
        val oy = topInset + availH / 2f - ((minSy + maxSy) / 2f) * scale

        // 背景：白天浅灰蓝 / 夜晚深蓝黑
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bgPaint.shader = LinearGradient(0f, topInset, 0f, viewH,
            lerpColor(Color.rgb(249, 250, 253), Color.rgb(10, 14, 26), t),
            lerpColor(Color.rgb(232, 237, 245), Color.rgb(5, 8, 15), t), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, viewW, viewH, bgPaint)

        val lights = plan.lights
        val vis = plan.lights.filter { it.on }

        // 1) 楼板 + 地板
        drawPlinth(canvas, plan, scale, ox, oy)
        for (room in plan.rooms) {
            val r = room.rect
            drawQuad(canvas, r[0], r[1], r[0] + r[2], r[1] + r[3], 0f,
                shade(floorBase(room.floor), 0f, 1f, 0f, r[0] + r[2] / 2, 0f, r[1] + r[3] / 2, lights),
                scale, ox, oy)
            drawFloorDetail(canvas, room, scale, ox, oy)
        }

        // 2) 家具在地面的投影（白天）
        if (t < 0.9f) {
            for (f in plan.furniture) {
                if (f.type == "rug") continue
                drawFurnitureShadow(canvas, f, scale, ox, oy, ((1f - t) * 36).toInt())
            }
        }

        // 3) 夜晚的地面光晕
        if (t > 0.02f) {
            for (l in vis) {
                val p = project(l.pos[0], l.pos[1], 0.01f, scale, ox, oy)
                val r = scale * (0.85f + 0.75f * l.brightness)
                glow.shader = RadialGradient(p.x, p.y, r,
                    Color.argb((150 * t).toInt().coerceAtMost(170), 255, 214, 158),
                    Color.argb(0, 255, 190, 120), Shader.TileMode.CLAMP)
                canvas.drawCircle(p.x, p.y, r, glow)
                glow.shader = null
            }
        }

        // 4) 墙面 + 家具（收集后按深度排序）
        val faces = ArrayList<Face>(220)
        buildWalls(plan, faces)
        buildFurniture(plan, faces)
        faces.sortByDescending { f ->
            val mx = f.xs.average().toFloat(); val mz = f.zs.average().toFloat(); val my = f.ys.average().toFloat()
            -depthOf(mx, mz, my)
        }

        for (f in faces) {
            val nx = normalX(f); val ny = normalY(f); val nz = normalZ(f)
            val mx = f.xs.average().toFloat(); val mz = f.zs.average().toFloat(); val my = f.ys.average().toFloat()
            val col = shade(f.base, nx, ny, nz, mx, my, mz, lights)
            fill.color = col
            path.reset()
            for (i in 0 until 4) {
                val p = project(f.xs[i], f.zs[i], f.ys[i], scale, ox, oy)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            canvas.drawPath(path, fill)
            // 墙顶与家具顶面描细边，强化建筑模型的转折和体块感。
            if (f.kind == 4 || f.kind == 10) {
                line.color = if (f.kind == 10) Color.argb(110, 38, 45, 60)
                             else Color.argb(55, 40, 50, 70)
                canvas.drawPath(path, line)
            }
        }

        // 5) 灯具标记
        val hits = ArrayList<Triple<Float, Float, LightDevice>>()
        for (l in plan.lights) {
            val p = project(l.pos[0], l.pos[1], if (l.type == "floor") 0.55f else 2.45f, scale, ox, oy)
            val lit = l.on
            if (lit) {
                glow.shader = RadialGradient(p.x, p.y, scale * 0.16f,
                    Color.argb((230 * max(t, 0.35f)).toInt(), 255, 224, 170),
                    Color.argb(0, 255, 200, 140), Shader.TileMode.CLAMP)
                canvas.drawCircle(p.x, p.y, scale * 0.16f, glow)
                glow.shader = null
                fill.color = Color.rgb(255, 231, 186)
            } else {
                fill.color = lerpColor(Color.rgb(70, 78, 96), Color.rgb(46, 54, 74), t)
            }
            fill.alpha = if (lit) 255 else 150
            canvas.drawCircle(p.x, p.y, scale * 0.045f, fill)
            fill.alpha = 255
            hits.add(Triple(p.x, p.y, l))
        }
        onTapTest?.invoke(hits)
    }

    // ---------- 材质 ----------
    private fun floorBase(kind: String): Int = when (kind) {
        "oak" -> Color.rgb(206, 172, 128)
        "walnut" -> Color.rgb(158, 118, 86)
        "tile" -> Color.rgb(214, 216, 220)
        "stone" -> Color.rgb(176, 174, 168)
        else -> Color.rgb(196, 170, 136)
    }

    private fun drawPlinth(canvas: Canvas, plan: Plan, scale: Float, ox: Float, oy: Float) {
        val b = plan.bounds
        val margin = 0.30f
        val box = WallBox(b[0] - margin, b[1] - margin, b[2] + margin, b[3] + margin, -0.18f, -0.015f)
        val faces = ArrayList<Face>(5)
        val dayColor = Color.rgb(190, 199, 211)
        val nightColor = Color.rgb(24, 31, 48)
        emitBox(faces, box, lerpColor(dayColor, nightColor, t), false)
        for (f in faces) {
            val n = normal(f)
            val mx = f.xs.average().toFloat(); val my = f.ys.average().toFloat(); val mz = f.zs.average().toFloat()
            fill.color = shade(f.base, n.first, n.second, n.third, mx, my, mz, emptyList())
            path.reset()
            for (i in 0 until 4) {
                val p = project(f.xs[i], f.zs[i], f.ys[i], scale, ox, oy)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            canvas.drawPath(path, fill)
        }
    }

    private fun drawFloorDetail(canvas: Canvas, room: Room, scale: Float, ox: Float, oy: Float) {
        val r = room.rect
        val x0 = r[0]; val z0 = r[1]; val x1 = r[0] + r[2]; val z1 = r[1] + r[3]
        path.reset()
        val corners = listOf(project(x0, z0, 0.002f, scale, ox, oy), project(x1, z0, 0.002f, scale, ox, oy),
            project(x1, z1, 0.002f, scale, ox, oy), project(x0, z1, 0.002f, scale, ox, oy))
        path.moveTo(corners[0].x, corners[0].y)
        for (i in 1 until 4) path.lineTo(corners[i].x, corners[i].y)
        path.close()

        canvas.save()
        canvas.clipPath(path)
        line.strokeWidth = if (room.floor == "tile" || room.floor == "stone") 0.75f else 0.8f
        line.color = if (room.floor == "tile" || room.floor == "stone")
            Color.argb(if (t > 0.5f) 38 else 48, 75, 83, 95)
        else Color.argb(if (t > 0.5f) 38 else 42, 82, 55, 34)

        if (room.floor == "tile" || room.floor == "stone") {
            val step = if (room.floor == "tile") 0.42f else 0.52f
            var x = x0 + step
            while (x < x1) {
                val a = project(x, z0, 0.003f, scale, ox, oy)
                val b = project(x, z1, 0.003f, scale, ox, oy)
                canvas.drawLine(a.x, a.y, b.x, b.y, line)
                x += step
            }
            var z = z0 + step
            while (z < z1) {
                val a = project(x0, z, 0.003f, scale, ox, oy)
                val b = project(x1, z, 0.003f, scale, ox, oy)
                canvas.drawLine(a.x, a.y, b.x, b.y, line)
                z += step
            }
        } else {
            val board = 0.21f
            var z = z0 + board
            var row = 0
            while (z < z1) {
                val a = project(x0, z, 0.003f, scale, ox, oy)
                val b = project(x1, z, 0.003f, scale, ox, oy)
                canvas.drawLine(a.x, a.y, b.x, b.y, line)
                val offset = if (row % 2 == 0) 0.18f else 0.61f
                var x = x0 + offset
                while (x < x1) {
                    val c = project(x, z, 0.004f, scale, ox, oy)
                    val d = project(x, z - board, 0.004f, scale, ox, oy)
                    canvas.drawLine(c.x, c.y, d.x, d.y, line)
                    x += 0.83f
                }
                z += board
                row++
            }
        }
        canvas.restore()
    }

    private fun drawFurnitureShadow(canvas: Canvas, f: Furniture, scale: Float, ox: Float, oy: Float, alpha: Int) {
        val w = (if (f.size != null) f.size[0] else f.width).coerceAtLeast(0.2f)
        val d = (if (f.size != null) f.size[1] else defaultDepth(f.type)).coerceAtLeast(0.2f)
        val a = Math.toRadians(f.rot.toDouble()).toFloat()
        val ca = cos(a); val sa = sin(a)
        fun wp(lx: Float, lz: Float): Pair<Float, Float> {
            val oxw = lx * ca - lz * sa + f.pos[0] + 0.24f
            val ozw = lx * sa + lz * ca + f.pos[1] + 0.32f
            return oxw to ozw
        }
        fun polygon(expand: Float, fillAlpha: Int) {
            val p0 = wp(-w/2-expand, -d/2-expand)
            val p1 = wp(w/2+expand, -d/2-expand)
            val p2 = wp(w/2+expand, d/2+expand)
            val p3 = wp(-w/2-expand, d/2+expand)
            val q0 = project(p0.first, p0.second, 0.003f, scale, ox, oy)
            val q1 = project(p1.first, p1.second, 0.003f, scale, ox, oy)
            val q2 = project(p2.first, p2.second, 0.003f, scale, ox, oy)
            val q3 = project(p3.first, p3.second, 0.003f, scale, ox, oy)
            shadow.color = Color.argb(fillAlpha, 43, 51, 68)
            path.reset()
            path.moveTo(q0.x, q0.y); path.lineTo(q1.x, q1.y)
            path.lineTo(q2.x, q2.y); path.lineTo(q3.x, q3.y); path.close()
            canvas.drawPath(path, shadow)
        }
        polygon(0.10f, (alpha * 0.45f).toInt().coerceAtLeast(6))
        polygon(0.01f, alpha.coerceAtLeast(12))
    }

    // ---------- 墙体 ----------
    private class WallBox(val x0: Float, val z0: Float, val x1: Float, val z1: Float, val y0: Float, val y1: Float)

    private fun buildWalls(plan: Plan, out: MutableList<Face>) {
        val b = plan.bounds
        val tH = plan.wallHeight
        val inner = max(plan.interiorWallHeight, 1.1f).coerceAtMost(tH)
        val az = Math.toRadians(azimuth.toDouble())
        val viewX = sin(az).toFloat(); val viewZ = cos(az).toFloat()

        val segs = ArrayList<FloatArray>() // x0,z0,x1,z1,interior
        segs.add(floatArrayOf(b[0], b[1], b[2], b[1], 0f))
        segs.add(floatArrayOf(b[2], b[1], b[2], b[3], 0f))
        segs.add(floatArrayOf(b[2], b[3], b[0], b[3], 0f))
        segs.add(floatArrayOf(b[0], b[3], b[0], b[1], 0f))

        for (i in plan.rooms.indices) for (j in i + 1 until plan.rooms.size) {
            val a = plan.rooms[i].rect; val c = plan.rooms[j].rect
            val eps = 0.06f
            if (abs(a[0] + a[2] - c[0]) < eps || abs(c[0] + c[2] - a[0]) < eps) {
                val x = if (abs(a[0] + a[2] - c[0]) < eps) a[0] + a[2] else c[0] + c[2]
                val s = max(a[1], c[1]); val e = min(a[1] + a[3], c[1] + c[3])
                if (e - s > 0.15f) segs.add(floatArrayOf(x, s, x, e, 1f))
            }
            if (abs(a[1] + a[3] - c[1]) < eps || abs(c[1] + c[3] - a[1]) < eps) {
                val z = if (abs(a[1] + a[3] - c[1]) < eps) a[1] + a[3] else c[1] + c[3]
                val s = max(a[0], c[0]); val e = min(a[0] + a[2], c[0] + c[2])
                if (e - s > 0.15f) segs.add(floatArrayOf(s, z, e, z, 1f))
            }
        }

        for (s in segs) {
            val x0 = s[0]; val z0 = s[1]; val x1 = s[2]; val z1 = s[3]
            val interior = s[4] > 0.5f
            val horizontal = abs(z1 - z0) < 0.01f
            val midX = (x0 + x1) / 2f; val midZ = (z0 + z1) / 2f
            val facing = (midX - (b[0] + b[2]) / 2f) * viewX + (midZ - (b[1] + b[3]) / 2f) * viewZ
            val near = !interior && facing > 0.01f
            val wallHeight = if (interior) inner else tH
            val thickness = if (interior) interiorWallThickness else exteriorWallThickness
            val bodyColor = if (interior) Color.rgb(224, 222, 216) else Color.rgb(238, 236, 231)

            // 开洞
            val ops = plan.openings.filter { o ->
                if (horizontal) abs(o.from[1] - z0) < 0.06f && abs(o.to[1] - z0) < 0.06f
                else abs(o.from[0] - x0) < 0.06f && abs(o.to[0] - x0) < 0.06f
            }.sortedBy { if (horizontal) min(it.from[0], it.to[0]) else min(it.from[1], it.to[1]) }

            val start = if (horizontal) min(x0, x1) else min(z0, z1)
            val end = if (horizontal) max(x0, x1) else max(z0, z1)
            var cursor = start

            fun boxFor(a: Float, bnd: Float, ya: Float, yb: Float): WallBox =
                if (horizontal) WallBox(a, z0 - thickness / 2, bnd, z0 + thickness / 2, ya, yb)
                else WallBox(x0 - thickness / 2, a, x0 + thickness / 2, bnd, ya, yb)

            fun pushBox(a: Float, bnd: Float, ya: Float, yb: Float, color: Int = bodyColor) {
                if (bnd - a < 0.02f || yb - ya < 0.02f) return
                emitBox(out, boxFor(a, bnd, ya, yb), color, interior)
            }

            // 近侧外墙只保留一段矮墙，形成参考图里的剖面建筑感；
            // 门洞和开放通道仍然真实断开。
            if (near) {
                for (o in ops) {
                    val s0 = if (horizontal) min(o.from[0], o.to[0]) else min(o.from[1], o.to[1])
                    val s1 = if (horizontal) max(o.from[0], o.to[0]) else max(o.from[1], o.to[1])
                    val a = max(cursor, s0); val bnd = min(end, s1)
                    if (a > cursor) pushBox(cursor, a, 0f, nearWallHeight)
                    cursor = max(cursor, if (o.type == "door" || o.type == "open") bnd else a)
                    if (o.type != "door" && o.type != "open" && bnd > a) {
                        pushBox(a, bnd, 0f, nearWallHeight)
                        cursor = max(cursor, bnd)
                    }
                }
                if (end > cursor) pushBox(cursor, end, 0f, nearWallHeight)
                continue
            }

            for (o in ops) {
                val s0 = if (horizontal) min(o.from[0], o.to[0]) else min(o.from[1], o.to[1])
                val s1 = if (horizontal) max(o.from[0], o.to[0]) else max(o.from[1], o.to[1])
                val a = max(cursor, s0); val bnd = min(end, s1)
                if (a > cursor) pushBox(cursor, a, 0f, wallHeight)
                if (bnd > a) {
                    when (o.type) {
                        "window" -> {
                            if (o.sill > 0.02f) pushBox(a, bnd, 0f, o.sill)
                            if (o.head < wallHeight) pushBox(a, bnd, o.head, wallHeight)
                            val box = if (horizontal) WallBox(a, z0 - 0.03f, bnd, z0 + 0.03f, o.sill, o.head)
                                      else WallBox(x0 - 0.03f, a, x0 + 0.03f, bnd, o.sill, o.head)
                            emitGlass(out, box)
                            // 窗框：四边 + 中间竖梃，白天和夜晚都能读出窗户结构。
                            val frameColor = Color.rgb(56, 64, 78)
                            val fw = 0.055f
                            pushBox(a, a + fw, o.sill, o.head, frameColor)
                            pushBox(bnd - fw, bnd, o.sill, o.head, frameColor)
                            pushBox(a, bnd, o.sill, o.sill + fw, frameColor)
                            pushBox(a, bnd, o.head - fw, o.head, frameColor)
                            if (bnd - a > 1.1f) {
                                val mid = (a + bnd) / 2f
                                pushBox(mid - fw / 2, mid + fw / 2, o.sill, o.head, frameColor)
                            }
                        }
                        "door", "open" -> {}
                        else -> {}
                    }
                }
                cursor = max(cursor, bnd)
            }
            if (end > cursor) pushBox(cursor, end, 0f, wallHeight)
        }
    }

    private fun emitBox(out: MutableList<Face>, b: WallBox, color: Int, interior: Boolean) {
        val col = color
        val cap = if (interior) Color.rgb(103, 110, 123) else Color.rgb(92, 99, 113)
        // 顶面使用深色墙 cap，侧面保持浅色，轮廓更像实体建筑模型。
        out.add(Face(floatArrayOf(b.x0, b.x1, b.x1, b.x0), floatArrayOf(b.y1, b.y1, b.y1, b.y1),
            floatArrayOf(b.z0, b.z0, b.z1, b.z1), cap, 10))
        // 四个侧面
        out.add(Face(floatArrayOf(b.x0, b.x1, b.x1, b.x0), floatArrayOf(b.y0, b.y0, b.y1, b.y1),
            floatArrayOf(b.z0, b.z0, b.z0, b.z0), col, 0))
        out.add(Face(floatArrayOf(b.x0, b.x1, b.x1, b.x0), floatArrayOf(b.y0, b.y0, b.y1, b.y1),
            floatArrayOf(b.z1, b.z1, b.z1, b.z1), col, 0))
        out.add(Face(floatArrayOf(b.x0, b.x0, b.x0, b.x0), floatArrayOf(b.y0, b.y0, b.y1, b.y1),
            floatArrayOf(b.z0, b.z1, b.z1, b.z0), col, 0))
        out.add(Face(floatArrayOf(b.x1, b.x1, b.x1, b.x1), floatArrayOf(b.y0, b.y0, b.y1, b.y1),
            floatArrayOf(b.z0, b.z1, b.z1, b.z0), col, 0))
    }

    private fun emitGlass(out: MutableList<Face>, b: WallBox) {
        val dark = lerpColor(Color.argb(255, 58, 72, 92), Color.argb(255, 120, 150, 180), t)
        out.add(Face(floatArrayOf(b.x0, b.x1, b.x1, b.x0), floatArrayOf(b.y0, b.y0, b.y1, b.y1),
            floatArrayOf(b.z0, b.z0, b.z0, b.z0), dark, 3))
        out.add(Face(floatArrayOf(b.x0, b.x1, b.x1, b.x0), floatArrayOf(b.y0, b.y0, b.y1, b.y1),
            floatArrayOf(b.z1, b.z1, b.z1, b.z1), dark, 3))
    }

    // ---------- 家具 ----------
    private fun buildFurniture(plan: Plan, out: MutableList<Face>) {
        for (f in plan.furniture) {
            val w = if (f.size != null) f.size[0] else f.width
            val d = if (f.size != null) f.size[1] else defaultDepth(f.type)
            val rot = Math.toRadians(f.rot.toDouble()).toFloat()
            val c = cos(rot); val s = sin(rot)
            // 局部坐标 → 世界坐标
            fun px(lx: Float, lz: Float): Pair<Float, Float> {
                val x = lx * c - lz * s
                val z = lx * s + lz * c
                return (f.pos[0] + x) to (f.pos[1] + z)
            }
            fun addBox(lx0: Float, lz0: Float, lx1: Float, lz1: Float, y0: Float, y1: Float, color: Int) {
                val p0 = px(lx0, lz0); val p1 = px(lx1, lz0); val p2 = px(lx1, lz1); val p3 = px(lx0, lz1)
                out.add(Face(floatArrayOf(p0.first, p1.first, p2.first, p3.first),
                    floatArrayOf(y1, y1, y1, y1), floatArrayOf(p0.second, p1.second, p2.second, p3.second), color, 4))
                val sides = listOf(
                    arrayOf(floatArrayOf(p0.first, p1.first, p1.first, p0.first), floatArrayOf(y0, y0, y1, y1), floatArrayOf(p0.second, p1.second, p1.second, p0.second)),
                    arrayOf(floatArrayOf(p1.first, p2.first, p2.first, p1.first), floatArrayOf(y0, y0, y1, y1), floatArrayOf(p1.second, p2.second, p2.second, p1.second)),
                    arrayOf(floatArrayOf(p2.first, p3.first, p3.first, p2.first), floatArrayOf(y0, y0, y1, y1), floatArrayOf(p2.second, p3.second, p3.second, p2.second)),
                    arrayOf(floatArrayOf(p3.first, p0.first, p0.first, p3.first), floatArrayOf(y0, y0, y1, y1), floatArrayOf(p3.second, p0.second, p0.second, p3.second))
                )
                for (sd in sides) out.add(Face(sd[0], sd[1], sd[2], color, 2))
            }

            val wood = Color.rgb(178, 140, 104)
            val darkWood = Color.rgb(112, 82, 62)
            val linen = Color.rgb(226, 226, 224)
            val white = Color.rgb(232, 230, 224)
            val metal = Color.rgb(145, 151, 160)
            val accent = f.color?.let { parseColor(it, wood) } ?: wood
            when (f.type) {
                "bed" -> {
                    addBox(-w/2, -d/2, w/2, d/2, 0.04f, 0.28f, darkWood)
                    addBox(-w/2+0.035f, -d/2+0.05f, w/2-0.035f, d/2-0.04f, 0.28f, 0.47f, linen)
                    addBox(-w/2+0.06f, -d/2+0.48f, w/2-0.06f, d/2-0.07f, 0.47f, 0.56f, accent)
                    addBox(-w/2, -d/2-0.02f, w/2, -d/2+0.10f, 0.04f, 0.98f, darkWood)
                    val pw = (w - 0.18f) / 2f
                    addBox(-w/2+0.07f, -d/2+0.13f, -w/2+0.07f+pw, -d/2+0.47f, 0.5f, 0.64f, Color.rgb(242, 241, 236))
                    addBox(w/2-0.07f-pw, -d/2+0.13f, w/2-0.07f, -d/2+0.47f, 0.5f, 0.64f, Color.rgb(242, 241, 236))
                    addBox(-w/2+0.02f, d/2-0.055f, w/2-0.02f, d/2, 0.28f, 0.58f, accent)
                }
                "sofa" -> {
                    val base = Color.rgb(110, 119, 139)
                    val fabric = Color.rgb(138, 147, 168)
                    addBox(-w/2+0.06f, -d/2+0.06f, -w/2+0.13f, -d/2+0.13f, 0f, 0.11f, darkWood)
                    addBox(w/2-0.13f, -d/2+0.06f, w/2-0.06f, -d/2+0.13f, 0f, 0.11f, darkWood)
                    addBox(-w/2+0.06f, d/2-0.13f, -w/2+0.13f, d/2-0.06f, 0f, 0.11f, darkWood)
                    addBox(w/2-0.13f, d/2-0.13f, w/2-0.06f, d/2-0.06f, 0f, 0.11f, darkWood)
                    addBox(-w/2, -d/2, w/2, d/2, 0.1f, 0.34f, base)
                    addBox(-w/2, -d/2+0.02f, w/2, -d/2+0.17f, 0.34f, 0.82f, fabric)
                    addBox(-w/2, -d/2, -w/2+0.19f, d/2, 0.34f, 0.62f, fabric)
                    addBox(w/2-0.19f, -d/2, w/2, d/2, 0.34f, 0.62f, fabric)
                    val n = if (w > 2.0f) 3 else 2
                    val cw = (w - 0.38f) / n
                    for (i in 0 until n) {
                        val left = -w/2 + 0.19f + cw * i
                        addBox(left + 0.018f, -d/2+0.19f, left+cw-0.018f, d/2-0.07f, 0.34f, 0.47f, fabric)
                    }
                    addBox(-w/2+0.34f, -d/2+0.16f, -w/2+0.72f, -d/2+0.29f, 0.47f, 0.82f, Color.rgb(210, 177, 127))
                    addBox(w/2-0.78f, -d/2+0.16f, w/2-0.40f, -d/2+0.29f, 0.47f, 0.82f, Color.rgb(220, 204, 181))
                }
                "diningSet" -> {
                    addBox(-w/2, -d/2, w/2, d/2, 0.70f, 0.75f, wood)
                    addBox(-w/2+0.06f, -d/2+0.06f, w/2-0.06f, d/2-0.06f, 0.66f, 0.69f, darkWood)
                    for (sx in listOf(-1f, 1f)) for (sz in listOf(-1f, 1f)) {
                        val lx = sx * (w/2 - 0.12f); val lz = sz * (d/2 - 0.12f)
                        addBox(lx-0.035f, lz-0.035f, lx+0.035f, lz+0.035f, 0f, 0.68f, darkWood)
                    }
                    val chairColor = Color.rgb(86, 91, 102)
                    for (i in 0 until 2) {
                        val x = if (i == 0) -w*0.22f else w*0.22f
                        addBox(x-0.2f, -d/2-0.43f, x+0.2f, -d/2-0.08f, 0f, 0.42f, chairColor)
                        addBox(x-0.2f, -d/2-0.47f, x+0.2f, -d/2-0.42f, 0.42f, 0.82f, chairColor)
                        addBox(x-0.2f, d/2+0.08f, x+0.2f, d/2+0.43f, 0f, 0.42f, chairColor)
                        addBox(x-0.2f, d/2+0.42f, x+0.2f, d/2+0.47f, 0.42f, 0.82f, chairColor)
                    }
                    addBox(-0.14f, -0.14f, 0.14f, 0.14f, 0.75f, 0.84f, white)
                }
                "coffeeTable" -> {
                    addBox(-w/2, -d/2, w/2, d/2, 0.36f, 0.41f, wood)
                    addBox(-w/2+0.10f, -d/2+0.10f, w/2-0.10f, d/2-0.10f, 0.15f, 0.18f, darkWood)
                    for (sx in listOf(-1f, 1f)) for (sz in listOf(-1f, 1f)) {
                        val lx = sx * (w/2 - 0.10f); val lz = sz * (d/2 - 0.10f)
                        addBox(lx-0.025f, lz-0.025f, lx+0.025f, lz+0.025f, 0f, 0.36f, darkWood)
                    }
                    addBox(w*0.12f, -d*0.16f, w*0.35f, d*0.12f, 0.41f, 0.44f, Color.rgb(187, 77, 58))
                    addBox(-w*0.34f, -d*0.08f, -w*0.15f, d*0.14f, 0.41f, 0.49f, white)
                }
                "tv" -> {
                    addBox(-w/2, -0.20f, w/2, 0.20f, 0f, 0.34f, darkWood)
                    addBox(-w/2+0.04f, -0.205f, w/2-0.04f, -0.198f, 0.07f, 0.29f, Color.rgb(160, 121, 87))
                    val sw = w * 0.82f
                    addBox(-sw/2, -0.16f, sw/2, -0.12f, 0.62f, 1.24f, Color.rgb(28, 32, 39))
                    addBox(-sw/2+0.025f, -0.173f, sw/2-0.025f, -0.168f, 0.65f, 1.21f, Color.rgb(18, 30, 54))
                    addBox(-0.12f, -0.14f, 0.12f, -0.08f, 0.34f, 0.62f, Color.rgb(44, 48, 56))
                }
                "kitchen" -> {
                    addBox(-w/2, -d/2, w/2, d/2, 0f, 0.84f, white)
                    addBox(-w/2-0.015f, -d/2, w/2+0.015f, d/2+0.015f, 0.84f, 0.89f, Color.rgb(58, 61, 67))
                    val seam = Color.rgb(185, 181, 173)
                    for (i in 1 until max(2, (w / 0.58f).roundToInt())) {
                        val x = -w/2 + w * i / max(2, (w / 0.58f).roundToInt())
                        addBox(x-0.004f, -d/2-0.005f, x+0.004f, -d/2+0.006f, 0.05f, 0.8f, seam)
                    }
                    addBox(-w*0.30f, -d*0.22f, -w*0.05f, d*0.20f, 0.89f, 0.905f, Color.rgb(28, 30, 34))
                    addBox(w*0.10f, -d*0.18f, w*0.28f, d*0.18f, 0.89f, 0.905f, metal)
                    addBox(-w/2, -d/2-0.03f, w/2, -d/2+0.30f, 1.47f, 2.12f, white)
                    addBox(-w*0.30f, -d/2-0.035f, w*0.10f, -d/2+0.29f, 1.50f, 1.62f, Color.rgb(63, 68, 76))
                }
                "wardrobe" -> {
                    addBox(-w/2, -d/2, w/2, d/2, 0f, 2.08f, Color.rgb(221, 217, 208))
                    val doors = max(2, (w / 0.52f).roundToInt())
                    for (i in 1 until doors) {
                        val x = -w/2 + w * i / doors
                        addBox(x-0.006f, -d/2-0.006f, x+0.006f, -d/2+0.01f, 0.06f, 2.02f, Color.rgb(174, 170, 162))
                    }
                    addBox(-0.18f, -d/2-0.02f, -0.15f, -d/2+0.02f, 1.05f, 1.25f, metal)
                    addBox(0.15f, -d/2-0.02f, 0.18f, -d/2+0.02f, 1.05f, 1.25f, metal)
                }
                "bookshelf" -> {
                    addBox(-w/2, -d/2, w/2, -d/2+0.035f, 0f, 1.9f, wood)
                    addBox(-w/2, -d/2, -w/2+0.035f, d/2, 0f, 1.9f, wood)
                    addBox(w/2-0.035f, -d/2, w/2, d/2, 0f, 1.9f, wood)
                    val levels = 4
                    for (i in 0..levels) {
                        val y = 0.08f + (1.72f * i / levels)
                        addBox(-w/2, -d/2, w/2, d/2, y, y+0.03f, wood)
                        if (i < levels) for (j in 0 until max(3, (w / 0.13f).roundToInt())) {
                            val x = -w/2 + 0.08f + j * (w - 0.16f) / max(3, (w / 0.13f).roundToInt())
                            val bh = 0.20f + (j % 3) * 0.035f
                            addBox(x, -d/2+0.07f, x+0.055f, d/2-0.07f, y+0.03f, y+0.03f+bh,
                                intArrayOf(Color.rgb(178, 78, 62), Color.rgb(63, 98, 132), Color.rgb(210, 190, 142))[j % 3])
                        }
                    }
                }
                "nightstand" -> {
                    addBox(-0.23f, -0.20f, 0.23f, 0.20f, 0.06f, 0.50f, wood)
                    addBox(-0.19f, -0.205f, 0.19f, -0.198f, 0.14f, 0.17f, darkWood)
                    addBox(-0.19f, -0.205f, 0.19f, -0.198f, 0.31f, 0.34f, darkWood)
                }
                "desk" -> {
                    addBox(-w/2, -d/2, w/2, d/2, 0.72f, 0.77f, wood)
                    addBox(-w/2+0.06f, -d/2+0.06f, -w/2+0.12f, d/2-0.06f, 0f, 0.72f, darkWood)
                    addBox(w/2-0.12f, -d/2+0.06f, w/2-0.06f, d/2-0.06f, 0f, 0.72f, darkWood)
                    addBox(-w*0.28f, -d/2+0.04f, w*0.22f, d/2-0.04f, 0.96f, 1.40f, Color.rgb(30, 34, 41))
                    addBox(-w*0.26f, -d/2+0.01f, w*0.20f, d/2-0.01f, 0.96f, 1.38f, Color.rgb(25, 40, 66))
                    addBox(-0.28f, -0.22f, 0.28f, 0.22f, 0f, 0.44f, Color.rgb(63, 68, 80))
                    addBox(-0.28f, -0.24f, 0.28f, -0.18f, 0.44f, 0.84f, Color.rgb(73, 79, 92))
                }
                "plant" -> {
                    addBox(-0.17f, -0.17f, 0.17f, 0.17f, 0f, 0.30f, Color.rgb(198, 188, 172))
                    addBox(-0.15f, -0.15f, 0.15f, 0.15f, 0.30f, 0.32f, Color.rgb(75, 56, 42))
                    addBox(-0.03f, -0.03f, 0.03f, 0.03f, 0.32f, 0.86f, Color.rgb(91, 73, 51))
                    addBox(-0.22f, -0.18f, 0.20f, 0.20f, 0.62f, 0.94f, Color.rgb(70, 120, 78))
                    addBox(-0.13f, 0.04f, 0.26f, 0.37f, 0.82f, 1.12f, Color.rgb(88, 142, 92))
                    addBox(-0.30f, -0.30f, -0.05f, -0.05f, 0.48f, 0.76f, Color.rgb(57, 103, 69))
                }
                "rug" -> {
                    val col = f.color?.let { parseColor(it, Color.rgb(96, 104, 120)) } ?: Color.rgb(96, 104, 120)
                    addBox(-w/2, -d/2, w/2, d/2, 0f, 0.012f, col)
                    addBox(-w/2+0.07f, -d/2+0.07f, w/2-0.07f, d/2-0.07f, 0.012f, 0.017f, lighten(col, 1.17f))
                }
                "island" -> {
                    addBox(-w/2+0.06f, -d/2+0.05f, w/2-0.06f, d/2-0.05f, 0f, 0.86f, Color.rgb(61, 69, 80))
                    addBox(-w/2, -d/2, w/2, d/2, 0.86f, 0.91f, Color.rgb(231, 228, 220))
                    for (i in 0 until 3) {
                        val x = -w/2 + w * (i + 0.5f) / 3f
                        addBox(x-0.15f, d/2+0.15f, x+0.15f, d/2+0.45f, 0f, 0.60f, Color.rgb(149, 99, 69))
                        addBox(x-0.18f, d/2+0.12f, x+0.18f, d/2+0.48f, 0.60f, 0.66f, Color.rgb(184, 126, 86))
                    }
                }
                "armchair" -> {
                    val fabric = accent
                    addBox(-0.44f, -0.40f, 0.44f, 0.40f, 0.08f, 0.38f, fabric)
                    addBox(-0.44f, -0.40f, 0.44f, -0.25f, 0.38f, 0.82f, fabric)
                    addBox(-0.44f, -0.40f, -0.30f, 0.40f, 0.38f, 0.62f, fabric)
                    addBox(0.30f, -0.40f, 0.44f, 0.40f, 0.38f, 0.62f, fabric)
                }
                "sideboard" -> {
                    addBox(-w/2, -d/2, w/2, d/2, 0.10f, 0.68f, wood)
                    for (i in 1 until 4) {
                        val x = -w/2 + w * i / 4f
                        addBox(x-0.004f, -d/2-0.004f, x+0.004f, -d/2+0.004f, 0.14f, 0.64f, darkWood)
                    }
                    addBox(-0.13f, -d/2-0.05f, 0.13f, -d/2+0.02f, 0.73f, 0.78f, Color.rgb(87, 119, 141))
                }
                "dresser" -> {
                    addBox(-w/2, -d/2, w/2, d/2, 0.06f, 0.78f, Color.rgb(228, 225, 218))
                    for (i in 1 until 3) {
                        val y = 0.06f + 0.72f * i / 3f
                        addBox(-w/2+0.03f, -d/2-0.004f, w/2-0.03f, -d/2+0.004f, y-0.004f, y+0.004f, Color.rgb(180, 176, 168))
                    }
                }
                "fridge" -> {
                    addBox(-0.35f, -0.34f, 0.35f, 0.34f, 0f, 1.92f, Color.rgb(195, 199, 207))
                    addBox(-0.32f, -0.345f, 0.32f, -0.338f, 1.22f, 1.235f, Color.rgb(123, 128, 136))
                    addBox(-0.29f, -0.37f, -0.26f, -0.34f, 0.82f, 1.30f, metal)
                }
                "vanity" -> {
                    addBox(-w/2, -d/2, w/2, d/2, 0.20f, 0.82f, wood)
                    addBox(-w/2-0.02f, -d/2, w/2+0.02f, d/2+0.015f, 0.82f, 0.87f, white)
                    addBox(-w*0.40f, -d/2-0.02f, w*0.40f, -d/2+0.02f, 0.96f, 1.72f, Color.rgb(153, 177, 198))
                }
                "bathtub" -> {
                    addBox(-0.85f, -0.39f, 0.85f, 0.39f, 0f, 0.56f, white)
                    addBox(-0.74f, -0.28f, 0.74f, 0.28f, 0.54f, 0.57f, Color.rgb(143, 197, 218))
                }
                "toilet" -> {
                    addBox(-0.19f, 0.02f, 0.19f, 0.32f, 0f, 0.40f, white)
                    addBox(-0.19f, -0.27f, 0.19f, -0.02f, 0.16f, 0.74f, white)
                }
                else -> addBox(-w/2, -d/2, w/2, d/2, 0f, 0.45f, wood)
            }
        }
    }

    private fun defaultDepth(type: String): Float = when (type) {
        "bed" -> 2.0f
        "sofa" -> 0.9f
        "diningSet" -> 0.9f
        "coffeeTable" -> 0.6f
        "kitchen" -> 0.65f
        "wardrobe" -> 0.6f
        "tv" -> 0.4f
        "nightstand" -> 0.45f
        "rug" -> 1.6f
        "island" -> 1.1f
        "sideboard" -> 0.42f
        "dresser" -> 0.45f
        "vanity" -> 0.5f
        "bathtub" -> 0.78f
        "toilet" -> 0.66f
        "fridge" -> 0.68f
        else -> 0.6f
    }

    private fun parseColor(hex: String, fallback: Int): Int = try { Color.parseColor(hex) } catch (_: Exception) { fallback }

    // ---------- 工具 ----------
    private fun drawQuad(canvas: Canvas, x0: Float, z0: Float, x1: Float, z1: Float, y: Float, col: Int,
                         scale: Float, ox: Float, oy: Float) {
        val p0 = project(x0, z0, y, scale, ox, oy); val p1 = project(x1, z0, y, scale, ox, oy)
        val p2 = project(x1, z1, y, scale, ox, oy); val p3 = project(x0, z1, y, scale, ox, oy)
        fill.color = col
        path.reset()
        path.moveTo(p0.x, p0.y); path.lineTo(p1.x, p1.y); path.lineTo(p2.x, p2.y); path.lineTo(p3.x, p3.y)
        path.close()
        canvas.drawPath(path, fill)
    }

    private fun normalX(f: Face): Float = normal(f).first
    private fun normalY(f: Face): Float = normal(f).second
    private fun normalZ(f: Face): Float = normal(f).third

    private fun normal(f: Face): Triple<Float, Float, Float> {
        val ux = f.xs[1] - f.xs[0]; val uy = f.ys[1] - f.ys[0]; val uz = f.zs[1] - f.zs[0]
        val vx = f.xs[2] - f.xs[0]; val vy = f.ys[2] - f.ys[0]; val vz = f.zs[2] - f.zs[0]
        var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
        val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f)
        nx /= len; ny /= len; nz /= len
        if (ny < 0) { nx = -nx; ny = -ny; nz = -nz }
        return Triple(nx, ny, nz)
    }

    private fun Float.round(d: Int): String = String.format("%.${d}f", this)
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
