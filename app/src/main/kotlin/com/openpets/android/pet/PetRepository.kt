package com.openpets.android.pet

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.openpets.android.protocol.PetDescriptor
import com.openpets.android.protocol.parsePetManifest
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Catalog of available pets: bundled in assets plus pets imported from the
 * phone (zip or folder containing `pet.json` + `spritesheet.webp`).
 *
 * Imported pets live under `filesDir/pets/<id>/` and take precedence over a
 * bundled pet with the same id.
 */
class PetRepository(private val context: Context) {

    private val importedRoot: File
        get() = File(context.filesDir, "pets").apply { mkdirs() }

    fun listPets(): List<PetDescriptor> {
        val result = LinkedHashMap<String, PetDescriptor>()
        PetPackageLoader.bundledPetIds(context).forEach { id ->
            val manifest = runCatching { PetPackageLoader.readManifestFromAssets(context, id) }.getOrNull()
                ?: return@forEach
            result[id] = PetDescriptor(id, manifest.displayName, builtIn = id == "hoot")
        }
        importedPetIds().forEach { id ->
            val manifest = runCatching { PetPackageLoader.readManifestFromDir(File(importedRoot, id)) }.getOrNull()
                ?: return@forEach
            result[id] = PetDescriptor(id, manifest.displayName, builtIn = false)
        }
        return result.values.toList()
    }

    @Throws(IOException::class)
    fun loadPet(id: String): PetPackage {
        val dir = File(importedRoot, id)
        return if (File(dir, "pet.json").isFile) {
            PetPackageLoader.loadFromDir(dir)
        } else {
            PetPackageLoader.loadFromAssets(context, id)
        }
    }

    fun isImported(id: String): Boolean = File(importedRoot, "$id/pet.json").isFile

    fun deleteImported(id: String): Boolean {
        val dir = File(importedRoot, id)
        return dir.exists() && dir.deleteRecursively()
    }

    /** Imports a pet from a `.zip` containing `pet.json` + spritesheet. */
    @Throws(IOException::class)
    fun importZip(uri: Uri): PetDescriptor {
        val temp = newTempDir()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip -> PetImportSupport.extractToDir(zip, temp) }
            } ?: throw IOException("Unable to open the file.")
            return finalizeImport(temp)
        } catch (e: Exception) {
            temp.deleteRecursively()
            throw e
        }
    }

    /** Imports a pet from an in-memory `.zip` (pushed by a client). */
    @Throws(IOException::class)
    fun importZipBytes(bytes: ByteArray): PetDescriptor {
        val temp = newTempDir()
        try {
            ByteArrayInputStream(bytes).use { input ->
                ZipInputStream(input).use { zip -> PetImportSupport.extractToDir(zip, temp) }
            }
            return finalizeImport(temp)
        } catch (e: Exception) {
            temp.deleteRecursively()
            throw e
        }
    }

    /** Imports a pet from a folder containing `pet.json` + spritesheet. */
    @Throws(IOException::class)
    fun importFolder(uri: Uri): PetDescriptor {
        val temp = newTempDir()
        try {
            val root = DocumentFile.fromTreeUri(context, uri)
                ?: throw IOException("Unable to open the folder.")
            val children = root.listFiles()
            val manifestDoc = children.firstOrNull { it.name?.equals("pet.json", true) == true }
                ?: throw IOException("The folder does not contain pet.json.")
            val manifestFile = File(temp, "pet.json")
            copyDoc(manifestDoc, manifestFile)
            val manifest = parsePetManifest(manifestFile.readText(Charsets.UTF_8))
            val sheetName = File(manifest.spritesheetPath).name
            val sheetDoc = children.firstOrNull { it.name == sheetName }
                ?: children.firstOrNull { it.name?.endsWith(".webp", true) == true || it.name?.endsWith(".png", true) == true }
                ?: throw IOException("The folder does not contain the spritesheet.")
            copyDoc(sheetDoc, File(temp, sheetName))
            return finalizeImport(temp)
        } catch (e: Exception) {
            temp.deleteRecursively()
            throw e
        }
    }

    private fun finalizeImport(temp: File): PetDescriptor {
        val manifest = PetPackageLoader.readManifestFromDir(temp)
        val sheetName = File(manifest.spritesheetPath).name
        if (!File(temp, sheetName).isFile) throw IOException("Spritesheet not found in the package.")
        val target = File(importedRoot, manifest.id)
        PetImportSupport.publish(temp, target)
        return PetDescriptor(manifest.id, manifest.displayName, builtIn = false)
    }

    private fun copyDoc(doc: DocumentFile, target: File) {
        context.contentResolver.openInputStream(doc.uri)?.use { input ->
            target.outputStream().use { output -> PetImportSupport.copyBounded(input, output, ImportLimits().maxEntryBytes) }
        } ?: throw IOException("Unable to read ${doc.name}.")
    }

    private fun importedPetIds(): List<String> =
        importedRoot.listFiles { f -> f.isDirectory && File(f, "pet.json").isFile }?.map { it.name } ?: emptyList()

    private fun newTempDir(): File =
        File(context.cacheDir, "pet-import-${System.currentTimeMillis()}").apply { mkdirs() }
}
