package com.openpets.android.companion

data class DeviceResources(
    val batteryPercent: Int?,
    val charging: Boolean?,
    val memoryUsedMb: Long?,
    val memoryTotalMb: Long?,
    val storageUsedMb: Long?,
    val storageTotalMb: Long?,
    val networkType: String?,
    val batteryTempC: Float?,
)

object SystemResourcesFormatter {
    fun percent(value: Int?): String = value?.let { "$it%" } ?: "—"

    fun megabytes(value: Long?): String {
        if (value == null) return "—"
        return if (value < 1024L) "$value MB" else "%.1f GB".format(value / 1024.0)
    }

    fun format(resources: DeviceResources): String = buildString {
        append("Battery ").append(percent(resources.batteryPercent))
        append(" · Memory ").append(megabytes(resources.memoryUsedMb))
        append('/').append(megabytes(resources.memoryTotalMb))
        append(" · Storage ").append(megabytes(resources.storageUsedMb))
        append('/').append(megabytes(resources.storageTotalMb))
        append(" · Network ").append(resources.networkType ?: "unavailable")
    }
}
