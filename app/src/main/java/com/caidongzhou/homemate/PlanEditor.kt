package com.caidongzhou.homemate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import androidx.compose.ui.text.font.FontWeight

// 户型编辑器：顶栏 + 平面画布 + 底部工具。
// 手势：点选房间/灯/空调；拖动移动选中的房间或设备；空白处拖动平移视图。
// 工具：新建房间 → 在画布上拖一个矩形；选中后可改名/删除。
@Composable
fun PlanEditor(ctx: android.content.Context, plan: Plan, onClose: () -> Unit, onSave: (Plan) -> Unit) {
    var work by remember { mutableStateOf(plan) }
    var selected by remember { mutableStateOf<Any?>(null) }   // Room | LightDevice | AcDevice
    var mode by remember { mutableStateOf("select") }          // select | addRoom
    var dragStart by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var pendingRect by remember { mutableStateOf<FloatArray?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var nameText by remember { mutableStateOf("") }
    var nextId by remember { mutableIntStateOf(work.rooms.size + 1) }
    // 视图变换：米 → 像素
    var zoom by remember { mutableFloatStateOf(60f) }
    var pan by remember { mutableStateOf(40f to 80f) }

    fun toWorld(px: Float, py: Float): Pair<Float, Float> =
        Pair((px - pan.first) / zoom, (py - pan.second) / zoom)

    fun hitTest(wx: Float, wy: Float): Any? {
        for (l in work.lights) {
            if (abs(l.pos[0] - wx) < 0.3f && abs(l.pos[1] - wy) < 0.3f) return l
        }
        for (a in work.acs) {
            if (abs(a.pos[0] - wx) < 0.3f && abs(a.pos[1] - wy) < 0.3f) return a
        }
        for (r in work.rooms.reversed()) {
            val (x, z, w, d) = r.rect.toList()
            if (wx >= x && wx <= x + w && wy >= z && wy <= z + d) return r
        }
        return null
    }

    Box(Modifier.fillMaxSize().background(Color(0xF0080D1A))) {
        // 画布
        val textMeasurer = rememberTextMeasurer()
        Canvas(Modifier.fillMaxSize().background(Color(0xFF0E1424)).pointerInput(mode) {
            detectTapGestures(
                onTap = { off ->
                    val (wx, wy) = toWorld(off.x, off.y)
                    selected = hitTest(wx, wy)
                    pendingRect = null
                },
                onDoubleTap = { _ ->
                    // 自动居中：把户型包围盒放到画布中央
                    val b = work.bounds
                    val wPx = (b[2] - b[0]) * zoom
                    val hPx = (b[3] - b[1]) * zoom
                    pan = (size.width - wPx) / 2f - b[0] * zoom to (size.height - hPx) / 2f - b[1] * zoom
                    selected = null
                }
            )
        }.pointerInput(mode) {
            detectDragGestures(
                onDragStart = { off ->
                    dragStart = toWorld(off.x, off.y)
                    if (mode == "select") {
                        val (wx, wy) = dragStart!!
                        selected = hitTest(wx, wy)
                    }
                },
                onDrag = { change, amt ->
                    change.consume()
                    val start = dragStart ?: return@detectDragGestures
                    val cur = toWorld(change.position.x, change.position.y)
                    when {
                        mode == "addRoom" -> {
                            val x0 = minOf(start.first, cur.first); val x1 = maxOf(start.first, cur.first)
                            val y0 = minOf(start.second, cur.second); val y1 = maxOf(start.second, cur.second)
                            pendingRect = floatArrayOf(x0, y0, x1 - x0, y1 - y0)
                        }
                        selected is Room -> {
                            val r = selected as Room
                            val dx = cur.first - start.first; val dy = cur.second - start.second
                            if (abs(dx) > 0.02f || abs(dy) > 0.02f) {
                                val nr = r.rect.copyOf(); nr[0] += dx; nr[1] += dy
                                work = work.copy(rooms = work.rooms.map { if (it.id == r.id) it.copy(rect = nr) else it })
                                dragStart = cur
                            }
                        }
                        selected is LightDevice -> {
                            val l = selected as LightDevice
                            work = work.copy(lights = work.lights.map {
                                if (it.id == l.id) it.copy(pos = floatArrayOf(cur.first, cur.second)) else it
                            })
                            selected = work.lights.first { it.id == l.id }
                        }
                        selected is AcDevice -> {
                            val a = selected as AcDevice
                            work = work.copy(acs = work.acs.map {
                                if (it.id == a.id) it.copy(pos = floatArrayOf(cur.first, cur.second)) else it
                            })
                            selected = work.acs.first { it.id == a.id }
                        }
                        else -> { pan = pan.first + amt.x to pan.second + amt.y }
                    }
                },
                onDragEnd = {
                    pendingRect?.let { rect ->
                        if (rect[2] > 0.6f && rect[3] > 0.6f) {
                            val id = "room$nextId"; nextId++
                            work = work.copy(rooms = work.rooms + Room(id, "房间$nextId", rect, "oak"))
                        }
                        pendingRect = null
                    }
                    dragStart = null
                }
            )
        }) {
            // 网格
            val gridColor = Color(0x332A3A5C)
            val step = zoom
            var gx = pan.first % step
            while (gx < size.width) { drawLine(gridColor, androidx.compose.ui.geometry.Offset(gx, 0f), androidx.compose.ui.geometry.Offset(gx, size.height), 1f); gx += step }
            var gy = pan.second % step
            while (gy < size.height) { drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, gy), androidx.compose.ui.geometry.Offset(size.width, gy), 1f); gy += step }

            fun rectOf(r: Room) = androidx.compose.ui.geometry.Rect(
                pan.first + r.rect[0] * zoom, pan.second + r.rect[1] * zoom,
                pan.first + (r.rect[0] + r.rect[2]) * zoom, pan.second + (r.rect[1] + r.rect[3]) * zoom
            )

            fun textLayout(text: String, color: Color, size: androidx.compose.ui.unit.TextUnit, bold: Boolean = false) =
                textMeasurer.measure(
                    androidx.compose.ui.text.AnnotatedString(text),
                    androidx.compose.ui.text.TextStyle(
                        color = color,
                        fontSize = size,
                        fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium
                    )
                )
            // 房间
            for (r in work.rooms) {
                val sel = selected == r
                drawRoundRect(
                    color = if (sel) Color(0x59263B66) else Color(0x2E1B2740),
                    topLeft = rectOf(r).topLeft, size = rectOf(r).size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f)
                )
                drawRoundRect(
                    color = if (sel) Color(0xFF8FB2FF) else Color(0xFF51628F),
                    topLeft = rectOf(r).topLeft, size = rectOf(r).size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
                    style = Stroke(2.5f)
                )
                // 房间名
                val rect = rectOf(r)
                val label = if (sel) r.name else r.name
                val tl = textLayout(label, if (sel) Color(0xFFEAF1FF) else Color(0xFFD6E0F6), 15.sp, sel)
                drawText(tl, topLeft = androidx.compose.ui.geometry.Offset(
                    rect.center.x - tl.size.width / 2f, rect.center.y - tl.size.height / 2f))
            }
            // 待绘制的新房间
            pendingRect?.let { pr ->
                drawRoundRect(
                    color = Color(0x557FA8FF),
                    topLeft = androidx.compose.ui.geometry.Offset(pan.first + pr[0] * zoom, pan.second + pr[1] * zoom),
                    size = androidx.compose.ui.geometry.Size(pr[2] * zoom, pr[3] * zoom),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
                    style = Stroke(2f)
                )
            }
            for (l in work.lights) {
                val c = androidx.compose.ui.geometry.Offset(pan.first + l.pos[0] * zoom, pan.second + l.pos[1] * zoom)
                val sel = selected == l
                drawCircle(Color(0xE6FFC97A), radius = if (sel) 17f else 12f, center = c)
                drawCircle(Color(0xFF0E1424), radius = 4f, center = c)
                val tl = textLayout("灯", Color(0xFFFFD9A0), 12.sp, sel)
                drawText(tl, topLeft = androidx.compose.ui.geometry.Offset(c.x - tl.size.width / 2f, c.y - tl.size.height - 24f))
            }
            for (a in work.acs) {
                val c = androidx.compose.ui.geometry.Offset(pan.first + a.pos[0] * zoom, pan.second + a.pos[1] * zoom)
                val sel = selected == a
                drawRoundRect(
                    color = if (sel) Color(0xFF8FE3B0) else Color(0xB36FCF97),
                    topLeft = androidx.compose.ui.geometry.Offset(c.x - 13f, c.y - 13f),
                    size = androidx.compose.ui.geometry.Size(26f, 26f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(7f, 7f)
                )
                val ta = textLayout("空调", Color(0xFFBFF0D4), 12.sp, sel)
                drawText(ta, topLeft = androidx.compose.ui.geometry.Offset(c.x - ta.size.width / 2f, c.y - ta.size.height - 26f))
            }
        }

        // 顶栏
        Row(Modifier.fillMaxWidth().background(Color(0xFF0C1220)).padding(top = 40.dp, bottom = 10.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("取消", color = Color(0xFF9FB0D6), fontSize = 16.sp, modifier = Modifier.clickable(onClick = onClose).padding(4.dp))
            Text("编辑户型", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("保存", color = Color(0xFF8FB2FF), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onSave(work) }.padding(4.dp))
        }

        // 底部工具栏
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            // 选中对象信息
            when (val s = selected) {
                is Room -> EditorInfoBar("房间：${s.name}", listOf(
                    "改名" to { renaming = true; nameText = s.name },
                    "删除" to {
                        work = work.copy(rooms = work.rooms.filter { it.id != s.id },
                            lights = work.lights.filter { it.room != s.id }, acs = work.acs.filter { it.room != s.id })
                        selected = null
                    }))
                is LightDevice -> EditorInfoBar("灯：${s.name}", listOf("删除" to {
                    work = work.copy(lights = work.lights.filter { it.id != s.id }); selected = null
                }))
                is AcDevice -> EditorInfoBar("空调：${s.name}", listOf("删除" to {
                    work = work.copy(acs = work.acs.filter { it.id != s.id }); selected = null
                }))
                else -> {}
            }
            Row(Modifier.fillMaxWidth().background(Color(0xFF0C1220)).padding(vertical = 14.dp, horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                EditorTool("➕", "加房间", mode == "addRoom") { mode = if (mode == "addRoom") "select" else "addRoom" }
                EditorTool("💡", "加灯", mode == "addLight") {
                    mode = "select"
                    val cx = work.bounds[0] + (work.bounds[2] - work.bounds[0]) / 2
                    val cz = work.bounds[1] + (work.bounds[3] - work.bounds[1]) / 2
                    val id = "light${work.lights.size + 1}"
                    work = work.copy(lights = work.lights + LightDevice(id, "新灯", work.rooms.firstOrNull()?.id ?: "", "ceiling", floatArrayOf(cx, cz), false, 1f))
                    selected = work.lights.last()
                }
                EditorTool("❄", "加空调", mode == "addAc") {
                    mode = "select"
                    val id = "ac${work.acs.size + 1}"
                    work = work.copy(acs = work.acs + AcDevice(id, "新空调", work.rooms.firstOrNull()?.id ?: "", floatArrayOf(work.bounds[2] - 0.5f, work.bounds[3] - 0.5f), false, 24f))
                    selected = work.acs.last()
                }
                EditorTool("↺", "重置", false) { work = Plan.demo(); selected = null }
            }
        }

        // 改名弹层
        if (renaming) {
            var text by remember(nameText) { mutableStateOf(nameText) }
            Column(Modifier.align(Alignment.Center).background(Panel, RoundedCornerShape(18.dp)).padding(20.dp).width(260.dp)) {
                Text("改名", color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Text("取消", color = Color(0xFF8A96B8), modifier = Modifier.clickable { renaming = false }.padding(8.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("确定", color = Accent, modifier = Modifier.clickable {
                        val s = selected
                        if (s is Room) work = work.copy(rooms = work.rooms.map { if (it.id == s.id) it.copy(name = text) else it })
                        renaming = false
                    }.padding(8.dp))
                }
            }
        }
    }
}

@Composable
fun EditorTool(icon: String, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Box(Modifier.size(52.dp).background(if (active) Color(0xFF33507F) else Color(0xFF1B2540), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 22.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 13.sp, color = if (active) Color(0xFFBFD4FF) else Color(0xFF9FB0D6), fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
    }
}

@Composable
fun EditorInfoBar(title: String, actions: List<Pair<String, () -> Unit>>) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF182338)).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            actions.forEach { (label, act) ->
                Text(label, color = Color(0xFF8FB2FF), fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = act))
            }
        }
    }
}
