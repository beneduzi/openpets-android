package com.openpets.android.pet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.openpets.android.protocol.MAX_MEDIA_BYTES
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Downloads a media image/GIF over http(s) and decodes it to a [Bitmap]. */
object MediaLoader {

    @Throws(IOException::class)
    fun download(url: String, maxBytes: Int = MAX_MEDIA_BYTES): Bitmap {
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
            if (bytes.size > maxBytes) throw IOException("Imagem muito grande")
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: throw IOException("Formato de imagem não suportado")
        } finally {
            connection.disconnect()
        }
    }
}
