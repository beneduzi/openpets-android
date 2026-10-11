package com.openpets.android.settings.ui

/** Produces stable, distinct RecyclerView IDs for distinct row keys, including hash collisions. */
private val settingRowIds=linkedMapOf<String,Long>()
private val allocatedSettingRowIds=mutableSetOf<Long>()
@Synchronized fun uniqueSettingRowIds(keys:List<String>):List<Long> {
    val occurrences=mutableMapOf<String,Int>()
    return keys.map{key->val occurrence=occurrences.getOrDefault(key,0);occurrences[key]=occurrence+1
        val stableKey=if(occurrence==0)key else "$key#$occurrence"
        settingRowIds.getOrPut(stableKey){var id=stableKey.hashCode().toLong();while(!allocatedSettingRowIds.add(id))id++;id}}
}
