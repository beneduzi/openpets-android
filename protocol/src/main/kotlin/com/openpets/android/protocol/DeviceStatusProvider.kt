package com.openpets.android.protocol

/** Supplies this device's identity and hardware status for the `device.status` IPC method. */
interface DeviceStatusProvider {
    fun deviceStatus(): DeviceStatus
}

/** Flat snapshot; the dispatcher groups it into `identity` / `display` / `resources` JSON objects. */
data class DeviceStatus(
    val manufacturer: String? = null,
    val model: String? = null,
    val androidVersion: String? = null,
    val sdkInt: Int? = null,
    val screenWidthPx: Int? = null,
    val screenHeightPx: Int? = null,
    val densityDpi: Int? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val batteryTempC: Float? = null,
    val memoryUsedMb: Long? = null,
    val memoryTotalMb: Long? = null,
    val storageUsedMb: Long? = null,
    val storageTotalMb: Long? = null,
    val networkType: String? = null,
)
