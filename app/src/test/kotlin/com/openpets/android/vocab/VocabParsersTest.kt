package com.openpets.android.vocab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VocabParsersTest {
    @Test fun parsesDictionaryDefinitions() {
        val json = """[{"word":"bright","meanings":[{"partOfSpeech":"adjective","definitions":[{"definition":"Giving out or reflecting light.","example":"A bright room."},{"definition":"Intelligent."}]},{"partOfSpeech":"adverb","definitions":[{"definition":"In a bright manner."}]}]}]"""
        val values = parseDefinitions(json)
        assertEquals(3, values.size)
        assertEquals("adjective", values[0].partOfSpeech)
        assertEquals("Giving out or reflecting light.", values[0].definition)
        assertEquals("A bright room.", values[0].example)
        assertEquals("adverb", values[2].partOfSpeech)
    }

    @Test fun parsesMyMemoryTranslation() {
        assertEquals("hola", parseTranslation("""{"responseData":{"translatedText":"hola"}}"""))
    }

    @Test fun malformedJsonIsSafe() {
        assertEquals(emptyList<VocabDefinition>(), parseDefinitions("not json"))
        assertNull(parseTranslation("not json"))
    }
}
