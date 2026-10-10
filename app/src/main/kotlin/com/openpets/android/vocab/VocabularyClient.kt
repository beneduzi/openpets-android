package com.openpets.android.vocab

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class VocabularyClient {
    private val worker = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())

    fun define(word: String, onResult: (List<VocabDefinition>) -> Unit, onError: (String) -> Unit) = runRequest(
        "https://api.dictionaryapi.dev/api/v2/entries/en/${encode(word)}", onResult, onError, ::parseDefinitions,
    )

    fun translate(text: String, from: String, to: String, onResult: (String) -> Unit, onError: (String) -> Unit) = runRequest(
        "https://api.mymemory.translated.net/get?q=${encode(text)}&langpair=${encode(from)}%7C${encode(to)}", onResult, onError, ::parseTranslation,
    )

    fun addToAnki(host: String, front: String, back: String, onResult: (Boolean) -> Unit) {
        worker.execute {
            val success = runCatching {
                val base = if (host.startsWith("http://") || host.startsWith("https://")) host.trimEnd('/') else "http://${host.trimEnd('/') }"
                val connection = URL("$base:8765/").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"; connection.connectTimeout = 8000; connection.readTimeout = 8000
                    connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    val note = JSONObject().put("deckName", "Default").put("modelName", "Basic")
                        .put("fields", JSONObject().put("Front", front).put("Back", back)).put("tags", org.json.JSONArray())
                    val body = JSONObject().put("action", "addNote").put("version", 5).put("params", JSONObject().put("note", note)).toString()
                    connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
                    JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).isNull("error")
                } finally { connection.disconnect() }
            }.getOrDefault(false)
            main.post { onResult(success) }
        }
    }

    private fun <T> runRequest(url: String, onResult: (T) -> Unit, onError: (String) -> Unit, parse: (String) -> T?) {
        worker.execute {
            val result = runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 8000; connection.readTimeout = 8000
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) error("HTTP $code")
                    parse(text) ?: error("No result")
                } finally { connection.disconnect() }
            }
            result.fold({ value -> main.post { onResult(value) } }, { error -> main.post { onError(error.message ?: "Lookup failed") } })
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
