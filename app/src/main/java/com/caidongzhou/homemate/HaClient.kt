package com.caidongzhou.homemate

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.roundToInt

// Home Assistant 客户端：REST API 控制设备 + WebSocket 长连接订阅状态变化。
//
// plan.json 的 lights/acs 增加 "entity" 字段即可映射到 HA 实体：
//   { "id": "living.main", "entity": "light.living_room", ... }
//   { "id": "bed1.ac", "entity": "climate.bedroom", ... }
// 没有 entity 字段的设备保持纯本地模拟。
class HaClient(private val baseUrl: String, private val token: String) {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var wsThread: Thread? = null
    @Volatile private var closed = false
    @Volatile private var wsSocket: java.net.Socket? = null
    private var wsIn: WsFrameReader? = null
    private var wsOut: java.io.OutputStream? = null

    var onStateChanged: ((Map<String, JSONObject>) -> Unit)? = null  // entity_id -> state
    var onConnectionChanged: ((Boolean) -> Unit)? = null

    // ---------- REST ----------

    private fun request(method: String, path: String, body: JSONObject? = null): JSONArray {
        val conn = URL("${baseUrl.trimEnd('/')}/api$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = conn.responseCode
        if (code !in 200..299) throw RuntimeException("HA $method $path -> $code")
        val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        return if (text.isBlank()) JSONArray() else JSONArray(text)
    }

    /** 拉取所有相关实体当前状态（灯 + 空调），返回 plan 应用的状态映射 */
    fun fetchStates(plan: Plan, onDone: (Map<String, JSONObject>) -> Unit, onError: (String) -> Unit) {
        io.execute {
            try {
                val states = request("GET", "/states")
                val map = mutableMapOf<String, JSONObject>()
                for (i in 0 until states.length()) {
                    val s = states.getJSONObject(i)
                    map[s.getString("entity_id")] = s
                }
                main.post { onDone(map) }
            } catch (e: Exception) {
                main.post { onError(e.message ?: "fetch failed") }
            }
        }
    }

    /** 开关灯 */
    fun setLight(entity: String, on: Boolean, brightness: Float? = null) {
        val svc = if (on) "turn_on" else "turn_off"
        val body = JSONObject().put("entity_id", entity)
        brightness?.let { if (on) body.put("brightness_pct", (it * 100).roundToInt()) }
        callService("light", svc, body)
    }

    /** 空调：power + 温度 */
    fun setClimate(entity: String, on: Boolean, target: Float? = null) {
        callService("climate", if (on) "turn_on" else "turn_off", JSONObject().put("entity_id", entity))
        if (on) target?.let {
            callService("climate", "set_temperature",
                JSONObject().put("entity_id", entity).put("temperature", it.toDouble()))
        }
    }

    private fun callService(domain: String, service: String, body: JSONObject) {
        io.execute {
            try { request("POST", "/services/$domain/$service", body) } catch (_: Exception) {}
        }
    }

    // ---------- WebSocket（状态推送） ----------

    // HA WS 协议：握手 auth → subscribe_events → 收 state_changed 事件。
    // 这里用最简实现：每条消息一行 JSON。
    fun startWebSocket(plan: Plan) {
        val entities = plan.lights.mapNotNull { it.entity } + plan.acs.mapNotNull { it.entity }
        if (entities.isEmpty()) return
        closed = false
        wsThread = Thread {
            try {
                // WS 握手用 HTTP Upgrade；https 用 SSL socket
                val wsPath = "/api/websocket"
                val secure = baseUrl.startsWith("https")
                val uri = java.net.URI(baseUrl.trimEnd('/'))
                val host = uri.host
                val port = if (uri.port > 0) uri.port else if (secure) 443 else 80
                val raw = if (secure) javax.net.ssl.SSLSocketFactory.getDefault().createSocket(host, port)
                          else java.net.Socket(host, port)
                raw.soTimeout = 0
                val key = android.util.Base64.encodeToString(
                    ByteArray(16) { (0..255).random().toByte() }, android.util.Base64.NO_WRAP)
                val handshake = "GET $wsPath HTTP/1.1\r\n" +
                    "Host: $host:$port\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: $key\r\n" +
                    "Sec-WebSocket-Version: 13\r\n\r\n"
                raw.getOutputStream().write(handshake.toByteArray())
                // 读握手响应头
                val headerReader = BufferedReader(InputStreamReader(raw.getInputStream()))
                var line = headerReader.readLine()
                if (line == null || !line.contains("101")) throw RuntimeException("WS handshake failed: $line")
                while (line != null && line.isNotEmpty()) line = headerReader.readLine()
                wsSocket = raw
                wsIn = WsFrameReader(raw.getInputStream())
                wsOut = raw.getOutputStream()
                val reader = wsIn!!
                val out = wsOut!!

                fun sendJson(o: JSONObject) { WsFrames.writeText(out, o.toString()) }
                fun readJson(): JSONObject = JSONObject(reader.readMessage())

                // 1. 等 auth_required
                readJson()
                // 2. 发 auth
                sendJson(JSONObject().put("type", "auth").put("access_token", token))
                val authResp = readJson()
                if (authResp.optString("type") != "auth_ok") throw RuntimeException("HA auth failed")
                main.post { onConnectionChanged?.invoke(true) }

                // 3. 订阅 state_changed
                sendJson(JSONObject().put("id", 1).put("type", "subscribe_events").put("event_type", "state_changed"))
                while (!closed && !raw.isClosed) {
                    val msg = readJson()
                    if (msg.optString("type") != "event") continue
                    val data = msg.optJSONObject("event")?.optJSONObject("data") ?: continue
                    val entityId = data.optString("entity_id")
                    if (entityId !in entities) continue
                    val newState = data.optJSONObject("new_state") ?: continue
                    val snapshot = mapOf(entityId to newState)
                    main.post { onStateChanged?.invoke(snapshot) }
                }
                raw.close()
            } catch (_: Exception) {
            } finally {
                if (!closed) main.post { onConnectionChanged?.invoke(false) }
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        closed = true
        wsThread?.interrupt()
        try { wsSocket?.close() } catch (_: Exception) {}
        wsSocket = null
        wsThread = null
    }
}
