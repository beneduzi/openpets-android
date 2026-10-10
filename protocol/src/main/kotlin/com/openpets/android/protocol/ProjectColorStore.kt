package com.openpets.android.protocol

import kotlin.random.Random

/**
 * Assigns a random accent color to each project ("source") and keeps it stable
 * while that project's session is alive. Entries older than [ttlMs] are evicted.
 */
class ProjectColorStore(
    private val ttlMs: Long = 30 * 60_000L,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val colors = HashMap<String, Int>()
    private val lastSeen = HashMap<String, Long>()

    @Synchronized
    fun accentFor(source: String?): Int? {
        val key = sanitizeSource(source) ?: return null
        val now = clock()
        evictStale(now)
        val existing = colors[key]
        if (existing != null) {
            lastSeen[key] = now
            return existing
        }
        val color = PALETTE[random.nextInt(PALETTE.size)]
        colors[key] = color
        lastSeen[key] = now
        return color
    }

    @Synchronized
    fun activeSources(): List<String> = colors.keys.toList()

    private fun evictStale(now: Long) {
        val stale = lastSeen.filterValues { now - it > ttlMs }.keys
        stale.forEach {
            colors.remove(it)
            lastSeen.remove(it)
        }
    }

    companion object {
        val PALETTE: IntArray = intArrayOf(
            0xFF60A5FA.toInt(), // blue
            0xFF34D399.toInt(), // green
            0xFFF472B6.toInt(), // pink
            0xFFFBBF24.toInt(), // amber
            0xFFA78BFA.toInt(), // violet
            0xFFFB7185.toInt(), // rose
            0xFF22D3EE.toInt(), // cyan
            0xFFFACC15.toInt(), // yellow
        )
    }
}

/** Normalizes an optional project/source label to a short, printable string. */
fun sanitizeSource(value: String?): String? {
    if (value == null) return null
    val cleaned = value.trim().filterNot { it.isISOControl() }.take(64).trim()
    return cleaned.ifEmpty { null }
}
