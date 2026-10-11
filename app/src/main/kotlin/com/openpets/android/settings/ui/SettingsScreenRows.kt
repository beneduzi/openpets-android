package com.openpets.android.settings.ui

sealed class SettingRow(open val id: Long) {
    data class SectionHeader(val title: String, override val id: Long = title.hashCode().toLong()) : SettingRow(id)
    data class SwitchRow(val key: String, val title: String, val summary: String?, val checked: Boolean, override val id: Long = key.hashCode().toLong()) : SettingRow(id)
    data class SwitchDetailRow(val key: String, val title: String, val summary: String?, val checked: Boolean, val detailDestination: String, override val id: Long = key.hashCode().toLong()) : SettingRow(id)
    data class TextFieldRow(val key: String, val title: String, val value: String, val hint: String?, val error: String?, val numeric: Boolean, val enabled: Boolean = true, override val id: Long = key.hashCode().toLong()) : SettingRow(id)
    data class SecretRow(val key: String, val title: String, val value: String, val error: String?, override val id: Long = key.hashCode().toLong()) : SettingRow(id)
    data class InfoRow(val key: String, val title: String, val value: String, val actionLabel: String? = null, override val id: Long = key.hashCode().toLong()) : SettingRow(id)
    data class ActionRow(val key: String, val title: String, val summary: String?, val enabled:Boolean=true, override val id: Long = key.hashCode().toLong()) : SettingRow(id)
    data class SliderRow(val key:String,val title:String,val value:Float,val min:Float,val max:Float,val valueLabel:String,override val id:Long=key.hashCode().toLong()):SettingRow(id)
    data class ChoiceRow(val key:String,val title:String,val options:List<Pair<String,String>>,val selectedKey:String,override val id:Long=key.hashCode().toLong()):SettingRow(id)
}

fun withTextFieldValue(rows:List<SettingRow>,key:String,value:String):List<SettingRow>{
    val position=rows.indexOfFirst{it.id==key.hashCode().toLong()}
    if(position<0)return rows
    return rows.toMutableList().also { items->items[position]=when(val row=items[position]){
        is SettingRow.TextFieldRow->row.copy(value=value)
        is SettingRow.SecretRow->row.copy(value=value)
        else->row
    } }
}
