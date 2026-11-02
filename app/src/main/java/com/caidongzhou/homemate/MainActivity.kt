package com.caidongzhou.homemate

import android.os.Bundle
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat

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
            "plus" -> {
                drawLine(color, Offset(s * .5f, s * .2f), Offset(s * .5f, s * .8f), w)
                drawLine(color, Offset(s * .2f, s * .5f), Offset(s * .8f, s * .5f), w)
            }
            "bulb" -> {
                drawCircle(color, radius = s * .22f, center = Offset(s * .5f, s * .38f), style = Stroke(w))
                drawLine(color, Offset(s * .39f, s * .66f), Offset(s * .61f, s * .66f), w)
                drawLine(color, Offset(s * .42f, s * .76f), Offset(s * .58f, s * .76f), w)
                drawLine(color, Offset(s * .35f, s * .57f), Offset(s * .35f, s * .66f), w)
                drawLine(color, Offset(s * .65f, s * .57f), Offset(s * .65f, s * .66f), w)
            }
            "snow" -> {
                for (i in 0..2) {
                    val a = Math.toRadians((i * 60).toDouble())
                    val dx = kotlin.math.cos(a).toFloat() * s * .34f
                    val dy = kotlin.math.sin(a).toFloat() * s * .34f
                    drawLine(color, Offset(s * .5f - dx, s * .5f - dy), Offset(s * .5f + dx, s * .5f + dy), w)
                }
            }
            "reset" -> {
                drawArc(color, startAngle = 35f, sweepAngle = 285f, useCenter = false,
                    topLeft = Offset(s * .22f, s * .22f), size = Size(s * .56f, s * .56f), style = Stroke(w))
                drawLine(color, Offset(s * .68f, s * .19f), Offset(s * .76f, s * .31f), w)
                drawLine(color, Offset(s * .76f, s * .31f), Offset(s * .63f, s * .32f), w)
            }
            "chart" -> {
                drawLine(color, Offset(s * .18f, s * .82f), Offset(s * .84f, s * .82f), w)
                drawRoundRect(color, Offset(s * .24f, s * .55f), Size(s * .12f, s * .27f), r)
                drawRoundRect(color, Offset(s * .44f, s * .38f), Size(s * .12f, s * .44f), r)
                drawRoundRect(color, Offset(s * .64f, s * .20f), Size(s * .12f, s * .62f), r)
            }
            "info" -> {
                drawCircle(color, radius = s * .34f, center = Offset(s * .5f, s * .5f), style = Stroke(w))
                drawLine(color, Offset(s * .5f, s * .46f), Offset(s * .5f, s * .68f), w)
                drawCircle(color, radius = 1.8f.dp.toPx(), center = Offset(s * .5f, s * .33f))
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
    var settingsOpen by remember { mutableStateOf(false) }
    var haConnected by remember { mutableStateOf(false) }

    // HA 集成：配置了地址就自动连接
    var ha by remember { mutableStateOf<HaClient?>(null) }
    DisposableEffect(Unit) {
        val client = if (HaSettings.enabled(ctx) && HaSettings.url(ctx).isNotEmpty())
            HaClient(HaSettings.url(ctx), HaSettings.token(ctx)) else null
        ha = client
        client?.onConnectionChanged = { haConnected = it }
        client?.onStateChanged = { states ->
            plan = HaSettings.applyStates(plan, states)
            PlanStore.save(ctx, plan)
        }
        if (client != null) {
            client.fetchStates(plan,
                onDone = { states -> plan = HaSettings.applyStates(plan, states); PlanStore.save(ctx, plan) },
                onError = { })
            client.startWebSocket(plan)
        }
        onDispose { client?.stop() }
    }

    fun toggleLight(l: LightDevice) {
        // 本地立即翻转（乐观更新），再推送到 HA
        plan = plan.copy(lights = plan.lights.map { if (it.id == l.id) it.copy(on = !it.on) else it })
        PlanStore.save(ctx, plan)
        val entity = l.entity
        if (entity != null) ha?.setLight(entity, !l.on, l.brightness)
    }

    val c = uiColors(night)
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
    }
    Surface(color = c.page) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> HomeTab(plan, night, { night = !night }, { editorOpen = true }, onToggle = { l -> toggleLight(l) }, selectedRoom = selectedRoom, onSelectRoom = { selectedRoom = it })
                        1 -> DevicesTab(plan, night)
                        else -> MineTab(plan, night, onEdit = { editorOpen = true }, onHaSettings = { settingsOpen = true },
                            haUrl = HaSettings.url(ctx), haEnabled = HaSettings.enabled(ctx), haConnected = haConnected)
                    }
                }

                // 底部导航：白天白底 + 顶部细分割线
                Column(Modifier.fillMaxWidth().background(c.surface).windowInsetsPadding(WindowInsets.navigationBars)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        NavItem("全屋", "home", tab == 0, c) { tab = 0 }
                        NavItem("设备", "grid", tab == 1, c) { tab = 1 }
                        NavItem("我的", "user", tab == 2, c) { tab = 2 }
                    }
                }
            }

            if (editorOpen) PlanEditor(ctx, plan, onClose = { editorOpen = false }, onSave = {
                plan = it; PlanStore.save(ctx, it); editorOpen = false
                // 重新拉状态（entity 映射可能变了）
                ha?.fetchStates(plan, onDone = { states -> plan = HaSettings.applyStates(plan, states) }, onError = { })
                ha?.stop(); ha?.startWebSocket(plan)
            })

            if (settingsOpen) HaSettingsScreen(
                initialUrl = HaSettings.url(ctx), initialToken = HaSettings.token(ctx),
                initialEnabled = HaSettings.enabled(ctx),
                onClose = { settingsOpen = false },
                onSave = { url, token, enabled ->
                    HaSettings.save(ctx, url, token, enabled)
                    settingsOpen = false
                    ha?.stop()
                    if (enabled && url.isNotEmpty()) {
                        val c = HaClient(url, token)
                        ha = c
                        c.onConnectionChanged = { haConnected = it }
                        c.onStateChanged = { states -> plan = HaSettings.applyStates(plan, states); PlanStore.save(ctx, plan) }
                        c.fetchStates(plan, onDone = { states -> plan = HaSettings.applyStates(plan, states); PlanStore.save(ctx, plan) }, onError = { })
                        c.startWebSocket(plan)
                    } else { ha = null; haConnected = false }
                })
        }
    }
}

@Composable
fun NavItem(label: String, icon: String, active: Boolean, c: UiColors, onClick: () -> Unit) {
    val tint = if (active) c.accent else c.textSecondary
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 4.dp)) {
        Box(Modifier.size(width = 44.dp, height = 30.dp)
            .background(if (active) c.accentSoft else Color.Transparent, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center) {
            LineIcon(icon, tint)
        }
        Text(label, fontSize = 11.sp, color = tint)
    }
}

@Composable
fun TopIcon(icon: String, c: UiColors, active: Boolean = true, onClick: (() -> Unit)? = null) {
    Box(Modifier.size(40.dp)
        .background(if (active) c.surface else c.surfaceAlt, RoundedCornerShape(13.dp))
        .clickable { onClick?.invoke() }, contentAlignment = Alignment.Center) {
        when (icon) {
            "☀" -> LineIcon("sun", if (active) Color(0xFFE8A33D) else c.textSecondary)
            "🌙" -> LineIcon("moon", if (active) c.accent else c.textSecondary)
            else -> LineIcon(icon, c.textSecondary)
        }
    }
}

@Composable
fun HomeTab(
    plan: Plan, night: Boolean, onToggleNight: () -> Unit, onEdit: () -> Unit,
    onToggle: (LightDevice) -> Unit, selectedRoom: String?, onSelectRoom: (String?) -> Unit
) {
    val c = uiColors(night)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(340.dp)) {
            AndroidView(
                factory = { c -> Home3DView(c).apply {
                    this.plan = plan; onLightToggle = onToggle
                    val density = c.resources.displayMetrics.density
                    topInsetPx = 112 * density; bottomInsetPx = 8 * density
                } },
                update = { v -> v.plan = plan; v.setNight(night) },
                modifier = Modifier.fillMaxSize()
            )
            val topText = c.textPrimary
            val topSub = c.textSecondary
            Row(Modifier.fillMaxWidth().padding(top = 46.dp, start = 20.dp, end = 20.dp).align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("晴 4.9°C", color = topText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("次卧 26.6°C · 47.2%", color = topSub, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TopIcon("home", c); TopIcon("☀", c, active = !night, onClick = { onToggleNight() }); TopIcon("🌙", c, active = night, onClick = { onToggleNight() })
                }
            }
            Text("编辑户型", color = c.accent, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 106.dp, end = 14.dp)
                    .background(if (night) c.surface else Color(0xF2FFFFFF), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp).clickable(onClick = onEdit))
        }

        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SceneChip("回家", c); SceneChip("离家", c); SceneChip("睡眠", c)
        }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip2("全屋", selectedRoom == null, c) { onSelectRoom(null) }
            plan.rooms.take(4).forEach { r -> FilterChip2(r.name, selectedRoom == r.id, c) { onSelectRoom(r.id) } }
        }
        Spacer(Modifier.height(12.dp))

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            plan.acs.filter { selectedRoom == null || it.room == selectedRoom }.forEach { ac ->
                DeviceRow("snow", ac.name, if (ac.on) "运行中 · ${ac.target.toInt()}°C" else "已关闭", ac.on, c = c)
            }
            plan.lights.filter { selectedRoom == null || it.room == selectedRoom }.forEach { l ->
                val roomName = plan.roomById(l.room)?.name ?: ""
                DeviceRow("bulb", l.name, "$roomName · ${if (l.on) "已开启" else "已关闭"}", l.on, c = c) { onToggle(l) }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun SceneChip(name: String, c: UiColors) {
    Row(Modifier.background(c.surface, RoundedCornerShape(14.dp)).border(1.dp, c.border, RoundedCornerShape(14.dp))
        .padding(horizontal = 14.dp, vertical = 9.dp)) {
        Text(name, color = c.textPrimary, fontSize = 13.sp)
    }
}

@Composable
fun FilterChip2(name: String, active: Boolean, c: UiColors, onClick: () -> Unit) {
    Text(name, fontSize = 13.sp, color = if (active) c.accent else c.textSecondary,
        modifier = Modifier.background(if (active) c.accentSoft else c.surface, RoundedCornerShape(13.dp))
            .border(1.dp, if (active) c.accentSoft else c.border, RoundedCornerShape(13.dp))
            .padding(horizontal = 13.dp, vertical = 7.dp).clickable(onClick = onClick))
}

@Composable
fun DeviceRow(icon: String, title: String, sub: String, on: Boolean, c: UiColors = uiColors(true), onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth()
            .background(if (on) c.onCardWarm else c.surface, RoundedCornerShape(16.dp))
            .border(1.dp, if (on) Color(0x00FFFFFF) else c.border, RoundedCornerShape(16.dp))
            .clickable { onClick?.invoke() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).background(c.surfaceAlt, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            LineIcon(icon, if (on) Color(0xFFE1A33F) else c.textSecondary, Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = c.textSecondary, fontSize = 11.sp)
        }
        Box(Modifier.size(10.dp).background(if (on) Color(0xFFF0A93E) else c.dotOff, CircleShape))
    }
}

@Composable
fun DevicesTab(plan: Plan, night: Boolean) {
    val c = uiColors(night)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)
        .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("设备", color = c.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("${plan.lights.size + plan.acs.size}", color = c.textSecondary, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Text("实时同步", color = Color(0xFF6FCF97), fontSize = 11.sp)
            Box(Modifier.size(7.dp).background(Color(0xFF6FCF97), CircleShape))
        }
        plan.acs.forEach { ac -> DeviceRow("snow", ac.name, plan.roomById(ac.room)?.name ?: "", ac.on, c) }
        plan.lights.forEach { l -> DeviceRow("bulb", l.name, plan.roomById(l.room)?.name ?: "", l.on, c) }
    }
}

@Composable
fun MineTab(plan: Plan, night: Boolean, onEdit: () -> Unit, onHaSettings: () -> Unit, haUrl: String, haEnabled: Boolean, haConnected: Boolean) {
    val c = uiColors(night)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)
        .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("我的", color = c.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        MineRow("pencil", "编辑户型", c, onClick = onEdit)
        MineRow("home", if (haEnabled && haUrl.isNotEmpty())
            "Home Assistant · ${if (haConnected) "已连接" else "未连接"}" else "Home Assistant · 未配置", c, onClick = onHaSettings)
        MineRow("chart", "房间 ${plan.rooms.size} · 灯 ${plan.lights.size} · 空调 ${plan.acs.size}", c)
        MineRow("info", "HomeMate 0.3.0", c)
    }
}

@Composable
fun MineRow(icon: String, text: String, c: UiColors, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(16.dp))
            .border(1.dp, c.border, RoundedCornerShape(16.dp))
            .clickable { onClick?.invoke() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(38.dp).background(c.surfaceAlt, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            LineIcon(icon, c.accent, Modifier.size(21.dp))
        }
        Text(text, color = c.textPrimary, fontSize = 14.sp)
    }
}
