package com.openpets.android.protocol

/**
 * Sprite-sheet animation metadata and the agent-reaction → sprite-state mapping.
 * Mirrors `reaction-animation-mapping.js` from the OpenPets desktop app.
 */
data class SpriteState(
    val id: String,
    val row: Int,
    val frames: Int,
    val durationMs: Long,
    /** null means the animation loops forever. */
    val iterations: Int?,
)

/** Frame geometry: 192x208 frames, 8 columns x 9 rows (background-size 1536x1872). */
const val SPRITE_FRAME_WIDTH = 192
const val SPRITE_FRAME_HEIGHT = 208
const val SPRITE_COLUMNS = 8
const val SPRITE_ROWS = 9

val SPRITE_STATES: Map<String, SpriteState> = linkedMapOf(
    "idle" to SpriteState("idle", row = 0, frames = 6, durationMs = 5500, iterations = null),
    "running-right" to SpriteState("running-right", row = 1, frames = 8, durationMs = 1060, iterations = null),
    "running-left" to SpriteState("running-left", row = 2, frames = 8, durationMs = 1060, iterations = null),
    "waving" to SpriteState("waving", row = 3, frames = 4, durationMs = 700, iterations = 2),
    "jumping" to SpriteState("jumping", row = 4, frames = 5, durationMs = 840, iterations = 2),
    "failed" to SpriteState("failed", row = 5, frames = 8, durationMs = 1220, iterations = 2),
    "waiting" to SpriteState("waiting", row = 6, frames = 6, durationMs = DEFAULT_WAITING_ANIMATION_MS.toLong(), iterations = null),
    "running" to SpriteState("running", row = 7, frames = 6, durationMs = 820, iterations = null),
    "review" to SpriteState("review", row = 8, frames = 6, durationMs = 1030, iterations = null),
)

/** Animation state ids a user may pick (excludes directional run states). */
val SELECTABLE_ANIMATIONS: List<String> = listOf(
    "idle", "review", "running", "waiting", "waving", "jumping", "failed",
)

/** Reactions that represent ongoing agent work and stay "sticky" until completion. */
val BUSY_REACTIONS: Set<String> = setOf(
    "thinking", "working", "editing", "running", "testing", "waiting",
)

val DEFAULT_REACTION_TO_SPRITE_STATE: Map<String, String> = mapOf(
    "idle" to "idle",
    "thinking" to "review",
    "working" to "running",
    "editing" to "running",
    "running" to "running",
    "testing" to "waiting",
    "waiting" to "waiting",
    "waving" to "waving",
    "success" to "jumping",
    "error" to "failed",
    "celebrating" to "jumping",
)

/**
 * Resolve the sprite state for a reaction, honoring optional per-reaction overrides.
 * Unknown/absent reaction resolves to `idle`, matching the desktop behavior.
 */
fun resolveReactionSpriteState(reaction: String?, overrides: Map<String, String>? = null): String {
    if (reaction.isNullOrEmpty()) return "idle"
    val override = overrides?.get(reaction)
    if (override != null && SPRITE_STATES.containsKey(override)) return override
    return DEFAULT_REACTION_TO_SPRITE_STATE[reaction] ?: "idle"
}

/** Returns the [SpriteState] for a sprite-state id, defaulting to idle. */
fun spriteStateFor(stateId: String): SpriteState = SPRITE_STATES[stateId] ?: SPRITE_STATES.getValue("idle")

/** Returns the sprite state with the configured `waiting` duration applied. */
fun configuredSpriteState(
    stateId: String,
    waitingDurationMs: Int = DEFAULT_WAITING_ANIMATION_MS,
): SpriteState {
    val base = SPRITE_STATES[stateId] ?: SPRITE_STATES.getValue("idle")
    return if (base.id == "waiting") base.copy(durationMs = waitingDurationMs.toLong()) else base
}
