package com.openpets.android.settings.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openpets.android.MainActivity
import com.openpets.android.companion.Deadline
import com.openpets.android.companion.DeadlineController
import java.util.Calendar

fun validDeadlineTitle(title:String)=title.trim().length in 1..120
fun deadlineRelativeText(dueAtMs:Long,now:Long):String{val delta=dueAtMs-now;if(delta<0)return "overdue";val min=delta/60_000L;return when{min>=1440->"${min/1440} day";min>=60->"${min/60} h";else->"$min min"}}

class DeadlinesFragment:SettingsScreenFragment(){
    override val screenTitle get()=getString(com.openpets.android.R.string.settings_deadlines_title)
    private var controller:DeadlineController?=null
    private var binding:ControllerBinding<List<Deadline>>?=null
    private var observer:LifecycleEventObserver?=null
    private var entries=emptyList<Deadline>()
    private var titleDraft=""
    private var due=Calendar.getInstance().apply{add(Calendar.HOUR,1)}
    override fun rows():List<SettingRow> = deadlineRows(entries,titleDraft,due.timeInMillis,requireContext())
    override fun onViewCreated(view:android.view.View,state:Bundle?){super.onViewCreated(view,state);val c=(requireActivity() as MainActivity).deadlinesControllerForSettings();controller=c;entries=c.upcoming()
        binding=ControllerBinding(viewLifecycleOwner.lifecycle,{c.upcoming()},{c.addListener(it)},{c.removeListener(it)},{c.refresh()},render={entries=it;(list?.adapter as? SettingsScreenAdapter)?.updateChangedRows(rows())})
        observer=LifecycleEventObserver{_,e->when(e){Lifecycle.Event.ON_START->binding?.start();Lifecycle.Event.ON_STOP->binding?.stop();else->Unit}};viewLifecycleOwner.lifecycle.addObserver(observer!!)
    }
    override fun onFieldChanged(key:String,value:String){if(key=="deadlineTitle"){titleDraft=value;(list?.adapter as? SettingsScreenAdapter)?.setFieldValue(key,value)}}
    override fun onRowClicked(row:SettingRow){val action=row as? SettingRow.ActionRow?:return;val c=controller?:return;when{
        action.key=="pickDate"->{val rv=list?:return;DatePickerDialog(requireContext(),{_,year,month,day->if(!isAdded||view==null||rv!==list)return@DatePickerDialog;due.set(Calendar.YEAR,year);due.set(Calendar.MONTH,month);due.set(Calendar.DAY_OF_MONTH,day);(rv.adapter as? SettingsScreenAdapter)?.updateChangedRows(rows())},due.get(Calendar.YEAR),due.get(Calendar.MONTH),due.get(Calendar.DAY_OF_MONTH)).show()}
        action.key=="pickTime"->{val rv=list?:return;TimePickerDialog(requireContext(),{_,hour,minute->if(!isAdded||view==null||rv!==list)return@TimePickerDialog;due.set(Calendar.HOUR_OF_DAY,hour);due.set(Calendar.MINUTE,minute);(rv.adapter as? SettingsScreenAdapter)?.updateChangedRows(rows())},due.get(Calendar.HOUR_OF_DAY),due.get(Calendar.MINUTE),DateFormat.is24HourFormat(requireContext())).show()}
        action.key=="addDeadline"->{if(!validDeadlineTitle(titleDraft))Toast.makeText(requireContext(),com.openpets.android.R.string.ui_deadline_title_error,Toast.LENGTH_SHORT).show()else try{c.add(titleDraft.trim(),due.timeInMillis);titleDraft="";(list?.adapter as? SettingsScreenAdapter)?.setFieldValue("deadlineTitle","");list?.findViewWithTag<android.widget.EditText>("deadlineTitle")?.setText("");(list?.adapter as? SettingsScreenAdapter)?.updateChangedRows(deadlineRows(c.upcoming(),titleDraft,due.timeInMillis,requireContext()))}catch(e:Exception){Toast.makeText(requireContext(),e.message,Toast.LENGTH_SHORT).show()}}
        action.key.startsWith("complete:")->c.complete(action.key.removePrefix("complete:"))
        action.key.startsWith("delete:")->c.remove(action.key.removePrefix("delete:"))
    }}
    override fun onDestroyView(){observer?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};observer=null;binding?.releaseView();binding=null;controller=null;super.onDestroyView()}
}

private fun deadlineRows(entries:List<Deadline>,title:String,dueAt:Long,context:android.content.Context):List<SettingRow>{
    val date=DateFormat.format("MMM d, yyyy",dueAt).toString();val time=DateFormat.format("HH:mm",dueAt).toString()
    val rows=mutableListOf<SettingRow>(SettingRow.TextFieldRow("deadlineTitle",context.getString(com.openpets.android.R.string.ui_title),title,context.getString(com.openpets.android.R.string.settings_deadline_title_hint),null,false),SettingRow.ActionRow("pickDate",context.getString(com.openpets.android.R.string.settings_deadline_date),date),SettingRow.ActionRow("pickTime",context.getString(com.openpets.android.R.string.settings_deadline_time),time),SettingRow.ActionRow("addDeadline",context.getString(com.openpets.android.R.string.ui_add),null))
    rows+=SettingRow.InfoRow("nearestDeadline",context.getString(com.openpets.android.R.string.settings_deadline_nearest),entries.firstOrNull()?.let{"${it.title} — ${deadlineRelativeText(it.dueAtMs,System.currentTimeMillis())}"}?:context.getString(com.openpets.android.R.string.settings_deadlines_empty))
    entries.forEach{d->rows+=SettingRow.InfoRow("deadline:${d.id}",d.title,deadlineRelativeText(d.dueAtMs,System.currentTimeMillis()));rows+=SettingRow.ActionRow("complete:${d.id}",context.getString(com.openpets.android.R.string.settings_deadline_complete),null);rows+=SettingRow.ActionRow("delete:${d.id}",context.getString(com.openpets.android.R.string.ui_delete),null)}
    val ids=uniqueSettingRowIds(rows.map{when(it){is SettingRow.InfoRow->it.key;is SettingRow.ActionRow->it.key;is SettingRow.TextFieldRow->it.key;else->it.id.toString()}})
    return rows.mapIndexed{i,row->when(row){is SettingRow.InfoRow->row.copy(id=ids[i]);is SettingRow.ActionRow->row.copy(id=ids[i]);is SettingRow.TextFieldRow->row.copy(id=ids[i]);else->row}}
}
