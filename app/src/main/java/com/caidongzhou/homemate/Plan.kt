package com.caidongzhou.homemate

import org.json.JSONArray
import org.json.JSONObject

// 户型数据模型，参考 house-control 的 plan.json：一切皆 JSON，编辑器只改这份数据。
data class Room(val id: String, val name: String, val rect: FloatArray, val floor: String)
data class Opening(val type: String, val from: FloatArray, val to: FloatArray, val sill: Float, val head: Float)
data class Furniture(val type: String, val pos: FloatArray, val rot: Float, val width: Float, val size: FloatArray?, val color: String?)
data class LightDevice(val id: String, val name: String, val room: String, val type: String, val pos: FloatArray, val on: Boolean, val brightness: Float, val entity: String? = null)
data class AcDevice(val id: String, val name: String, val room: String, val pos: FloatArray, val on: Boolean, val target: Float, val entity: String? = null)

data class Plan(
    val name: String,
    val wallHeight: Float,
    val interiorWallHeight: Float,
    val rooms: List<Room>,
    val openings: List<Opening>,
    val furniture: List<Furniture>,
    val lights: List<LightDevice>,
    val acs: List<AcDevice>,
    val cameraAzimuth: Float,
    val cameraElevation: Float
) {
    val bounds: FloatArray
        get() {
            var minX = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
            for (r in rooms) {
                minX = minOf(minX, r.rect[0]); minZ = minOf(minZ, r.rect[1])
                maxX = maxOf(maxX, r.rect[0] + r.rect[2]); maxZ = maxOf(maxZ, r.rect[1] + r.rect[3])
            }
            return floatArrayOf(minX, minZ, maxX, maxZ)
        }

    fun roomById(id: String): Room? = rooms.firstOrNull { it.id == id }

    companion object {
        fun fromJson(text: String): Plan {
            val o = JSONObject(text)
            fun fa(a: JSONArray) = FloatArray(a.length()) { it -> a.getDouble(it).toFloat() }
            val rooms = mutableListOf<Room>()
            val roomArr = o.optJSONArray("rooms") ?: JSONArray()
            for (i in 0 until roomArr.length()) {
                val r = roomArr.getJSONObject(i)
                rooms.add(Room(r.getString("id"), r.getString("name"), fa(r.getJSONArray("rect")), r.optString("floor", "oak")))
            }
            val openings = mutableListOf<Opening>()
            val opArr = o.optJSONArray("openings") ?: JSONArray()
            for (i in 0 until opArr.length()) {
                val p = opArr.getJSONObject(i)
                openings.add(Opening(p.optString("type", "door"), fa(p.getJSONArray("from")), fa(p.getJSONArray("to")),
                    p.optDouble("sill", 0.85).toFloat(), p.optDouble("head", 2.1).toFloat()))
            }
            val furniture = mutableListOf<Furniture>()
            val fArr = o.optJSONArray("furniture") ?: JSONArray()
            for (i in 0 until fArr.length()) {
                val f = fArr.getJSONObject(i)
                furniture.add(Furniture(f.getString("type"), fa(f.getJSONArray("pos")), f.optDouble("rot", 0.0).toFloat(),
                    f.optDouble("width", 1.0).toFloat(), f.optJSONArray("size")?.let { fa(it) }, f.optString("color", null)))
            }
            val lights = mutableListOf<LightDevice>()
            val lArr = o.optJSONArray("lights") ?: JSONArray()
            for (i in 0 until lArr.length()) {
                val l = lArr.getJSONObject(i)
                lights.add(LightDevice(l.getString("id"), l.getString("name"), l.getString("room"), l.optString("type", "ceiling"),
                    fa(l.getJSONArray("pos")), l.optBoolean("on", false), l.optDouble("brightness", 1.0).toFloat(),
                    l.optString("entity", null)))
            }
            val acs = mutableListOf<AcDevice>()
            val aArr = o.optJSONArray("acs") ?: JSONArray()
            for (i in 0 until aArr.length()) {
                val a = aArr.getJSONObject(i)
                acs.add(AcDevice(a.getString("id"), a.getString("name"), a.getString("room"), fa(a.getJSONArray("pos")),
                    a.optBoolean("on", false), a.optDouble("target", 24.0).toFloat(), a.optString("entity", null)))
            }
            val cam = o.optJSONObject("camera") ?: JSONObject()
            return Plan(
                name = o.optString("name", "Home"),
                wallHeight = o.optDouble("wallHeight", 2.6).toFloat(),
                interiorWallHeight = o.optDouble("interiorWallHeight", 1.25).toFloat(),
                rooms = rooms, openings = openings, furniture = furniture, lights = lights, acs = acs,
                cameraAzimuth = cam.optDouble("azimuth", 34.0).toFloat(),
                cameraElevation = cam.optDouble("elevation", 47.0).toFloat()
            )
        }

        fun toJson(p: Plan): String {
            val o = JSONObject()
            o.put("version", 1)
            o.put("name", p.name)
            o.put("wallHeight", p.wallHeight.toDouble())
            o.put("interiorWallHeight", p.interiorWallHeight.toDouble())
            o.put("camera", JSONObject().put("azimuth", p.cameraAzimuth.toDouble()).put("elevation", p.cameraElevation.toDouble()))
            fun ja(items: List<JSONObject>) = JSONArray().also { a -> items.forEach { a.put(it) } }
            o.put("rooms", ja(p.rooms.map { r -> JSONObject().put("id", r.id).put("name", r.name)
                .put("rect", JSONArray(r.rect.map { it.toDouble() })).put("floor", r.floor) }))
            o.put("openings", ja(p.openings.map { g -> JSONObject().put("type", g.type)
                .put("from", JSONArray(g.from.map { it.toDouble() })).put("to", JSONArray(g.to.map { it.toDouble() }))
                .put("sill", g.sill.toDouble()).put("head", g.head.toDouble()) }))
            o.put("furniture", ja(p.furniture.map { f -> JSONObject().put("type", f.type)
                .put("pos", JSONArray(f.pos.map { it.toDouble() })).put("rot", f.rot.toDouble())
                .put("width", f.width.toDouble()).apply { f.size?.let { put("size", JSONArray(it.map { v -> v.toDouble() })) }
                    f.color?.let { put("color", it) } } }))
            o.put("lights", ja(p.lights.map { l -> JSONObject().put("id", l.id).put("name", l.name).put("room", l.room)
                .put("type", l.type).put("pos", JSONArray(l.pos.map { it.toDouble() }))
                .put("on", l.on).put("brightness", l.brightness.toDouble())
                .apply { l.entity?.let { put("entity", it) } } }))
            o.put("acs", ja(p.acs.map { a -> JSONObject().put("id", a.id).put("name", a.name).put("room", a.room)
                .put("pos", JSONArray(a.pos.map { it.toDouble() })).put("on", a.on).put("target", a.target.toDouble())
                .apply { a.entity?.let { put("entity", it) } } }))
            return o.toString(2)
        }

        // 内置示例：两室一厅，带窗户和家具，开箱即可看效果
        fun demo(): Plan = fromJson("""{
          "version": 1, "name": "我的家", "wallHeight": 2.6, "interiorWallHeight": 1.25,
          "camera": { "azimuth": 34, "elevation": 47 },
          "rooms": [
            { "id": "living", "name": "客厅", "rect": [0, 0, 4.8, 4.2], "floor": "oak" },
            { "id": "dining", "name": "餐厅", "rect": [4.8, 0, 2.6, 3.2], "floor": "tile" },
            { "id": "kitchen", "name": "厨房", "rect": [4.8, 3.2, 2.6, 2.0], "floor": "stone" },
            { "id": "bed1", "name": "主卧", "rect": [0, 4.2, 3.8, 3.4], "floor": "walnut" },
            { "id": "bed2", "name": "次卧", "rect": [3.8, 4.2, 2.6, 3.4], "floor": "oak" },
            { "id": "bath", "name": "卫生间", "rect": [6.4, 5.2, 1.0, 2.4], "floor": "tile" }
          ],
          "openings": [
            { "type": "window", "from": [0.6, 0], "to": [3.2, 0] },
            { "type": "window", "from": [5.6, 0], "to": [7.0, 0] },
            { "type": "door", "from": [4.8, 1.2], "to": [4.8, 2.0] },
            { "type": "open", "from": [4.8, 3.6], "to": [4.8, 4.6] },
            { "type": "door", "from": [1.6, 4.2], "to": [2.4, 4.2] },
            { "type": "door", "from": [4.6, 4.2], "to": [5.4, 4.2] },
            { "type": "door", "from": [6.4, 5.4], "to": [6.4, 6.2] }
          ],
          "furniture": [
            { "type": "sofa", "pos": [2.4, 1.1], "rot": 180, "width": 2.4, "color": "#8b93a7" },
            { "type": "coffeeTable", "pos": [2.4, 2.3], "width": 1.1 },
            { "type": "tv", "pos": [2.4, 3.9], "width": 1.6 },
            { "type": "rug", "pos": [2.4, 2.2], "size": [2.8, 1.9], "color": "#4a5568" },
            { "type": "diningSet", "pos": [6.1, 1.5], "width": 1.4, "rot": 90 },
            { "type": "kitchen", "pos": [7.2, 4.0], "width": 1.8, "rot": -90 },
            { "type": "bed", "pos": [1.9, 5.9], "width": 1.8, "rot": 0, "color": "#b98a7a" },
            { "type": "nightstand", "pos": [0.5, 4.7] },
            { "type": "wardrobe", "pos": [0.6, 6.9], "width": 2.2, "rot": 90 },
            { "type": "bed", "pos": [5.1, 6.2], "width": 1.5, "rot": 0, "color": "#7a95b9" },
            { "type": "plant", "pos": [4.4, 0.6], "height": 1.1 }
          ],
          "lights": [
            { "id": "living.main", "name": "客厅主灯", "room": "living", "type": "ceiling", "pos": [2.4, 2.1], "on": true, "brightness": 0.9 },
            { "id": "living.spot", "name": "灯带左键", "room": "living", "type": "strip", "pos": [0.15, 2.1], "y": 2.4 },
            { "id": "dining.main", "name": "餐厅吊灯", "room": "dining", "type": "ceiling", "pos": [6.1, 1.5], "on": true, "brightness": 0.72 },
            { "id": "kitchen.main", "name": "厨房灯", "room": "kitchen", "type": "ceiling", "pos": [6.1, 4.2], "on": true, "brightness": 0.65 },
            { "id": "bed1.main", "name": "主卧灯", "room": "bed1", "type": "ceiling", "pos": [1.9, 5.9], "on": true, "brightness": 0.62 },
            { "id": "bed1.bedside", "name": "床头灯", "room": "bed1", "type": "floor", "pos": [3.3, 4.8] },
            { "id": "bed2.main", "name": "次卧灯", "room": "bed2", "type": "ceiling", "pos": [5.1, 5.9] },
            { "id": "bath.main", "name": "卫生间灯", "room": "bath", "type": "ceiling", "pos": [6.9, 6.4] }
          ],
          "acs": [
            { "id": "bed1.ac", "name": "主卧空调", "room": "bed1", "wall": "west", "pos": [0, 5.9] },
            { "id": "living.ac", "name": "客厅空调", "room": "living", "wall": "east", "pos": [4.8, 2.4] }
          ]
        }""")
    }
}
