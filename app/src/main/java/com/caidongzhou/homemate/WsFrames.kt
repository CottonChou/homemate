package com.caidongzhou.homemate

import java.io.InputStream
import java.io.OutputStream

// 最小 WebSocket 帧读写（客户端侧，未掩码的服务器帧 + 掩码的客户端帧）。
class WsFrameReader(private val input: InputStream) {
    fun readMessage(): String {
        var payload = ByteArray(0)
        while (true) {
            val b0 = input.read(); val b1 = input.read()
            if (b0 == -1 || b1 == -1) throw RuntimeException("ws closed")
            val opcode = b0 and 0x0F
            val masked = (b1 and 0x80) != 0
            var len = (b1 and 0x7F).toLong()
            if (len == 126L) len = ((input.read() and 0xFF).toLong() shl 8) or (input.read() and 0xFF).toLong()
            else if (len == 127L) {
                len = 0
                repeat(8) { len = (len shl 8) or (input.read() and 0xFF).toLong() }
            }
            val mask = if (masked) ByteArray(4).also { input.read(it) } else null
            val chunk = ByteArray(len.toInt())
            var read = 0
            while (read < chunk.size) {
                val n = input.read(chunk, read, chunk.size - read)
                if (n == -1) throw RuntimeException("ws eof")
                read += n
            }
            if (mask != null) for (i in chunk.indices) chunk[i] = (chunk[i].toInt() xor mask[i % 4].toInt()).toByte()
            payload += chunk
            if (opcode == 0x1 || opcode == 0x2 || (opcode == 0x8 && payload.isNotEmpty())) break  // text/binary/close-with-payload
            if (opcode == 0x8) throw RuntimeException("ws close frame")
        }
        return String(payload, Charsets.UTF_8)
    }
}

object WsFrames {
    fun writeText(out: OutputStream, text: String) {
        val payload = text.toByteArray(Charsets.UTF_8)
        val mask = ByteArray(4) { (0..255).random().toByte() }
        val header = mutableListOf<Byte>()
        header.add(0x81.toByte())  // FIN + text
        when {
            payload.size < 126 -> header.add((0x80 or payload.size).toByte())
            payload.size < 65536 -> {
                header.add((0x80 or 126).toByte())
                header.add((payload.size shr 8).toByte()); header.add(payload.size.toByte())
            }
            else -> {
                header.add((0x80 or 127).toByte())
                var l = payload.size.toLong()
                repeat(8) { header.add((((l shr ((7 - it) * 8)) and 0xFF).toInt()).toByte()) }
            }
        }
        header.addAll(mask.toList())
        val masked = ByteArray(payload.size) { (payload[it].toInt() xor mask[it % 4].toInt()).toByte() }
        out.write(header.toByteArray()); out.write(masked); out.flush()
    }
}
