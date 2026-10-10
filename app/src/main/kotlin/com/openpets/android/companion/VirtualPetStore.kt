package com.openpets.android.companion

import android.content.Context

class VirtualPetStore(context: Context) {
    private val preferences = context.getSharedPreferences("openpets_virtualpet", Context.MODE_PRIVATE)

    fun load(): PetStats = PetStats(
        preferences.getInt(KEY_SATIETY, 100).coerceIn(0, 100),
        preferences.getInt(KEY_HAPPINESS, 100).coerceIn(0, 100),
        preferences.getInt(KEY_ENERGY, 100).coerceIn(0, 100),
        preferences.getLong(KEY_UPDATED_AT, System.currentTimeMillis()),
    )

    fun save(stats: PetStats) {
        preferences.edit()
            .putInt(KEY_SATIETY, stats.satiety.coerceIn(0, 100))
            .putInt(KEY_HAPPINESS, stats.happiness.coerceIn(0, 100))
            .putInt(KEY_ENERGY, stats.energy.coerceIn(0, 100))
            .putLong(KEY_UPDATED_AT, stats.updatedAtMs)
            .apply()
    }

    companion object {
        private const val KEY_SATIETY = "satiety"
        private const val KEY_HAPPINESS = "happiness"
        private const val KEY_ENERGY = "energy"
        private const val KEY_UPDATED_AT = "updatedAtMs"
    }
}
