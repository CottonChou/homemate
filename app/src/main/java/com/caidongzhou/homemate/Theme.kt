package com.caidongzhou.homemate

import androidx.compose.ui.graphics.Color

// 昼夜两套配色。白天白底深字，夜晚深蓝底浅字。
data class UiColors(
    val page: Color,        // 页面背景
    val surface: Color,     // 卡片 / 导航栏
    val surfaceAlt: Color,  // 次级按钮
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentSoft: Color,  // 选中态背景
    val onCardWarm: Color,  // 已开启设备卡片背景
    val dotOff: Color
)

fun uiColors(night: Boolean): UiColors = if (night) UiColors(
    page = Color(0xFF090D18),
    surface = Color(0xFF141B2E),
    surfaceAlt = Color(0xFF1C2540),
    border = Color(0xFF222C46),
    textPrimary = Color(0xFFF2F5FB),
    textSecondary = Color(0xFF8A96B8),
    accent = Color(0xFF7FA8FF),
    accentSoft = Color(0xFF2A3B63),
    onCardWarm = Color(0xFF332B18),
    dotOff = Color(0xFF3D4A68)
) else UiColors(
    page = Color(0xFFF8FAFD),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF1F4F8),
    border = Color(0xFFE4E8EF),
    textPrimary = Color(0xFF16203A),
    textSecondary = Color(0xFF6B7A94),
    accent = Color(0xFF3D6FE0),
    accentSoft = Color(0xFFE4EBFA),
    onCardWarm = Color(0xFFFFF4E2),
    dotOff = Color(0xFFC9D2E0)
)
