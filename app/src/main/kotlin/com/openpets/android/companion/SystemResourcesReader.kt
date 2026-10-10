package com.openpets.android.companion

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs

class SystemResourcesReader(private val context: Context) {
    fun read(): DeviceResources {
        val battery = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = if (status < 0) null else status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val batteryTemp = if (tempTenths == Int.MIN_VALUE) null else tempTenths / 10f

        val memory = runCatching {
            val info = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
            info.availMem to info.totalMem
        }.getOrNull()
        val storage = runCatching {
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            val total = stat.blockCountLong * stat.blockSizeLong
            val available = stat.availableBlocksLong * stat.blockSizeLong
            (total - available).coerceAtLeast(0L) to total
        }.getOrNull()
        val network = runCatching {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val active = manager.activeNetwork ?: return@runCatching "NONE"
            val caps = manager.getNetworkCapabilities(active) ?: return@runCatching null
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                else -> "NONE"
            }
        }.getOrNull()

        return DeviceResources(
            batteryPercent = batteryPercent,
            charging = charging,
            memoryUsedMb = memory?.let { (it.second - it.first).coerceAtLeast(0L) / MB },
            memoryTotalMb = memory?.second?.div(MB),
            storageUsedMb = storage?.first?.div(MB),
            storageTotalMb = storage?.second?.div(MB),
            networkType = network,
            batteryTempC = batteryTemp,
        )
    }

    companion object { private const val MB = 1024L * 1024L }
}
