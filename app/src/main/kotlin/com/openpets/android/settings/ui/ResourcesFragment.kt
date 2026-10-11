package com.openpets.android.settings.ui

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openpets.android.MainActivity
import com.openpets.android.companion.DeviceResources
import com.openpets.android.companion.SystemResourcesController
import com.openpets.android.companion.SystemResourcesFormatter

class ResourcesFragment:SettingsScreenFragment(){
    override val screenTitle get()=getString(com.openpets.android.R.string.ui_resources)
    private var controller:SystemResourcesController?=null
    private var binding:ControllerBinding<DeviceResources>?=null
    private var viewObserver:LifecycleEventObserver?=null
    private var latest=DeviceResources(null,null,null,null,null,null,null,null)
    override fun rows():List<SettingRow> = resourceRows(latest,requireContext())

    override fun onViewCreated(view:android.view.View,state:Bundle?){
        super.onViewCreated(view,state)
        val host=requireActivity() as MainActivity
        controller=host.resourcesControllerForSettings()
        val c=controller?:return
        latest=c.snapshot();updateRowsIncrementally()
        binding=ControllerBinding(viewLifecycleOwner.lifecycle,{c.snapshot()},{c.addListener(it)},{c.removeListener(it)},{c.refresh()},render={renderSnapshot(it)})
        viewObserver=LifecycleEventObserver { _,event->when(event){Lifecycle.Event.ON_START->binding?.start();Lifecycle.Event.ON_STOP->binding?.stop();else->Unit} }
        viewLifecycleOwner.lifecycle.addObserver(viewObserver!!)
    }
    private fun renderSnapshot(value:DeviceResources){
        latest=value
        (list!!.adapter as? SettingsScreenAdapter)?.updateChangedRows(resourceRows(value,requireContext()))
    }
    override fun onRowClicked(row:SettingRow){when((row as? SettingRow.ActionRow)?.key){
        "refreshResources"->controller?.refresh()
        "showResources"->{controller?.showOnPet();(requireActivity() as com.openpets.android.settings.ui.SettingsHost).revealStage()}
    }}
    override fun onDestroyView(){viewObserver?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};viewObserver=null;binding?.releaseView();binding=null;controller=null;super.onDestroyView()}
}

private fun resourceRows(r:DeviceResources,context:android.content.Context):List<SettingRow>{
    fun get(id:Int)=context.getString(id)
    val unavailable=get(com.openpets.android.R.string.ui_unavailable)
    val rows= mutableListOf<SettingRow>(
        SettingRow.InfoRow("battery",get(com.openpets.android.R.string.resources_battery),SystemResourcesFormatter.percent(r.batteryPercent)),
        SettingRow.InfoRow("charging",get(com.openpets.android.R.string.resources_charging),r.charging?.let{if(it)get(com.openpets.android.R.string.resources_charging) else get(com.openpets.android.R.string.ui_not_charging)}?:unavailable),
        SettingRow.InfoRow("memory",get(com.openpets.android.R.string.resources_memory),"${SystemResourcesFormatter.megabytes(r.memoryUsedMb)} / ${SystemResourcesFormatter.megabytes(r.memoryTotalMb)}"),
        SettingRow.InfoRow("storage",get(com.openpets.android.R.string.resources_storage),"${SystemResourcesFormatter.megabytes(r.storageUsedMb)} / ${SystemResourcesFormatter.megabytes(r.storageTotalMb)}"),
        SettingRow.InfoRow("network",get(com.openpets.android.R.string.resources_network),r.networkType?:unavailable),
    )
    rows+=SettingRow.InfoRow("temperature",get(com.openpets.android.R.string.ui_battery_temperature),r.batteryTempC?.let{java.text.NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).format(it)+" °C"}?:unavailable)
    rows+=SettingRow.ActionRow("refreshResources",get(com.openpets.android.R.string.ui_refresh),null)
    rows+=SettingRow.ActionRow("showResources",get(com.openpets.android.R.string.ui_show_on_pet),null)
    return rows
}
