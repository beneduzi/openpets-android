package com.openpets.android.protocol

/** Result of an install request (`pets.install` / `pets.install-local`). */
data class PetInstallResult(
    val petId: String,
    val displayName: String,
)

/**
 * Installs pets on demand. Implemented by the app (which owns storage/network);
 * injected into [IpcServer] so the pure protocol layer stays IO-free.
 */
interface PetInstaller {
    /** Installs a pet from the online catalog by [petId]. */
    fun installFromCatalog(petId: String): PetInstallResult

    /** Installs a pet from a pushed `.zip` (base64) or a zip [url]. */
    fun installFromZip(dataBase64: String?, url: String?): PetInstallResult
}
