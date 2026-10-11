package com.openpets.android.settings.ui

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openpets.android.MainActivity
import com.openpets.android.companion.DeviceResources
import com.openpets.android.companion.SystemResourcesController
import com.openpets.android.companion.SystemResourcesFormatter

class ResourcesFragment:SettingsScreenFragment(){
    override val screenTitle="System resources"
    private var controller:SystemResourcesController?=null
    private var binding:ControllerBinding<DeviceResources>?=null
    private var viewObserver:LifecycleEventObserver?=null
    private var latest=DeviceResources(null,null,null,null,null,null,null,null)
    override fun rows():List<SettingRow> = resourceRows(latest)

    override fun onViewCreated(view:android.view.View,state:Bundle?){
        super.onViewCreated(view,state)
        val host=requireActivity() as MainActivity
        controller=host.resourcesControllerForSettings()
        val c=controller?:return
        binding=ControllerBinding(viewLifecycleOwner.lifecycle,{c.snapshot()},{c.addListener(it)},{c.removeListener(it)},{c.refresh()},render={renderSnapshot(it)})
        viewObserver=LifecycleEventObserver { _,event->when(event){Lifecycle.Event.ON_START->binding?.start();Lifecycle.Event.ON_STOP->binding?.stop();else->Unit} }
        viewLifecycleOwner.lifecycle.addObserver(viewObserver!!)
    }
    private fun renderSnapshot(value:DeviceResources){
        latest=value
        (list!!.adapter as? SettingsScreenAdapter)?.updateChangedRows(resourceRows(value))
    }
    override fun onRowClicked(row:SettingRow){when((row as? SettingRow.ActionRow)?.key){
        "refreshResources"->controller?.refresh()
        "showResources"->{controller?.showOnPet();(requireActivity() as com.openpets.android.settings.ui.SettingsHost).revealStage()}
    }}
    override fun onDestroyView(){viewObserver?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};viewObserver=null;binding?.releaseView();binding=null;controller=null;super.onDestroyView()}
}

private fun resourceRows(r:DeviceResources):List<SettingRow>{
    val unavailable="Unavailable"
    val rows= mutableListOf<SettingRow>(
        SettingRow.InfoRow("battery","Battery",SystemResourcesFormatter.percent(r.batteryPercent)),
        SettingRow.InfoRow("charging","Charging",r.charging?.let{if(it)"Charging" else "Not charging"}?:unavailable),
        SettingRow.InfoRow("memory","Memory","${SystemResourcesFormatter.megabytes(r.memoryUsedMb)} / ${SystemResourcesFormatter.megabytes(r.memoryTotalMb)}"),
        SettingRow.InfoRow("storage","Storage","${SystemResourcesFormatter.megabytes(r.storageUsedMb)} / ${SystemResourcesFormatter.megabytes(r.storageTotalMb)}"),
        SettingRow.InfoRow("network","Network",r.networkType?:unavailable),
    )
    rows+=SettingRow.InfoRow("temperature","Battery temperature",r.batteryTempC?.let{"%.1f °C".format(it)}?:unavailable)
    rows+=SettingRow.ActionRow("refreshResources","Refresh",null)
    rows+=SettingRow.ActionRow("showResources","Show on pet",null)
    return rows
}
