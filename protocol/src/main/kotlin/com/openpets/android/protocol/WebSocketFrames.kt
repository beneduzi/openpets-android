package com.openpets.android.protocol

import java.io.EOFException
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

data class Frame(val opcode: Int, val fin: Boolean, val payload: ByteArray)

fun computeAcceptKey(secWebSocketKey: String): String = Base64.getEncoder().encodeToString(
    MessageDigest.getInstance("SHA-1").digest((secWebSocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII))
)

fun decodeClientFrame(input: InputStream, maxPayloadBytes: Int = 1024 * 1024): Frame? {
    fun byte(): Int { val b = input.read(); if (b < 0) throw EOFException(); return b }
    val first = input.read()
    if (first < 0) return null
    val second = byte()
    val fin = first and 0x80 != 0
    val opcode = first and 0x0f
    if (second and 0x80 == 0) throw IllegalArgumentException("Client frame must be masked")
    var length = (second and 0x7f).toLong()
    if (length == 126L) length = (byte().toLong() shl 8) or byte().toLong()
    else if (length == 127L) {
        length = 0
        repeat(8) { length = (length shl 8) or byte().toLong() }
    }
    if (length > maxPayloadBytes || length > Int.MAX_VALUE) throw IllegalArgumentException("Payload too large")
    val mask = ByteArray(4) { byte().toByte() }
    val payload = ByteArray(length.toInt())
    var offset = 0
    while (offset < payload.size) {
        val n = input.read(payload, offset, payload.size - offset)
        if (n < 0) throw EOFException()
        offset += n
    }
    for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
    return Frame(opcode, fin, payload)
}

fun encodeServerTextFrame(text: String): ByteArray = encodeServerControlFrame(0x1, text.toByteArray(Charsets.UTF_8))

fun encodeServerControlFrame(opcode: Int, payload: ByteArray = ByteArray(0)): ByteArray {
    require(opcode in 0..15)
    val out = ByteArrayOutputStream()
    out.write(0x80 or opcode)
    when {
        payload.size < 126 -> out.write(payload.size)
        payload.size <= 0xffff -> { out.write(126); out.write(payload.size ushr 8); out.write(payload.size) }
        else -> {
            out.write(127)
            for (shift in 56 downTo 0 step 8) out.write((payload.size.toLong() ushr shift).toInt())
        }
    }
    out.write(payload)
    return out.toByteArray()
}

fun encodeCloseFrame(code: Int = 1000): ByteArray = encodeServerControlFrame(0x8, byteArrayOf((code ushr 8).toByte(), code.toByte()))
