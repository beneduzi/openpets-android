package com.openpets.android.protocol

/** A pet available on the device (bundled or imported). */
data class PetDescriptor(
    val id: String,
    val displayName: String,
    val builtIn: Boolean,
)

/**
 * Supplies the pet catalog and current selection to the IPC/remote servers so
 * `pets.list` / `status` / `lease.acquire` report the active pet.
 */
interface PetCatalogProvider {
    val selectedPetId: String
    fun pets(): List<PetDescriptor>
}

/** Fallback catalog when the host does not supply one. */
class StaticPetCatalogProvider(
    override val selectedPetId: String = "tux",
    private val descriptors: List<PetDescriptor> = listOf(PetDescriptor("tux", "Tux", false)),
) : PetCatalogProvider {
    override fun pets(): List<PetDescriptor> = descriptors
}
