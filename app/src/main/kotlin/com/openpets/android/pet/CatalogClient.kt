package com.openpets.android.pet

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A pet listed in the online Codex Pets catalog. */
data class CatalogPet(
    val id: String,
    val displayName: String,
    val author: String,
    val kind: String,
    val description: String,
    val downloadPath: String,
    val spritesheetUrl: String,
    val likeCount: Int,
)

data class CatalogPage(
    val pets: List<CatalogPet>,
    val page: Int,
    val totalPages: Int,
    val total: Int,
)

/**
 * Minimal client for the Codex Pets catalog (`codex-pets.net`). Downloads
 * `.codex-pet.zip` packages that [PetRepository.importZip] can consume.
 */
class CatalogClient(private val baseUrl: String = "https://codex-pets.net") {

    fun listPets(query: String, page: Int, pageSize: Int = 30): CatalogPage {
        val q = query.trim()
        val url = buildString {
            append("$baseUrl/api/pets?page=$page&pageSize=$pageSize")
            if (q.isNotEmpty()) append("&q=${URLEncoder.encode(q, "UTF-8")}")
        }
        val json = JSONObject(get(url))
        val array = json.optJSONArray("pets") ?: JSONArray()
        val pets = (0 until array.length()).map { index ->
            val p = array.getJSONObject(index)
            CatalogPet(
                id = p.optString("id"),
                displayName = p.optString("displayName"),
                author = p.optString("ownerName").ifEmpty { p.optString("ownerHandle") },
                kind = p.optString("kind"),
                description = p.optString("description"),
                downloadPath = p.optString("downloadUrl"),
                spritesheetUrl = p.optString("spritesheetUrl"),
                likeCount = p.optInt("likeCount"),
            )
        }
        return CatalogPage(
            pets = pets,
            page = json.optInt("page", page),
            totalPages = json.optInt("totalPages", 1).coerceAtLeast(1),
            total = json.optInt("total", pets.size),
        )
    }

    fun downloadPet(pet: CatalogPet, target: File) {
        val path = pet.downloadPath.ifEmpty { "/api/pets/${pet.id}/download" }
        val url = if (path.startsWith("http")) path else baseUrl + path
        target.writeBytes(downloadBytes(url))
    }

    /** Downloads a pet's `.codex-pet.zip` bytes by id. */
    fun downloadZipBytes(petId: String): ByteArray = downloadBytes("$baseUrl/api/pets/$petId/download")

    /** Downloads arbitrary bytes over http(s). */
    fun downloadBytes(url: String): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun download(url: String, target: File) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
    }
}
