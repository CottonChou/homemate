package com.caidongzhou.homemate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

internal val NightBg = Color(0xFF090D18)
internal val Panel = Color(0xFF141B2E)
internal val PanelHi = Color(0xFF1C2540)
internal val Accent = Color(0xFF7FA8FF)
internal val Warm = Color(0xFFFFC97A)

// 简洁线性图标
@Composable
fun LineIcon(kind: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val s = size.width
        val w = 2.dp.toPx()
        val r = CornerRadius(2.5.dp.toPx())
        when (kind) {
            "home" -> {
                drawLine(color, Offset(s * .16f, s * .52f), Offset(s * .5f, s * .18f), w)
                drawLine(color, Offset(s * .5f, s * .18f), Offset(s * .84f, s * .52f), w)
                drawLine(color, Offset(s * .24f, s * .46f), Offset(s * .24f, s * .84f), w)
                drawLine(color, Offset(s * .76f, s * .46f), Offset(s * .76f, s * .84f), w)
                drawLine(color, Offset(s * .24f, s * .84f), Offset(s * .76f, s * .84f), w)
            }
            "grid" -> {
                val g = s * .10f
                val cell = Size(s * .36f, s * .36f)
                drawRoundRect(color, Offset(g, g), cell, r, style = Stroke(w))
                drawRoundRect(color, Offset(s - g - cell.width, g), cell, r, style = Stroke(w))
                drawRoundRect(color, Offset(g, s - g - cell.height), cell, r, style = Stroke(w))
                drawRoundRect(color, Offset(s - g - cell.width, s - g - cell.height), cell, r, style = Stroke(w))
            }
            "calendar" -> {
                drawRoundRect(color, Offset(s * .16f, s * .22f), Size(s * .68f, s * .62f), r, style = Stroke(w))
                drawLine(color, Offset(s * .16f, s * .43f), Offset(s * .84f, s * .43f), w)
                drawLine(color, Offset(s * .34f, s * .12f), Offset(s * .34f, s * .3f), w)
                drawLine(color, Offset(s * .66f, s * .12f), Offset(s * .66f, s * .3f), w)
                drawCircle(color, radius = 1.7f.dp.toPx(), center = Offset(s * .5f, s * .63f))
            }
            "user" -> {
                drawCircle(color, radius = s * .17f, center = Offset(s * .5f, s * .33f), style = Stroke(w))
                drawArc(color, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(s * .2f, s * .55f), size = Size(s * .6f, s * .6f), style = Stroke(w))
            }
            "sun" -> {
                drawCircle(color, radius = s * .18f, center = Offset(s * .5f, s * .5f), style = Stroke(w))
                for (i in 0..7) {
                    val a = Math.toRadians((i * 45).toDouble())
                    val x0 = s * .5f + (s * .28f * kotlin.math.cos(a)).toFloat()
                    val y0 = s * .5f + (s * .28f * kotlin.math.sin(a)).toFloat()
                    val x1 = s * .5f + (s * .38f * kotlin.math.cos(a)).toFloat()
                    val y1 = s * .5f + (s * .38f * kotlin.math.sin(a)).toFloat()
                    drawLine(color, Offset(x0, y0), Offset(x1, y1), w)
                }
            }
            "moon" -> {
                drawArc(color, startAngle = 110f, sweepAngle = 220f, useCenter = true,
                    topLeft = Offset(s * .22f, s * .14f), size = Size(s * .62f, s * .72f))
            }
            "pencil" -> {
                drawLine(color, Offset(s * .3f, s * .7f), Offset(s * .72f, s * .28f), w)
                drawLine(color, Offset(s * .72f, s * .28f), Offset(s * .62f, s * .18f), w)
                drawLine(color, Offset(s * .62f, s * .18f), Offset(s * .2f, s * .6f), w)
                drawLine(color, Offset(s * .2f, s * .6f), Offset(s * .3f, s * .7f), w)
                drawLine(color, Offset(s * .16f, s * .84f), Offset(s * .36f, s * .84f), w)
            }
        }
    }
}

@Composable
fun App() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var plan by remember { mutableStateOf(PlanStore.load(ctx)) }
    var tab by remember { mutableIntStateOf(0) }
    var night by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var selectedRoom by remember { mutableStateOf<String?>(null) }

    Surface(color = NightBg) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> HomeTab(plan, night, { night = !night }, { editorOpen = true }, onToggle = { l ->
                            plan = plan.copy(lights = plan.lights.map { if (it.id == l.id) it.copy(on = !it.on) else it })
                            PlanStore.save(ctx, plan)
                        }, selectedRoom = selectedRoom, onSelectRoom = { selectedRoom = it })
                        1 -> DevicesTab(plan)
                        2 -> CalendarTab()
                        else -> MineTab(plan, onEdit = { editorOpen = true })
                    }
                }

                // 底部导航
                Row(Modifier.fillMaxWidth().background(Color(0xFF0C1322))
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(top = 10.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    NavItem("全屋", "home", tab == 0) { tab = 0 }
                    NavItem("设备", "grid", tab == 1) { tab = 1 }
                    NavItem("日历", "calendar", tab == 2) { tab = 2 }
                    NavItem("我的", "user", tab == 3) { tab = 3 }
                }
            }

            if (editorOpen) PlanEditor(ctx, plan, onClose = { editorOpen = false }, onSave = {
                plan = it; PlanStore.save(ctx, it); editorOpen = false
            })
        }
    }
}

@Composable
fun NavItem(label: String, icon: String, active: Boolean, onClick: () -> Unit) {
    val tint = if (active) Accent else Color(0xFF5B688A)
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 4.dp)) {
        Box(Modifier.size(width = 44.dp, height = 30.dp).background(if (active) Color(0xFF22304F) else Color.Transparent, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center) {
            LineIcon(icon, tint)
        }
        Text(label, fontSize = 11.sp, color = tint)
    }
}

@Composable
fun TopIcon(icon: String, active: Boolean = true, onClick: (() -> Unit)? = null) {
    Box(Modifier.size(40.dp).background(if (active) PanelHi else Panel, RoundedCornerShape(13.dp))
        .clickable { onClick?.invoke() }, contentAlignment = Alignment.Center) {
        when (icon) {
            "☀" -> LineIcon("sun", if (active) Warm else Color(0xFF5B688A))
            "🌙" -> LineIcon("moon", if (active) Accent else Color(0xFF5B688A))
            else -> LineIcon("home", Color(0xFF93A0BE))
        }
    }
}

@Composable
fun HomeTab(
    plan: Plan, night: Boolean, onToggleNight: () -> Unit, onEdit: () -> Unit,
    onToggle: (LightDevice) -> Unit, selectedRoom: String?, onSelectRoom: (String?) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            AndroidView(
                factory = { c -> Home3DView(c).apply { this.plan = plan; onLightToggle = onToggle } },
                update = { v -> v.plan = plan; v.setNight(night) },
                modifier = Modifier.fillMaxSize()
            )
            Row(Modifier.fillMaxWidth().padding(top = 46.dp, start = 20.dp, end = 20.dp).align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("晴 4.9°C", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("次卧 26.6°C · 47.2%", color = Color(0xFF93A0BE), fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TopIcon("🏠"); TopIcon("☀", active = !night, onClick = { onToggleNight() }); TopIcon("🌙", active = night, onClick = { onToggleNight() })
                }
            }
            Text("编辑户型", color = Accent, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 106.dp, end = 14.dp)
                    .background(Panel, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 5.dp).clickable(onClick = onEdit))
        }

        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SceneChip("回家"); SceneChip("离家"); SceneChip("睡眠")
        }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip2("全屋", selectedRoom == null) { onSelectRoom(null) }
            plan.rooms.take(4).forEach { r -> FilterChip2(r.name, selectedRoom == r.id) { onSelectRoom(r.id) } }
        }
        Spacer(Modifier.height(12.dp))

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            plan.acs.filter { selectedRoom == null || it.room == selectedRoom }.forEach { ac ->
                DeviceRow("❄", ac.name, if (ac.on) "运行中 · ${ac.target.toInt()}°C" else "已关闭", ac.on)
            }
            plan.lights.filter { selectedRoom == null || it.room == selectedRoom }.forEach { l ->
                val roomName = plan.roomById(l.room)?.name ?: ""
                DeviceRow("💡", l.name, "$roomName · ${if (l.on) "已开启" else "已关闭"}", l.on) { onToggle(l) }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun SceneChip(name: String) {
    Row(Modifier.background(Panel, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 9.dp)) {
        Text(name, color = Color(0xFFC6D0E8), fontSize = 13.sp)
    }
}

@Composable
fun FilterChip2(name: String, active: Boolean, onClick: () -> Unit) {
    Text(name, fontSize = 13.sp, color = if (active) Color.White else Color(0xFF6B7899),
        modifier = Modifier.background(if (active) Color(0xFF2A3B63) else Panel, RoundedCornerShape(13.dp))
            .padding(horizontal = 13.dp, vertical = 7.dp).clickable(onClick = onClick))
}

@Composable
fun DeviceRow(icon: String, title: String, sub: String, on: Boolean, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().background(if (on) Color(0xFF332B18) else Panel, RoundedCornerShape(16.dp))
            .clickable { onClick?.invoke() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(icon, fontSize = 18.sp)
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = Color(0xFF8A96B8), fontSize = 11.sp)
        }
        Box(Modifier.size(10.dp).background(if (on) Warm else Color(0xFF3D4A68), CircleShape))
    }
}

@Composable
fun DevicesTab(plan: Plan) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("设备", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("${plan.lights.size + plan.acs.size}", color = Color(0xFF6B7899), fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Text("实时同步", color = Color(0xFF6FCF97), fontSize = 11.sp)
            Box(Modifier.size(7.dp).background(Color(0xFF6FCF97), CircleShape))
        }
        plan.acs.forEach { ac -> DeviceRow("❄", ac.name, plan.roomById(ac.room)?.name ?: "", ac.on) }
        plan.lights.forEach { l -> DeviceRow("💡", l.name, plan.roomById(l.room)?.name ?: "", l.on) }
    }
}

@Composable
fun CalendarTab() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("日历", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text("暂无日程", color = Color(0xFF6B7899), fontSize = 14.sp)
    }
}

@Composable
fun MineTab(plan: Plan, onEdit: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("我的", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        MineRow("✏️", "编辑户型", onClick = onEdit)
        MineRow("📊", "房间 ${plan.rooms.size} · 灯 ${plan.lights.size} · 空调 ${plan.acs.size}")
        MineRow("ℹ️", "HomeMate 0.1.0")
    }
}

@Composable
fun MineRow(icon: String, text: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).clickable { onClick?.invoke() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(icon); Text(text, color = Color(0xFFC6D0E8), fontSize = 14.sp)
    }
}
