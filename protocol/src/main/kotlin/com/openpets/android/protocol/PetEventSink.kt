package com.openpets.android.protocol

/** A tappable button on a bubble. */
data class PetAction(
    val id: String,
    val label: String,
    /** default | primary | danger */
    val style: String,
    /** Optional URL opened on tap. */
    val url: String?,
    /** Optional http(s) endpoint that receives a POST when tapped (agent/webhook callback). */
    val callbackUrl: String? = null,
)

/** A progress row in a bubble HUD (e.g. virtual-pet stats, focus timer). */
data class HudItem(
    val label: String,
    val value: Float,
    val max: Float,
    val tone: String?,
    val icon: String?,
)

/** A form field in a bubble. */
data class BubbleInput(
    val id: String,
    val label: String,
    /** text | number */
    val type: String,
    val placeholder: String?,
)

/** A rich speech bubble (text + markdown + HUD + actions + optional inputs). */
data class SayRequest(
    val message: String,
    val reaction: String?,
    val source: String?,
    val actions: List<PetAction> = emptyList(),
    val hud: List<HudItem> = emptyList(),
    val inputs: List<BubbleInput> = emptyList(),
    /** http(s) endpoint that receives the submitted form values. */
    val submitUrl: String? = null,
    /** Stable id so the bubble can be updated/dismissed. */
    val bubbleId: String? = null,
    /** Pinned bubbles do not auto-dismiss. */
    val pinned: Boolean = false,
)

/** A request to display an image/GIF on the pet (`pet.showMedia`). */
data class MediaRequest(
    val url: String?,
    val dataBase64: String?,
    val message: String?,
    val reaction: String?,
    val durationMs: Int,
    val clickUrl: String?,
    val actions: List<PetAction> = emptyList(),
    val bubbleId: String? = null,
    val pinned: Boolean = false,
)

/**
 * Sink for pet-driving side effects produced by incoming protocol requests.
 */
interface PetEventSink {
    fun onReact(reaction: String, source: String?)
    fun onSay(request: SayRequest)
    fun onMedia(request: MediaRequest)
    fun onDismissBubble(id: String?)
}
