package com.openpets.android.settings.ui

import com.openpets.android.net.RemoteCommand
import com.openpets.android.companion.MoodEntry
import org.junit.Assert.*
import org.junit.Test

class Phase6CarryoverTest {
    @Test fun commandIdentityUsesLogicalSourceAndIdAcrossReordering(){
        val first=RemoteCommand("plugin-a","run","Run",null);val second=RemoteCommand("plugin-b","run","Run",null)
        val oldOrder=listOf(first,second).associate{commandRowKey(it) to it.title}
        val newOrder=listOf(second,first).associate{commandRowKey(it) to it.title}
        assertEquals(oldOrder.keys,newOrder.keys);assertNotEquals(commandRowKey(first),commandRowKey(second))
    }
    @Test fun moodHistoryIdentityCannotCollideWithFixedScreenKeys(){
        val ids=moodRowIds(listOf(MoodEntry(4,null,1L),MoodEntry(3,null,2L)))
        val fixed=listOf("moodNote","rate1","latestMood","averageMood","nudge","showMood").map{it.hashCode().toLong()}
        assertTrue(ids.none{it in fixed})
    }
    @Test fun savedLanguageChangeInvalidatesCachedTranslation(){assertTrue(savedLanguageChanged("en","fr"));assertFalse(savedLanguageChanged("fr","fr"))}
    @Test fun confirmedDiscardRunsShowActionAfterDiscard(){var discarded=false;var shown=false;discardThen({discarded=true}){shown=true};assertTrue(discarded);assertTrue(shown)}
}
