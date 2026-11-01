package com.caidongzhou.homemate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Home Assistant 连接设置：URL + 长期访问令牌。
@Composable
fun HaSettingsScreen(
    initialUrl: String, initialToken: String, initialEnabled: Boolean,
    onClose: () -> Unit, onSave: (url: String, token: String, enabled: Boolean) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf(initialToken) }
    var enabled by remember { mutableStateOf(initialEnabled) }

    Box(Modifier.fillMaxSize().background(Color(0xF2080D18))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 52.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("取消", color = Color(0xFF9FB0D6), fontSize = 16.sp, modifier = Modifier.clickable(onClick = onClose).padding(4.dp))
                Text("Home Assistant", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("保存", color = Color(0xFF8FB2FF), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onSave(url, token, enabled) }.padding(4.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                Text("启用连接", color = Color(0xFFC6D0E8), fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { value -> enabled = value },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedTrackColor = Color(0xFF33507F), checkedThumbColor = Color(0xFFBFD4FF)))
            }

            Text("服务器地址", color = Color(0xFF9FB0D6), fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                placeholder = { Text("http://192.168.1.100:8123", color = Color(0xFF5B688A), fontSize = 14.sp) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF33507F), unfocusedBorderColor = Color(0xFF26314E),
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Accent))
            Spacer(Modifier.height(16.dp))

            Text("长期访问令牌", color = Color(0xFF9FB0D6), fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            OutlinedTextField(
                value = token, onValueChange = { token = it },
                placeholder = { Text("HA 用户头像 → 安全 → 长期访问令牌", color = Color(0xFF5B688A), fontSize = 13.sp) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF33507F), unfocusedBorderColor = Color(0xFF26314E),
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Accent))
            Spacer(Modifier.height(20.dp))

            Text(
                "设备映射：在户型编辑器之外，编辑 plan.json 给灯和空调加 \"entity\" 字段即可绑定 HA 实体，" +
                "例如 \"entity\": \"light.living_room\"、\"entity\": \"climate.bedroom\"。" +
                "绑定后 App 内的开关/温度操作会同步到 HA，HA 侧的变动也会实时反映到 3D 场景。",
                color = Color(0xFF8A96B8), fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}
