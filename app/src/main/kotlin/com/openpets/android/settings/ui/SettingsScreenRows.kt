package com.openpets.android.settings.ui

sealed class SettingRow(open val id: Long) {
    data class SectionHeader(val title: String, override val id: Long = title.hashCode().toLong()) : SettingRow(id)
    data class SwitchRow(val key: String, val title: String, val summary: String?, val checked: Boolean, override val id: Long = key.hashCode().toLong()) : SettingRow(id)
    data class SwitchDetailRow(val key: String, val title: String, val summary: String?, val checked: Boolean, val detailDestination: String, override val id: Long = key.hashCode().toLong()) : SettingRow(id)
    data class TextFieldRow(val key: String, val title: String, val value: String, val hint: String?, val error: String?, val numeric: Boolean, val enabled: Boolean = true, override val id: Long = key.hashCode().toLong()) : SettingRow(id)
    data class SecretRow(val key: String, val title: String, val value: String, val error: String?, override val id: Long = key.hashCode().toLong()) : SettingRow(id)
    data class InfoRow(val key: String, val title: String, val value: String, val actionLabel: String? = null, override val id: Long = key.hashCode().toLong()) : SettingRow(id)
    data class ActionRow(val key: String, val title: String, val summary: String?, override val id: Long = key.hashCode().toLong()) : SettingRow(id)
}
