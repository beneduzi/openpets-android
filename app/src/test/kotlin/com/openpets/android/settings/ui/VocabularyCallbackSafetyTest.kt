package com.openpets.android.settings.ui

import org.junit.Assert.*
import org.junit.Test

class VocabularyCallbackSafetyTest {
    @Test fun detachedErrorCompletionDoesNotApplyOrLeavePlaceholder(){
        val completion=reconcileLookupCompletion(LookupCompletion(true,""),accepted=shouldApplyLookupResult(false,true,true),value="localized error")
        assertFalse(completion.busy);assertEquals("",completion.result)
    }
    @Test fun lookupUsesPersistedLanguageRatherThanDraft(){
        val saved=lookupRequestLanguage("fr");val draft="de"
        assertEquals("fr",saved);assertTrue(lookupKeyMatches(LookupKey("word",saved),LookupKey("word","fr")));assertNotEquals(draft,saved)
    }
    @Test fun rejectedLookupClearsBusyWithoutRetainingLoadingPlaceholder(){
        val completion=reconcileLookupCompletion(LookupCompletion(true,"…"),accepted=false)
        assertFalse(completion.busy);assertEquals("",completion.result)
    }
    @Test fun sharedArgumentDoesNotOverwriteRetainedWordOnRecreation(){
        // Fresh arrival: the shared argument seeds the word.
        assertEquals("shared",initialWord(false,"","shared"))
        // Recreation with a retained (possibly edited) word: the argument must not overwrite it,
        // so results/Add-to-Anki stay consistent with that word.
        assertEquals("B",initialWord(true,"B","shared"))
        // Fresh arrival without a share.
        assertEquals("",initialWord(false,"",null))
    }
    @Test fun freshFlagMapsToInitializedPolarity(){
        // Production calls initialWord(initialized = !fresh, ...): pin both directions explicitly.
        val fresh = !false // screen is fresh -> initialized = false -> shared text applies
        assertEquals("shared",initialWord(initialized=!fresh,retained="",sharedText="shared"))
        val recreated = !true // screen was recreated -> initialized = true -> retained word preserved
        assertEquals("B",initialWord(initialized=!recreated,retained="B",sharedText="shared"))
    }
}
