package com.openpets.android.settings.ui

import com.openpets.android.companion.MoodEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MoodRowIdsTest {
    @Test fun duplicateTimestampsHaveDistinctStableDiffIdentities(){
        val entries=listOf(MoodEntry(3,"first",123L),MoodEntry(4,"second",123L),MoodEntry(5,null,456L))
        val ids=moodRowIds(entries)
        assertEquals(entries.size,ids.distinct().size)
        assertNotEquals(ids[0],ids[1])
        val diffState=mutableListOf<Long>()
        ids.forEachIndexed{index,id->val existing=diffState.indexOf(id);if(existing<0)diffState.add(index.coerceAtMost(diffState.size),id) else diffState[existing]=id}
        assertEquals(ids.size,diffState.size)
        assertEquals(ids.toSet(),diffState.toSet())
        assertEquals(ids,moodRowIds(entries))
    }
}
