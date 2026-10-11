package com.openpets.android.pet

import com.openpets.android.protocol.MAX_MEDIA_BYTES
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Downloads media image/GIF bytes over http(s). */
object MediaLoader {

    @Throws(IOException::class)
    fun downloadBytes(url: String, maxBytes: Int = MAX_MEDIA_BYTES): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("accept", "image/*")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size > maxBytes) throw IOException("Image is too large")
            return bytes
        } finally {
            connection.disconnect()
        }
    }
}
