package com.openpets.android.companion

import android.content.Context
import com.openpets.android.FeatureHud
import com.openpets.android.PetBus
import com.openpets.android.protocol.HudItem
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Application-scoped API: snapshot(): PetStats; refresh(); feed(); play(); rest(); cuddle();
 * mood(): PetMood; addListener(l: (PetStats) -> Unit); removeListener(l: (PetStats) -> Unit);
 * showOnPet(). Construct with any Context; state is shared through [CompanionRuntime].
 */
class VirtualPetController private constructor(context: Context, runtimeInstance: Boolean) {
    constructor(context: Context) : this(context, false)

    private val delegate: VirtualPetController? = if (runtimeInstance) null else CompanionRuntime.run { init(context); virtualPet }
    private val appContext = context.applicationContext
    private val store = VirtualPetStore(context.applicationContext)
    private val engine = VirtualPetEngine()
    private val listeners = CopyOnWriteArrayList<(PetStats) -> Unit>()

    init { engine.restore(store.load()) }

    @Synchronized fun snapshot(): PetStats = delegate?.snapshot() ?: engine.snapshot()

    @Synchronized fun refresh() {
        delegate?.let { it.refresh(); return }
        val current = engine.snapshot()
        store.save(current)
        listeners.forEach { it(current) }
    }

    @Synchronized fun feed() { delegate?.let { it.feed(); return }; act { engine.feed() } }
    @Synchronized fun play() { delegate?.let { it.play(); return }; act { engine.play() } }
    @Synchronized fun rest() { delegate?.let { it.rest(); return }; act { engine.rest() } }
    @Synchronized fun cuddle() { delegate?.let { it.cuddle(); return }; act { engine.cuddle() } }

    @Synchronized fun mood(): PetMood = delegate?.mood() ?: engine.mood()

    fun addListener(l: (PetStats) -> Unit) { (delegate ?: this).listeners.addIfAbsent(l) }
    fun removeListener(l: (PetStats) -> Unit) { (delegate ?: this).listeners.remove(l) }

    @Synchronized fun showOnPet() {
        delegate?.let { it.showOnPet(); return }
        val current = engine.snapshot()
        val message = when (moodFor(current)) {
            PetMood.HAPPY -> appContext.getString(com.openpets.android.R.string.settings_pet_mood_happy)
            PetMood.CONTENT -> appContext.getString(com.openpets.android.R.string.settings_pet_mood_content)
            PetMood.TIRED -> appContext.getString(com.openpets.android.R.string.settings_pet_mood_tired)
            PetMood.HUNGRY -> appContext.getString(com.openpets.android.R.string.settings_pet_mood_hungry)
            PetMood.EXCITED -> appContext.getString(com.openpets.android.R.string.settings_pet_mood_excited)
        }
        PetBus.hud(
            FeatureHud(
                bubbleId = "virtual-pet",
                source = "virtualpet",
                message = message,
                subtext = null,
                hud = listOf(
                    HudItem(appContext.getString(com.openpets.android.R.string.settings_pet_satiety), current.satiety.toFloat(), 100f, "green", null),
                    HudItem(appContext.getString(com.openpets.android.R.string.settings_pet_mood_happy), current.happiness.toFloat(), 100f, "pink", null),
                    HudItem(appContext.getString(com.openpets.android.R.string.settings_pet_energy), current.energy.toFloat(), 100f, "blue", null),
                ),
                claim = true,
            ),
        )
    }

    private inline fun act(action: () -> PetStats) {
        val current = action()
        store.save(current)
        listeners.forEach { it(current) }
    }

    private fun moodFor(stats: PetStats): PetMood = when {
        stats.satiety < 25 -> PetMood.HUNGRY
        stats.energy < 25 -> PetMood.TIRED
        stats.satiety >= 80 && stats.happiness >= 80 && stats.energy >= 80 -> PetMood.EXCITED
        stats.happiness >= 80 -> PetMood.HAPPY
        else -> PetMood.CONTENT
    }

    companion object {
        internal fun createRuntime(context: Context) = VirtualPetController(context, true)
    }
}
