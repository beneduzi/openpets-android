package com.openpets.android.settings.ui

import android.Manifest
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openpets.android.MainActivity
import com.openpets.android.R
import com.openpets.android.companion.CalendarController
import com.openpets.android.companion.CalendarFormat
import com.openpets.android.companion.DeviceEvent

private const val CALENDAR_PERMISSION_REQUEST=1002
class CalendarFragment:SettingsScreenFragment(),SettingsPermissionResultHandler{
    override val screenTitle="Calendar"
    private var controller:CalendarController?=null
    private var binding:ControllerBinding<List<DeviceEvent>>?=null
    private var observer:LifecycleEventObserver?=null
    private var events=emptyList<DeviceEvent>()
    private var permissionDenied=false
    override fun rows():List<SettingRow>{
        val c=controller
        val rows=mutableListOf<SettingRow>()
        if(c?.hasPermission()!=true){rows+=SettingRow.InfoRow("calendarPermission","Calendar permission",if(permissionDenied)"Permission denied. Grant access to retry." else "Calendar access is required to read upcoming events.");rows+=SettingRow.ActionRow("requestCalendarPermission",getString(R.string.calendar_grant),null)}
        else if(events.isEmpty())rows+=SettingRow.InfoRow("calendarEmpty","Upcoming events",getString(R.string.calendar_empty))
        else events.forEachIndexed{index,event->rows+=SettingRow.InfoRow("event:$index",event.title,CalendarFormat.relativeTime(event.beginMs,System.currentTimeMillis()))}
        rows+=SettingRow.ActionRow("refreshCalendar",getString(R.string.calendar_refresh),null)
        rows+=SettingRow.ActionRow("showCalendar",getString(R.string.calendar_show),null)
        rows+=SettingRow.SwitchRow("calendarReminder",getString(R.string.calendar_remind),null,c?.enabled()?:false)
        val ids=uniqueSettingRowIds(rows.mapIndexed{i,row->if(row is SettingRow.InfoRow&&row.key.startsWith("event:"))"event:${events.getOrNull(i)?.id}" else when(row){is SettingRow.InfoRow->row.key;is SettingRow.ActionRow->row.key;is SettingRow.SwitchRow->row.key;else->row.id.toString()}})
        return rows.mapIndexed{i,row->when(row){is SettingRow.InfoRow->row.copy(id=ids[i]);is SettingRow.ActionRow->row.copy(id=ids[i]);is SettingRow.SwitchRow->row.copy(id=ids[i]);else->row}}
    }
    override fun onViewCreated(view:android.view.View,state:Bundle?){super.onViewCreated(view,state);val c=(requireActivity() as MainActivity).calendarControllerForSettings()?:return;controller=c;events=c.upcoming()
        updateRowsIncrementally()
        binding=ControllerBinding(viewLifecycleOwner.lifecycle,{c.upcoming()},{c.addListener(it)},{c.removeListener(it)},{if(c.hasPermission())c.refresh()},render={events=it;(list?.adapter as? SettingsScreenAdapter)?.updateChangedRows(rows())})
        observer=LifecycleEventObserver{_,e->when(e){Lifecycle.Event.ON_START->binding?.start();Lifecycle.Event.ON_STOP->binding?.stop();else->Unit}};viewLifecycleOwner.lifecycle.addObserver(observer!!)
    }
    override fun onSwitchChanged(key:String,value:Boolean){if(key!="calendarReminder")return;controller?.setEnabled(value);(list?.adapter as? SettingsScreenAdapter)?.updateRowsByKey(rows(),key)}
    override fun onRowClicked(row:SettingRow){when((row as? SettingRow.ActionRow)?.key){
        "requestCalendarPermission"->requestCalendarPermission()
        "refreshCalendar"->{if(controller?.hasPermission()==true)controller?.refresh()else requestCalendarPermission()}
        "showCalendar"->{controller?.showOnPet();(requireActivity() as SettingsHost).revealStage()}
    }}
    private fun requestCalendarPermission(){
        val host=requireActivity() as SettingsHost
        AlertDialog.Builder(requireContext()).setTitle(R.string.calendar_title).setMessage(R.string.calendar_permission_rationale)
            .setPositiveButton(R.string.calendar_grant){_,_->host.requestSettingsPermission(Manifest.permission.READ_CALENDAR,CALENDAR_PERMISSION_REQUEST)}
            .setNegativeButton(R.string.dialog_close,null).show()
    }
    override fun onSettingsPermissionResult(requestCode:Int,granted:Boolean){if(requestCode!=CALENDAR_PERMISSION_REQUEST)return;permissionDenied=!granted;if(granted)controller?.refresh();updateRowsIncrementally()}
    override fun onDestroyView(){observer?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};observer=null;binding?.releaseView();binding=null;controller=null;super.onDestroyView()}
}
