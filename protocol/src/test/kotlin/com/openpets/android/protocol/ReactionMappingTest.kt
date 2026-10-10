package com.openpets.android.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReactionMappingTest {

    @Test fun mapsEveryReaction() {
        assertEquals("idle", resolveReactionSpriteState("idle"))
        assertEquals("review", resolveReactionSpriteState("thinking"))
        assertEquals("running", resolveReactionSpriteState("working"))
        assertEquals("running", resolveReactionSpriteState("editing"))
        assertEquals("running", resolveReactionSpriteState("running"))
        assertEquals("waiting", resolveReactionSpriteState("testing"))
        assertEquals("waiting", resolveReactionSpriteState("waiting"))
        assertEquals("waving", resolveReactionSpriteState("waving"))
        assertEquals("jumping", resolveReactionSpriteState("success"))
        assertEquals("failed", resolveReactionSpriteState("error"))
        assertEquals("jumping", resolveReactionSpriteState("celebrating"))
    }

    @Test fun unknownReactionFallsBackToIdle() {
        assertEquals("idle", resolveReactionSpriteState("bogus"))
        assertEquals("idle", resolveReactionSpriteState(null))
        assertEquals("idle", resolveReactionSpriteState(""))
    }

    @Test fun overrideWins() {
        assertEquals("waving", resolveReactionSpriteState("thinking", mapOf("thinking" to "waving")))
    }

    @Test fun invalidOverrideIgnored() {
        assertEquals("review", resolveReactionSpriteState("thinking", mapOf("thinking" to "nope")))
    }

    @Test fun spriteStatesGeometry() {
        assertEquals(9, SPRITE_STATES.size)
        SPRITE_STATES.values.forEach { state ->
            assertTrue(state.row in 0 until SPRITE_ROWS)
            assertTrue(state.frames in 1..SPRITE_COLUMNS)
            assertTrue(state.durationMs > 0)
        }
        assertEquals(0, spriteStateFor("idle").row)
        assertEquals(8, spriteStateFor("review").row)
        assertEquals(null, spriteStateFor("idle").iterations)
        assertEquals(2, spriteStateFor("jumping").iterations)
    }

    @Test fun busyReactionsCoverActiveWork() {
        assertTrue(BUSY_REACTIONS.containsAll(listOf("thinking", "working", "editing", "running", "testing", "waiting")))
        assertTrue(!BUSY_REACTIONS.contains("success"))
    }
}

class PetManifestTest {
    @Test fun parsesTuxManifest() {
        val m = parsePetManifest(
            """{"id":"tux","displayName":"Tux","description":"penguin","spritesheetPath":"spritesheet.webp","author":"seymour","tags":[],"source":"codex-pet-share"}"""
        )
        assertEquals("tux", m.id)
        assertEquals("Tux", m.displayName)
        assertEquals("spritesheet.webp", m.spritesheetPath)
    }

    @Test fun rejectsInvalidId() {
        try {
            parsePetManifest("""{"id":"builtin","displayName":"X"}""")
            fail("expected")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun rejectsMissingDisplayName() {
        try {
            parsePetManifest("""{"id":"tux"}""")
            fail("expected")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
