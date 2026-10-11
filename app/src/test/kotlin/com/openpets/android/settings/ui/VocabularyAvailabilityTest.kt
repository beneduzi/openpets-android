package com.openpets.android.settings.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyAvailabilityTest {
    @Test fun blankAnkiHostKeepsNetworkActionUnavailable(){assertFalse(isAnkiHostAvailable(""));assertFalse(isAnkiHostAvailable("   "));assertTrue(isAnkiHostAvailable("localhost"))}
    @Test fun vocabularySettingsTrimLikeExistingPersistenceSetters(){assertEquals("fr",normalizeVocabularyLanguage("  fr "));assertEquals("anki.local:8765",normalizeAnkiHost(" anki.local:8765 "))}
}
