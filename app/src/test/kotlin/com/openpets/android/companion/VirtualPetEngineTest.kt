package com.openpets.android.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class VirtualPetEngineTest {
    private var now = 1_000_000L
    private val engine = VirtualPetEngine { now }

    @Test fun decaysStatsOverElapsedTimeAndClampsAtZero() {
        engine.restore(PetStats(5, 5, 5, now))
        now += 30 * 60_000L
        val decayed = engine.snapshot()
        assertEquals(2, decayed.satiety)
        assertEquals(3, decayed.happiness)
        assertEquals(2, decayed.energy)

        engine.restore(PetStats(2, 2, 2, now))
        now += 100 * 60 * 60_000L
        val depleted = engine.snapshot()
        assertEquals(0, depleted.satiety)
        assertEquals(0, depleted.happiness)
        assertEquals(0, depleted.energy)
    }

    @Test fun actionsAdjustAndClampStats() {
        engine.restore(PetStats(90, 90, 50, now))
        assertEquals(100, engine.feed().satiety)

        engine.restore(PetStats(80, 90, 50, now))
        val played = engine.play()
        assertEquals(100, played.happiness)
        assertEquals(35, played.energy)

        engine.restore(PetStats(80, 80, 80, now))
        assertEquals(100, engine.rest().energy)
        engine.restore(PetStats(80, 95, 80, now))
        assertEquals(100, engine.cuddle().happiness)
    }

    @Test fun moodUsesStatThresholds() {
        engine.restore(PetStats(20, 100, 100, now))
        assertEquals(PetMood.HUNGRY, engine.mood())
        engine.restore(PetStats(80, 50, 20, now))
        assertEquals(PetMood.TIRED, engine.mood())
        engine.restore(PetStats(90, 90, 90, now))
        assertEquals(PetMood.EXCITED, engine.mood())
        engine.restore(PetStats(70, 90, 70, now))
        assertEquals(PetMood.HAPPY, engine.mood())
        engine.restore(PetStats(70, 70, 70, now))
        assertEquals(PetMood.CONTENT, engine.mood())
    }
}
