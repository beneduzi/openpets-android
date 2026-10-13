package com.openpets.android.companion

import android.content.Context
import android.os.Build
import com.openpets.android.protocol.DeviceStatus
import com.openpets.android.protocol.DeviceStatusProvider

/** Reads this Android device's identity, screen metrics and `DeviceResources` for `device.status`. */
class AndroidDeviceStatusProvider(context: Context) : DeviceStatusProvider {
    private val appContext = context.applicationContext
    private val reader = SystemResourcesReader(appContext)

    override fun deviceStatus(): DeviceStatus {
        val resources = reader.read()
        val metrics = appContext.resources.displayMetrics
        return DeviceStatus(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            batteryPercent = resources.batteryPercent,
            charging = resources.charging,
            batteryTempC = resources.batteryTempC,
            memoryUsedMb = resources.memoryUsedMb,
            memoryTotalMb = resources.memoryTotalMb,
            storageUsedMb = resources.storageUsedMb,
            storageTotalMb = resources.storageTotalMb,
            networkType = resources.networkType,
        )
    }
}
