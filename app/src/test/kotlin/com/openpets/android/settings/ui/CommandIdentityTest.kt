package com.openpets.android.settings.ui

import com.openpets.android.net.RemoteCommand
import org.junit.Assert.*
import org.junit.Test

class CommandIdentityTest {
    @Test fun logicalKeysSeparateDelimiterAmbiguity(){
        val left=commandRowKey(RemoteCommand("a:b","c","",null))
        val right=commandRowKey(RemoteCommand("a","b:c","",null))
        assertNotEquals(left,right)
    }
    @Test fun stableIdsResolveHashCollisionsAndIgnoreOrder(){
        val first=commandRowKey(RemoteCommand("plugin","Aa","",null))
        val second=commandRowKey(RemoteCommand("plugin","BB","",null))
        assertEquals(first.hashCode(),second.hashCode())
        val a=commandRowIds(listOf(first,second));val b=commandRowIds(listOf(second,first))
        assertEquals(a,b);assertNotEquals(a.getValue(first),a.getValue(second))
    }
}
