package com.openpets.android.protocol

/** Non-looping reactions that play a fixed number of animation cycles then settle. */
val TRANSIENT_REACTIONS: Set<String> = setOf("waving", "success", "error", "celebrating")

/**
 * Pure reaction/animation state machine driving the pet.
 *
 * Busy reactions (active agent work) are "sticky": they remain until a newer
 * reaction arrives. Transient reactions (waving/success/error/celebrating) play
 * once, then settle back to the most recent busy reaction, or idle if none.
 */
class PetReactionStateMachine(initialReaction: String = "idle") {
    var currentReaction: String = if (initialReaction in REACTIONS) initialReaction else "idle"
        private set

    private var pendingBusy: String? = null

    /** Apply an incoming reaction. Unknown reactions are ignored. */
    fun onReaction(reaction: String) {
        if (reaction !in REACTIONS) return
        when (reaction) {
            "idle" -> {
                pendingBusy = null
                currentReaction = "idle"
            }
            in BUSY_REACTIONS -> {
                pendingBusy = reaction
                currentReaction = reaction
            }
            else -> {
                // transient: success/error are terminal, so clear the busy backlog
                if (reaction == "success" || reaction == "error") pendingBusy = null
                currentReaction = reaction
            }
        }
    }

    /** Called when a non-looping animation finishes its cycles. */
    fun onAnimationComplete() {
        if (currentReaction in TRANSIENT_REACTIONS) {
            currentReaction = pendingBusy ?: "idle"
        }
    }

    val currentSpriteState: String
        get() = resolveReactionSpriteState(currentReaction)

    val isTransient: Boolean
        get() = currentReaction in TRANSIENT_REACTIONS
}
