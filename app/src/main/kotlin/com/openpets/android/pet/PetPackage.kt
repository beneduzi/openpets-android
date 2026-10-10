package com.openpets.android.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.openpets.android.protocol.PetManifest
import com.openpets.android.protocol.SPRITE_COLUMNS
import com.openpets.android.protocol.SPRITE_FRAME_HEIGHT
import com.openpets.android.protocol.SPRITE_FRAME_WIDTH
import com.openpets.android.protocol.SPRITE_ROWS
import com.openpets.android.protocol.parsePetManifest
import java.io.File
import java.io.IOException

/** A loadable pet: manifest plus its decoded spritesheet. */
data class PetPackage(
    val manifest: PetManifest,
    val spritesheet: Bitmap,
    val frameWidth: Int = SPRITE_FRAME_WIDTH,
    val frameHeight: Int = SPRITE_FRAME_HEIGHT,
    val columns: Int = SPRITE_COLUMNS,
    val rows: Int = SPRITE_ROWS,
)

/** Loads pet packages bundled in assets (`assets/pets/<id>/`) or imported on disk. */
object PetPackageLoader {
    const val DEFAULT_PET_ID = "tux"

    @Throws(IOException::class)
    fun loadFromAssets(context: Context, petId: String = DEFAULT_PET_ID): PetPackage {
        val manifest = readManifestFromAssets(context, petId)
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bitmap = context.assets.open("pets/$petId/${manifest.spritesheetPath}").use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IOException("Failed to decode spritesheet for pet '$petId'.")
        return PetPackage(manifest = manifest, spritesheet = bitmap)
    }

    @Throws(IOException::class)
    fun loadFromDir(dir: File): PetPackage {
        val manifest = readManifestFromDir(dir)
        val sheetFile = File(dir, File(manifest.spritesheetPath).name)
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bitmap = BitmapFactory.decodeFile(sheetFile.absolutePath, options)
            ?: throw IOException("Failed to decode spritesheet at ${sheetFile.absolutePath}.")
        return PetPackage(manifest = manifest, spritesheet = bitmap)
    }

    fun readManifestFromAssets(context: Context, petId: String): PetManifest {
        val json = context.assets.open("pets/$petId/pet.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parsePetManifest(json)
    }

    fun readManifestFromDir(dir: File): PetManifest =
        parsePetManifest(File(dir, "pet.json").readText(Charsets.UTF_8))

    /** Lists bundled pet ids present under assets/pets. */
    fun bundledPetIds(context: Context): List<String> {
        val pets = context.assets.list("pets") ?: return emptyList()
        return pets.filter { id -> context.assets.list("pets/$id")?.contains("pet.json") == true }
    }
}
