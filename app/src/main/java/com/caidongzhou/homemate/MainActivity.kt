package com.caidongzhou.homemate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

internal val NightBg = Color(0xFF0A0F1E)
internal val Panel = Color(0xFF141B2E)
internal val PanelHi = Color(0xFF1C2540)
internal val Accent = Color(0xFF7FA8FF)
internal val Warm = Color(0xFFFFC97A)

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

                // 底部导航（避开手势条）
                Row(Modifier.fillMaxWidth().background(Color(0xFF0C1322)).padding(top = 8.dp, bottom = 34.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    NavItem("全屋", "🏠", tab == 0) { tab = 0 }
                    NavItem("设备", "▦", tab == 1) { tab = 1 }
                    NavItem("日历", "📅", tab == 2) { tab = 2 }
                    NavItem("我的", "👤", tab == 3) { tab = 3 }
                }
            }

            if (editorOpen) PlanEditor(ctx, plan, onClose = { editorOpen = false }, onSave = {
                plan = it; PlanStore.save(ctx, it); editorOpen = false
            })
        }
    }
}

@Composable
fun TopIcon(icon: String, active: Boolean = true, onClick: (() -> Unit)? = null) {
    Box(Modifier.size(40.dp).background(if (active) PanelHi else Panel, RoundedCornerShape(14.dp)).clickable { onClick?.invoke() }, contentAlignment = Alignment.Center) {
        Text(icon, fontSize = 17.sp)
    }
}

@Composable
fun NavItem(label: String, icon: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(Modifier.size(46.dp).background(if (active) Color(0xFF2A3B63) else Color.Transparent, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 19.sp, color = if (active) Accent else Color(0xFF6B7899))
        }
        Text(label, fontSize = 11.sp, color = if (active) Accent else Color(0xFF6B7899))
    }
}

@Composable
fun HomeTab(
    plan: Plan, night: Boolean, onToggleNight: () -> Unit, onEdit: () -> Unit,
    onToggle: (LightDevice) -> Unit, selectedRoom: String?, onSelectRoom: (String?) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            AndroidView(
                factory = { c -> Home3DView(c).apply {
                    this.plan = plan
                    onLightToggle = onToggle
                } },
                update = { v -> v.plan = plan; v.setNight(night) },
                modifier = Modifier.fillMaxSize()
            )
            // 顶部状态覆盖在 3D 画布上（对照参考截图）
            Row(Modifier.fillMaxWidth().padding(top = 52.dp, start = 20.dp, end = 20.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("晴 4.9°C", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("次卧 26.6°C · 47.2%", color = Color(0xFF93A0BE), fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TopIcon("🏠"); TopIcon("☀", active = !night, onClick = { onToggleNight() }); TopIcon("🌙", active = night, onClick = { onToggleNight() })
                }
            }
            // 3D 视图角上的编辑入口
            Text("编辑户型", color = Accent, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 110.dp, end = 14.dp).background(Panel, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 5.dp).clickable(onClick = onEdit))
        }

        // 场景快捷
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SceneChip("回家"); SceneChip("离家"); SceneChip("睡眠")
        }
        Spacer(Modifier.height(12.dp))

        // 房间筛选
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip2("全屋", selectedRoom == null) { onSelectRoom(null) }
            plan.rooms.take(4).forEach { r -> FilterChip2(r.name, selectedRoom == r.id) { onSelectRoom(r.id) } }
        }
        Spacer(Modifier.height(12.dp))

        // 设备卡片
        val lights = plan.lights.filter { selectedRoom == null || it.room == selectedRoom }
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            plan.acs.filter { selectedRoom == null || it.room == selectedRoom }.forEach { ac ->
                DeviceRow(icon = "❄", title = ac.name, sub = if (ac.on) "运行中 · ${ac.target.toInt()}°C" else "已关闭", on = ac.on)
            }
            lights.forEach { l ->
                val roomName = plan.roomById(l.room)?.name ?: ""
                DeviceRow(icon = "💡", title = l.name, sub = "$roomName · ${if (l.on) "已开启" else "已关闭"}", on = l.on, onClick = { onToggle(l) })
            }
        }
    }
}

@Composable
fun SceneChip(name: String) {
    Row(Modifier.background(Panel, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("·", color = Color(0xFF6B7899)); Text(name, color = Color(0xFFC6D0E8), fontSize = 13.sp)
    }
}

@Composable
fun FilterChip2(name: String, active: Boolean, onClick: () -> Unit) {
    Text(name, fontSize = 13.sp, color = if (active) Color.White else Color(0xFF6B7899),
        modifier = Modifier.background(if (active) Color(0xFF2A3B63) else Panel, RoundedCornerShape(13.dp)).padding(horizontal = 13.dp, vertical = 7.dp).clickable(onClick = onClick))
}

@Composable
fun DeviceRow(icon: String, title: String, sub: String, on: Boolean, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().background(if (on) Color(0xFF3A3522) else Panel, RoundedCornerShape(16.dp)).padding(14.dp).clickable { onClick?.invoke() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(icon, fontSize = 18.sp)
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = Color(0xFF8A96B8), fontSize = 11.sp)
        }
        Text(if (on) "●" else "○", color = if (on) Warm else Color(0xFF556179), fontSize = 15.sp)
    }
}

@Composable
fun DevicesTab(plan: Plan) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("设备 ${plan.lights.size + plan.acs.size}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("● 实时同步", color = Color(0xFF6FCF97), fontSize = 11.sp)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (plan.acs.map { DeviceInfo("❄", it.name, it.room) } + plan.lights.map { DeviceInfo("💡", it.name, it.room) }).forEach { d ->
                DeviceRow(icon = d.icon, title = d.name, sub = plan.roomById(d.room)?.name ?: "", on = false)
            }
        }
    }
}

private data class DeviceInfo(val icon: String, val name: String, val room: String)

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
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp).clickable { onClick?.invoke() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(icon); Text(text, color = Color(0xFFC6D0E8), fontSize = 14.sp)
    }
}
