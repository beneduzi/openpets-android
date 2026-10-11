package com.openpets.android.settings.ui

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openpets.android.MainActivity
import com.openpets.android.companion.ChecklistController
import com.openpets.android.companion.ChecklistItem
import com.openpets.android.settings.ui.SettingsHost

fun validChecklistItem(value:String)=value.trim().isNotEmpty()&&value.trim().length<=80

class ChecklistFragment:SettingsScreenFragment(){
    override val screenTitle get()=getString(com.openpets.android.R.string.settings_checklist_title)
    private var controller:ChecklistController?=null
    private var binding:ControllerBinding<List<ChecklistItem>>?=null
    private var observer:LifecycleEventObserver?=null
    private var items=emptyList<ChecklistItem>()
    private var itemDraft=""
    private var greetingDraft=""
    override fun rows():List<SettingRow> = checklistRows(items,itemDraft,greetingDraft,requireContext())
    override fun onViewCreated(view:android.view.View,state:Bundle?){super.onViewCreated(view,state);val host=requireActivity() as MainActivity;controller=host.checklistControllerForSettings();greetingDraft=host.checklistGreetingForSettings();val c=controller?:return;items=c.items()
        binding=ControllerBinding(viewLifecycleOwner.lifecycle,{c.items()},{c.addListener(it)},{c.removeListener(it)},{c.refresh()},render={items=it;(list?.adapter as? SettingsScreenAdapter)?.updateChangedRows(rows())})
        observer=LifecycleEventObserver{_,e->when(e){Lifecycle.Event.ON_START->binding?.start();Lifecycle.Event.ON_STOP->binding?.stop();else->Unit}};viewLifecycleOwner.lifecycle.addObserver(observer!!)
    }
    override fun onFieldChanged(key:String,value:String){when(key){"checklistItem"->itemDraft=value;"checklistGreeting"->greetingDraft=value};(list?.adapter as? SettingsScreenAdapter)?.setFieldValue(key,value)}
    override fun onRowClicked(row:SettingRow){val action=row as? SettingRow.ActionRow?:return;val c=controller?:return;val host=requireActivity() as MainActivity;when{
         action.key=="addChecklistItem"->{if(!validChecklistItem(itemDraft))Toast.makeText(requireContext(),com.openpets.android.R.string.ui_checklist_item_error,Toast.LENGTH_SHORT).show()else try{c.add(itemDraft.trim());itemDraft="";(list?.adapter as? SettingsScreenAdapter)?.setFieldValue("checklistItem","");list?.findViewWithTag<android.widget.EditText>("checklistItem")?.setText("");(list?.adapter as? SettingsScreenAdapter)?.updateChangedRows(checklistRows(c.items(),itemDraft,greetingDraft,requireContext()))}catch(e:Exception){Toast.makeText(requireContext(),e.message,Toast.LENGTH_SHORT).show()}}
        action.key.startsWith("toggle:")->c.toggle(action.key.removePrefix("toggle:"))
        action.key.startsWith("remove:")->c.remove(action.key.removePrefix("remove:"))
        action.key=="clearCompleted"->c.clearCompleted()
        action.key=="launchChecklist"->{host.persistChecklistGreetingForSettings(greetingDraft);c.launch();host.revealStage()}
    }}
    override fun onDestroyView(){(activity as? MainActivity)?.persistChecklistGreetingForSettings(greetingDraft);observer?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};observer=null;binding?.releaseView();binding=null;controller=null;super.onDestroyView()}
}

private fun checklistRows(items:List<ChecklistItem>,draft:String,greeting:String,context:android.content.Context):List<SettingRow>{
    fun get(id:Int)=context.getString(id)
    val rows=mutableListOf<SettingRow>(SettingRow.TextFieldRow("checklistItem",get(com.openpets.android.R.string.ui_item),draft,get(com.openpets.android.R.string.ui_checklist_hint),null,false),SettingRow.ActionRow("addChecklistItem",get(com.openpets.android.R.string.ui_add),null))
    if(items.isEmpty())rows+=SettingRow.InfoRow("checklistEmpty",get(com.openpets.android.R.string.ui_checklist),get(com.openpets.android.R.string.ui_no_items)) else items.forEach{item->rows+=SettingRow.InfoRow("item:${item.id}",item.label,get(if(item.done)com.openpets.android.R.string.settings_checklist_done else com.openpets.android.R.string.ui_pending));rows+=SettingRow.ActionRow("toggle:${item.id}",get(if(item.done)com.openpets.android.R.string.ui_undo else com.openpets.android.R.string.settings_checklist_done),null);rows+=SettingRow.ActionRow("remove:${item.id}",get(com.openpets.android.R.string.ui_remove),null)}
    rows+=SettingRow.TextFieldRow("checklistGreeting",get(com.openpets.android.R.string.ui_greeting),greeting,get(com.openpets.android.R.string.ui_greeting_hint),null,false)
    rows+=SettingRow.ActionRow("clearCompleted",get(com.openpets.android.R.string.ui_clear_completed),null)
    rows+=SettingRow.ActionRow("launchChecklist",get(com.openpets.android.R.string.ui_launch),null)
    val ids=uniqueSettingRowIds(rows.map{when(it){is SettingRow.InfoRow->it.key;is SettingRow.ActionRow->it.key;is SettingRow.TextFieldRow->it.key;else->it.id.toString()}})
    return rows.mapIndexed{i,row->when(row){is SettingRow.InfoRow->row.copy(id=ids[i]);is SettingRow.ActionRow->row.copy(id=ids[i]);is SettingRow.TextFieldRow->row.copy(id=ids[i]);else->row}}
}
