package com.openpets.android.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetReactionStateMachineTest {

    @Test fun startsIdle() {
        val m = PetReactionStateMachine()
        assertEquals("idle", m.currentReaction)
        assertEquals("idle", m.currentSpriteState)
    }

    @Test fun busyReactionIsSticky() {
        val m = PetReactionStateMachine()
        m.onReaction("thinking")
        assertEquals("review", m.currentSpriteState)
        m.onReaction("editing")
        assertEquals("running", m.currentSpriteState)
        m.onReaction("running")
        assertEquals("running", m.currentSpriteState)
    }

    @Test fun transientSettlesBackToBusy() {
        val m = PetReactionStateMachine()
        m.onReaction("editing")
        m.onReaction("waving")
        assertEquals("waving", m.currentSpriteState)
        m.onAnimationComplete()
        assertEquals("editing", m.currentReaction)
        assertEquals("running", m.currentSpriteState)
    }

    @Test fun transientSettlesToIdleWhenNoBusy() {
        val m = PetReactionStateMachine()
        m.onReaction("celebrating")
        assertTrue(m.isTransient)
        m.onAnimationComplete()
        assertEquals("idle", m.currentReaction)
        assertFalse(m.isTransient)
    }

    @Test fun successClearsBusyBacklog() {
        val m = PetReactionStateMachine()
        m.onReaction("working")   // busy
        m.onReaction("success")   // terminal
        assertEquals("jumping", m.currentSpriteState)
        m.onAnimationComplete()
        assertEquals("idle", m.currentReaction)
    }

    @Test fun errorClearsBusyBacklog() {
        val m = PetReactionStateMachine()
        m.onReaction("testing")
        m.onReaction("error")
        assertEquals("failed", m.currentSpriteState)
        m.onAnimationComplete()
        assertEquals("idle", m.currentReaction)
    }

    @Test fun explicitIdleResets() {
        val m = PetReactionStateMachine()
        m.onReaction("thinking")
        m.onReaction("idle")
        assertEquals("idle", m.currentReaction)
    }

    @Test fun unknownReactionIgnored() {
        val m = PetReactionStateMachine()
        m.onReaction("thinking")
        m.onReaction("bogus")
        assertEquals("thinking", m.currentReaction)
    }

    @Test fun animationCompleteIgnoredForLoopingState() {
        val m = PetReactionStateMachine()
        m.onReaction("thinking")
        m.onAnimationComplete() // no-op for looping busy state
        assertEquals("thinking", m.currentReaction)
    }
}
