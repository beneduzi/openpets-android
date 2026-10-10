package com.openpets.android.protocol

import com.google.gson.JsonParser

/** Parsed `pet.json` manifest for an installed OpenPets pet. */
data class PetManifest(
    val id: String,
    val displayName: String,
    val description: String?,
    val spritesheetPath: String,
    val author: String?,
    val tags: List<String>,
    val source: String?,
    val sourceUrl: String?,
)

private val PET_ID = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")

fun parsePetManifest(json: String): PetManifest {
    val el = try {
        JsonParser.parseString(json)
    } catch (e: Exception) {
        throw IllegalArgumentException("Pet manifest is not valid JSON.", e)
    }
    if (!el.isJsonObject) throw IllegalArgumentException("Pet manifest must be an object.")
    val obj = el.asJsonObject
    val id = obj.stringOrNull("id")
        ?: throw IllegalArgumentException("Pet manifest id is required.")
    if (!PET_ID.matches(id) || id == "builtin") throw IllegalArgumentException("Pet manifest id is invalid.")
    val displayName = obj.stringOrNull("displayName")
        ?: throw IllegalArgumentException("Pet manifest displayName is required.")
    if (displayName.isBlank()) throw IllegalArgumentException("Pet manifest displayName is empty.")
    val spritesheetPath = obj.stringOrNull("spritesheetPath") ?: "spritesheet.webp"
    val tags = mutableListOf<String>()
    val tagsEl = obj.get("tags")
    if (tagsEl != null && tagsEl.isJsonArray) {
        tagsEl.asJsonArray.forEach { t -> if (t.isJsonPrimitive && t.asJsonPrimitive.isString) tags.add(t.asString) }
    }
    return PetManifest(
        id = id,
        displayName = displayName,
        description = obj.stringOrNull("description"),
        spritesheetPath = spritesheetPath,
        author = obj.stringOrNull("author"),
        tags = tags,
        source = obj.stringOrNull("source"),
        sourceUrl = obj.stringOrNull("sourceUrl"),
    )
}
