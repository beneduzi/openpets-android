package com.openpets.android.settings.ui

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openpets.android.MainActivity
import com.openpets.android.companion.Reminder
import com.openpets.android.companion.ReminderController

fun validReminderDraft(title:String,delay:String):Boolean=title.trim().isNotEmpty()&&title.trim().length<=120&&(delay.toIntOrNull()?.let{it>0}==true)
fun reminderDueText(dueAtMs:Long,now:Long):String{val delta=dueAtMs-now;return when{delta<=0L->"overdue";delta<3_600_000L->"in ${(delta+59_999L)/60_000L} min";delta<86_400_000L->"in ${(delta+3_599_999L)/3_600_000L} h";else->android.text.format.DateFormat.format("MMM d, HH:mm",dueAtMs).toString()}}

class RemindersFragment:SettingsScreenFragment(){
    override val screenTitle="Reminders"
    private var controller:ReminderController?=null
    private var binding:ControllerBinding<List<Reminder>>?=null
    private var observer:LifecycleEventObserver?=null
    private var reminders=emptyList<Reminder>()
    private var titleDraft="";private var delayDraft="10"
    override fun rows():List<SettingRow> = reminderRows(reminders,titleDraft,delayDraft)
    override fun onViewCreated(view:android.view.View,state:Bundle?){super.onViewCreated(view,state);controller=(requireActivity() as MainActivity).remindersControllerForSettings();val c=controller?:return;reminders=c.snapshot()
        binding=ControllerBinding(viewLifecycleOwner.lifecycle,{c.snapshot()},{c.addListener(it)},{c.removeListener(it)},{c.refresh()},render={reminders=it;(list?.adapter as? SettingsScreenAdapter)?.updateChangedRows(reminderRows(it,titleDraft,delayDraft))})
        observer=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_START->binding?.start();Lifecycle.Event.ON_STOP->binding?.stop();else->Unit}};viewLifecycleOwner.lifecycle.addObserver(observer!!)
    }
    override fun onFieldChanged(key:String,value:String){when(key){"reminderTitle"->titleDraft=value;"reminderDelay"->delayDraft=value};(list?.adapter as? SettingsScreenAdapter)?.setFieldValue(key,value)}
    override fun onRowClicked(row:SettingRow){val action=row as? SettingRow.ActionRow?:return;val c=controller?:return;when{
        action.key=="addReminder"->{if(!validReminderDraft(titleDraft,delayDraft)){val message=if(titleDraft.trim().length>120)getString(com.openpets.android.R.string.reminders_title_too_long)else getString(com.openpets.android.R.string.reminders_invalid);Toast.makeText(requireContext(),message,Toast.LENGTH_SHORT).show()}else{c.addInMinutes(titleDraft.trim(),delayDraft.toInt());titleDraft="";(list?.adapter as? SettingsScreenAdapter)?.setFieldValue("reminderTitle","");list?.findViewWithTag<android.widget.EditText>("reminderTitle")?.setText("");(list?.adapter as? SettingsScreenAdapter)?.updateChangedRows(reminderRows(c.snapshot(),titleDraft,delayDraft))}}
        action.key.startsWith("done:")->c.complete(action.key.removePrefix("done:"))
        action.key.startsWith("snooze:")->c.snooze(action.key.removePrefix("snooze:"),10)
        action.key.startsWith("delete:")->c.remove(action.key.removePrefix("delete:"))
    }}
    override fun onDestroyView(){observer?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};observer=null;binding?.releaseView();binding=null;controller=null;super.onDestroyView()}
}

private fun reminderRows(entries:List<Reminder>,title:String,delay:String):List<SettingRow>{
    val rows=mutableListOf<SettingRow>(SettingRow.TextFieldRow("reminderTitle","Title",title,"Reminder title",null,false),SettingRow.TextFieldRow("reminderDelay","In minutes",delay,"Minutes",null,true),SettingRow.ActionRow("addReminder","Add",null))
    if(entries.isEmpty())rows+=SettingRow.InfoRow("reminderEmpty","Reminders","No reminders") else entries.forEach{r->val due=reminderDueText(r.dueAtMs,System.currentTimeMillis());rows+=SettingRow.InfoRow("reminder:${r.id}",r.title,due,id=r.id.hashCode().toLong());rows+=SettingRow.ActionRow("done:${r.id}","Done",null,id="done:${r.id}".hashCode().toLong());rows+=SettingRow.ActionRow("snooze:${r.id}","Snooze 10 min",null,id="snooze:${r.id}".hashCode().toLong());rows+=SettingRow.ActionRow("delete:${r.id}","Delete",null,id="delete:${r.id}".hashCode().toLong())}
    val ids=uniqueSettingRowIds(rows.map{when(it){is SettingRow.InfoRow->it.key;is SettingRow.ActionRow->it.key;is SettingRow.TextFieldRow->it.key;else->it.id.toString()}})
    return rows.mapIndexed{i,row->when(row){is SettingRow.InfoRow->row.copy(id=ids[i]);is SettingRow.ActionRow->row.copy(id=ids[i]);is SettingRow.TextFieldRow->row.copy(id=ids[i]);else->row}}
}
