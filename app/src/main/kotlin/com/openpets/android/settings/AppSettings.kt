package com.openpets.android.settings

import android.content.Context
import android.util.Base64
import com.openpets.android.protocol.DEFAULT_WAITING_ANIMATION_MS
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

/**
 * Persisted companion settings. Tokens are generated once and kept stable so
 * paired agents keep working across restarts.
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("openpets", Context.MODE_PRIVATE)

    var ipcEnabled: Boolean
        get() = prefs.getBoolean("ipcEnabled", true)
        set(value) = prefs.edit().putBoolean("ipcEnabled", value).apply()

    var remoteEnabled: Boolean
        get() = prefs.getBoolean("remoteEnabled", true)
        set(value) = prefs.edit().putBoolean("remoteEnabled", value).apply()

    var wsEnabled: Boolean
        get() = prefs.getBoolean("wsEnabled", false)
        set(value) = prefs.edit().putBoolean("wsEnabled", value).apply()

    var hostStatusEnabled: Boolean
        get() = prefs.getBoolean("hostStatusEnabled", false)
        set(value) = prefs.edit().putBoolean("hostStatusEnabled", value).apply()

    var wsPort: Int
        get() = prefs.getInt("wsPort", 18423)
        set(value) = prefs.edit().putInt("wsPort", value).apply()

    var bindAddress: String
        get() = prefs.getString("bindAddress", "0.0.0.0") ?: "0.0.0.0"
        set(value) = prefs.edit().putString("bindAddress", value).apply()

    var ipcPort: Int
        get() = prefs.getInt("ipcPort", DEFAULT_IPC_PORT)
        set(value) = prefs.edit().putInt("ipcPort", value).apply()

    var remotePort: Int
        get() = prefs.getInt("remotePort", DEFAULT_REMOTE_PORT)
        set(value) = prefs.edit().putInt("remotePort", value).apply()

    var ipcToken: String
        get() {
            val existing = prefs.getString("ipcToken", null)
            if (existing != null && existing.length in 16..256) return existing
            val generated = randomHex(16)
            prefs.edit().putString("ipcToken", generated).apply()
            return generated
        }
        set(value) = prefs.edit().putString("ipcToken", value).apply()

    var remoteToken: String
        get() {
            val existing = prefs.getString("remoteToken", null)
            if (existing != null && existing.length in 43..256) return existing
            val generated = randomBase64Url(32)
            prefs.edit().putString("remoteToken", generated).apply()
            return generated
        }
        set(value) = prefs.edit().putString("remoteToken", value).apply()

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean("autoStartOnBoot", true)
        set(value) = prefs.edit().putBoolean("autoStartOnBoot", value).apply()

    var selectedPetId: String
        get() = prefs.getString("selectedPetId", "tux") ?: "tux"
        set(value) = prefs.edit().putString("selectedPetId", value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean("soundEnabled", true)
        set(value) = prefs.edit().putBoolean("soundEnabled", value).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean("hapticsEnabled", true)
        set(value) = prefs.edit().putBoolean("hapticsEnabled", value).apply()

    var ttsEnabled: Boolean
        get() = prefs.getBoolean("ttsEnabled", true)
        set(value) = prefs.edit().putBoolean("ttsEnabled", value).apply()

    var notifyEnabled: Boolean
        get() = prefs.getBoolean("notifyEnabled", true)
        set(value) = prefs.edit().putBoolean("notifyEnabled", value).apply()

    var requireLease: Boolean
        get() = prefs.getBoolean("requireLease", false)
        set(value) = prefs.edit().putBoolean("requireLease", value).apply()

    var customSoundUri: String?
        get() = prefs.getString("customSoundUri", null)
        set(value) = prefs.edit().putString("customSoundUri", value).apply()

    /** Per-reaction sprite-animation overrides (reaction id → sprite state id). */
    var reactionOverrides: Map<String, String>
        get() {
            val raw = prefs.getString("reactionOverrides", null) ?: return emptyMap()
            return try {
                val obj = org.json.JSONObject(raw)
                val map = LinkedHashMap<String, String>()
                obj.keys().forEach { key -> map[key] = obj.getString(key) }
                map
            } catch (_: Exception) {
                emptyMap()
            }
        }
        set(value) {
            val obj = org.json.JSONObject()
            value.forEach { (key, v) -> obj.put(key, v) }
            prefs.edit().putString("reactionOverrides", obj.toString()).apply()
        }

    var waitingDurationMs: Int
        get() = prefs.getInt("waitingDurationMs", DEFAULT_WAITING_ANIMATION_MS)
        set(value) = prefs.edit().putInt("waitingDurationMs", value).apply()

    var launchGreeting: String
        get() = prefs.getString("launchGreeting", "") ?: ""
        set(value) = prefs.edit().putString("launchGreeting", value).apply()

    var launchChecklistEnabled: Boolean
        get() = prefs.getBoolean("launchChecklistEnabled", true)
        set(value) = prefs.edit().putBoolean("launchChecklistEnabled", value).apply()

    var walkaboutMode: String
        get() = prefs.getString("walkaboutMode", "roam")?.takeIf { it in setOf("off", "roam", "patrol", "follow") } ?: "roam"
        set(value) = prefs.edit().putString("walkaboutMode", value.takeIf { it in setOf("off", "roam", "patrol", "follow") } ?: "roam").apply()

    var walkaboutSpeed: Float
        get() = prefs.getFloat("walkaboutSpeed", 1.0f).coerceIn(0.5f, 3.0f)
        set(value) = prefs.edit().putFloat("walkaboutSpeed", value.coerceIn(0.5f, 3.0f)).apply()

    var vocabTargetLang: String
        get() = prefs.getString("vocabTargetLang", "en") ?: "en"
        set(value) = prefs.edit().putString("vocabTargetLang", value.trim()).apply()

    var ankiHost: String
        get() = prefs.getString("ankiHost", "") ?: ""
        set(value) = prefs.edit().putString("ankiHost", value.trim()).apply()

    companion object {
        const val DEFAULT_IPC_PORT = 18420
        const val DEFAULT_REMOTE_PORT = 18421

        fun randomHex(byteCount: Int): String {
            val bytes = ByteArray(byteCount)
            SecureRandom().nextBytes(bytes)
            val sb = StringBuilder(byteCount * 2)
            for (b in bytes) sb.append("%02x".format(b))
            return sb.toString()
        }

        fun randomBase64Url(byteCount: Int): String {
            val bytes = ByteArray(byteCount)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(
                bytes,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
        }

        /** Best-effort LAN IPv4 of the device (wlan/eth preferred). */
        fun findLanIpv4(): String? {
            return try {
                NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { !it.isLoopbackAddress }
                    ?.hostAddress
            } catch (_: Exception) {
                null
            }
        }
    }
}
