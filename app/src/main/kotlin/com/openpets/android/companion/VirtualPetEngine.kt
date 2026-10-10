package com.openpets.android.companion

/** Pure virtual-pet stat decay and action rules. */
class VirtualPetEngine(private val clock: () -> Long = System::currentTimeMillis) {
    private var stats = PetStats(100, 100, 100, clock())

    fun snapshot(): PetStats {
        val now = clock()
        val elapsed = (now - stats.updatedAtMs).coerceAtLeast(0L)
        val satietyDecay = elapsed / SATIETY_DECAY_MS
        val happinessDecay = elapsed / HAPPINESS_DECAY_MS
        val energyDecay = elapsed / ENERGY_DECAY_MS
        stats = PetStats(
            satiety = (stats.satiety.toLong() - satietyDecay).coerceAtLeast(0L).toInt(),
            happiness = (stats.happiness.toLong() - happinessDecay).coerceAtLeast(0L).toInt(),
            energy = (stats.energy.toLong() - energyDecay).coerceAtLeast(0L).toInt(),
            updatedAtMs = now,
        )
        return stats
    }

    fun feed(): PetStats = act { it.copy(satiety = (it.satiety + 25).coerceAtMost(100)) }

    fun play(): PetStats = act {
        it.copy(happiness = (it.happiness + 20).coerceAtMost(100), energy = (it.energy - 15).coerceAtLeast(0))
    }

    fun rest(): PetStats = act { it.copy(energy = (it.energy + 30).coerceAtMost(100)) }

    fun cuddle(): PetStats = act { it.copy(happiness = (it.happiness + 10).coerceAtMost(100)) }

    fun mood(): PetMood {
        val current = snapshot()
        return when {
            current.satiety < 25 -> PetMood.HUNGRY
            current.energy < 25 -> PetMood.TIRED
            current.satiety >= 80 && current.happiness >= 80 && current.energy >= 80 -> PetMood.EXCITED
            current.happiness >= 80 -> PetMood.HAPPY
            else -> PetMood.CONTENT
        }
    }

    fun restore(stats: PetStats) {
        this.stats = PetStats(
            stats.satiety.coerceIn(0, 100),
            stats.happiness.coerceIn(0, 100),
            stats.energy.coerceIn(0, 100),
            stats.updatedAtMs,
        )
    }

    private inline fun act(transform: (PetStats) -> PetStats): PetStats {
        val current = snapshot()
        val changed = transform(current)
        stats = changed.copy(updatedAtMs = clock())
        return stats
    }

    companion object {
        private const val SATIETY_DECAY_MS = 10 * 60_000L
        private const val ENERGY_DECAY_MS = 10 * 60_000L
        private const val HAPPINESS_DECAY_MS = 15 * 60_000L
    }
}
