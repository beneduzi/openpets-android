package com.openpets.android.vocab

import com.google.gson.JsonParser

data class VocabDefinition(val partOfSpeech: String?, val definition: String, val example: String?)

fun parseDefinitions(json: String): List<VocabDefinition> = runCatching {
    val entries = JsonParser.parseString(json).asJsonArray
    buildList {
        entries.forEach { entry ->
            val meanings = entry.asJsonObject.get("meanings")?.takeIf { it.isJsonArray }?.asJsonArray ?: return@forEach
            meanings.forEach { element ->
                val meaning = element.asJsonObject
                val pos = meaning.get("partOfSpeech")?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
                val defs = meaning.get("definitions")?.takeIf { it.isJsonArray }?.asJsonArray ?: return@forEach
                defs.forEach { defElement ->
                    val item = defElement.asJsonObject
                    val definition = item.get("definition")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
                    if (definition.isNotEmpty()) {
                        val example = item.get("example")?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
                        add(VocabDefinition(pos, definition, example))
                    }
                }
            }
        }
    }
}.getOrDefault(emptyList())

fun parseTranslation(json: String): String? = runCatching {
    val response = JsonParser.parseString(json).asJsonObject.get("responseData")
    val obj = if (response?.isJsonArray == true) response.asJsonArray.firstOrNull() else response
    obj?.asJsonObject?.get("translatedText")?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotEmpty() }
}.getOrNull()
