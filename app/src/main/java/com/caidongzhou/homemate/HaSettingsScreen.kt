package com.caidongzhou.homemate

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Home Assistant 连接设置：URL + 长期访问令牌。
@Composable
fun HaSettingsScreen(
    initialUrl: String, initialToken: String, initialEnabled: Boolean, night: Boolean,
    onClose: () -> Unit, onSave: (url: String, token: String, enabled: Boolean) -> Unit
) {
    val c = uiColors(night)
    var url by remember { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf(initialToken) }
    var enabled by remember { mutableStateOf(initialEnabled) }
    val view = LocalView.current

    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !night
                isAppearanceLightNavigationBars = !night
            }
        }
        onDispose {
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !night
                    isAppearanceLightNavigationBars = !night
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(c.page)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 52.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("取消", color = c.textSecondary, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onClose).padding(4.dp))
                Text("Home Assistant", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("保存", color = c.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onSave(url, token, enabled) }.padding(4.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                Text("启用连接", color = c.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { value -> enabled = value },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedTrackColor = c.accent, checkedThumbColor = Color.White))
            }

            Text("服务器地址", color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                placeholder = { Text("http://192.168.1.100:8123", color = c.textSecondary, fontSize = 14.sp) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent, unfocusedBorderColor = c.border,
                    focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = c.accent))
            Spacer(Modifier.height(16.dp))

            Text("长期访问令牌", color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            OutlinedTextField(
                value = token, onValueChange = { token = it },
                placeholder = { Text("HA 用户头像 → 安全 → 长期访问令牌", color = c.textSecondary, fontSize = 13.sp) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent, unfocusedBorderColor = c.border,
                    focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = c.accent))
            Spacer(Modifier.height(20.dp))

            Text(
                "设备映射：在户型编辑器之外，编辑 plan.json 给灯和空调加 \"entity\" 字段即可绑定 HA 实体，" +
                "例如 \"entity\": \"light.living_room\"、\"entity\": \"climate.bedroom\"。" +
                "绑定后 App 内的开关/温度操作会同步到 HA，HA 侧的变动也会实时反映到 3D 场景。",
                color = c.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}
