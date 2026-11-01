package com.caidongzhou.homemate

import android.content.Context

// HA 连接配置与实体映射，存 SharedPreferences。
object HaSettings {
    private const val PREFS = "ha_settings"

    fun url(ctx: Context): String = ctx.getSharedPreferences(PREFS, 0).getString("url", "") ?: ""
    fun token(ctx: Context): String = ctx.getSharedPreferences(PREFS, 0).getString("token", "") ?: ""
    fun enabled(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, 0).getBoolean("enabled", false)

    fun save(ctx: Context, url: String, token: String, enabled: Boolean) {
        ctx.getSharedPreferences(PREFS, 0).edit()
            .putString("url", url.trim()).putString("token", token.trim()).putBoolean("enabled", enabled)
            .apply()
    }

    /** 把 HA 实体状态应用到 plan（就地返回新 plan） */
    fun applyStates(plan: Plan, states: Map<String, org.json.JSONObject>): Plan {
        var p = plan
        for (l in p.lights) {
            val e = l.entity ?: continue
            val st = states[e] ?: continue
            val on = st.optString("state") == "on"
            val bri = st.optJSONObject("attributes")?.optDouble("brightness", 255.0)?.toFloat()?.div(255f) ?: l.brightness
            p = p.copy(lights = p.lights.map { if (it.id == l.id) it.copy(on = on, brightness = bri.coerceIn(0f, 1f)) else it })
        }
        for (a in p.acs) {
            val e = a.entity ?: continue
            val st = states[e] ?: continue
            val on = st.optString("state") != "off" && st.optString("state") != "unavailable"
            val temp = st.optJSONObject("attributes")?.optDouble("temperature", a.target.toDouble())?.toFloat() ?: a.target
            p = p.copy(acs = p.acs.map { if (it.id == a.id) it.copy(on = on, target = temp) else it })
        }
        return p
    }
}
