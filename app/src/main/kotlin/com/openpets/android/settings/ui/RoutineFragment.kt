package com.openpets.android.settings.ui

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import com.openpets.android.MainActivity
import com.openpets.android.companion.RoutineController
import com.openpets.android.companion.RoutineEngine
import com.openpets.android.companion.RoutineSettings
import com.openpets.android.settings.SettingsValidators

data class RoutineDraft(var morningEnabled:Boolean,var morningHour:String,var morningMinute:String,var eveningEnabled:Boolean,var eveningHour:String,var eveningMinute:String)
fun validateRoutineDraft(d:RoutineDraft):Map<String,SettingsValidators.Error>{
    val errors=linkedMapOf<String,SettingsValidators.Error>()
    listOf("morningHour" to SettingsValidators.routineHour(d.morningHour),"morningMinute" to SettingsValidators.routineMinute(d.morningMinute),"eveningHour" to SettingsValidators.routineHour(d.eveningHour),"eveningMinute" to SettingsValidators.routineMinute(d.eveningMinute)).forEach{(key,result)->if(result is SettingsValidators.Result.Invalid)errors[key]=result.error}
    return errors
}
class RoutineDraftViewModel:ViewModel(){var draft:RoutineDraft?=null;var errors:Map<String,SettingsValidators.Error> = emptyMap();var live:RoutineSettings?=null}

class RoutineFragment:SettingsScreenFragment(){
    override val screenTitle get()=getString(com.openpets.android.R.string.ui_routine);override val transactional=true
    private val state:RoutineDraftViewModel by viewModels()
    private var controller:RoutineController?=null
    private var binding:ControllerBinding<RoutineSettings>?=null
    private var observer:LifecycleEventObserver?=null
    override fun rows():List<SettingRow>{val d=state.draft?:return emptyList();val live=state.live?:return emptyList();val next=RoutineEngine().nextFire(System.currentTimeMillis(),live)?.let{android.text.format.DateFormat.format("MMM d, HH:mm",it).toString()}?:"—";return listOf(
        SettingRow.SectionHeader(getString(com.openpets.android.R.string.ui_morning)),SettingRow.SwitchRow("morningEnabled",getString(com.openpets.android.R.string.ui_enabled),null,d.morningEnabled),
        SettingRow.TextFieldRow("morningHour",getString(com.openpets.android.R.string.ui_hour),d.morningHour,null,state.errors["morningHour"]?.let{routineError(it,requireContext())},true),SettingRow.TextFieldRow("morningMinute",getString(com.openpets.android.R.string.ui_minute),d.morningMinute,null,state.errors["morningMinute"]?.let{routineError(it,requireContext())},true),
        SettingRow.SectionHeader(getString(com.openpets.android.R.string.ui_evening)),SettingRow.SwitchRow("eveningEnabled",getString(com.openpets.android.R.string.ui_enabled),null,d.eveningEnabled),
        SettingRow.TextFieldRow("eveningHour",getString(com.openpets.android.R.string.ui_hour),d.eveningHour,null,state.errors["eveningHour"]?.let{routineError(it,requireContext())},true),SettingRow.TextFieldRow("eveningMinute",getString(com.openpets.android.R.string.ui_minute),d.eveningMinute,null,state.errors["eveningMinute"]?.let{routineError(it,requireContext())},true),
        SettingRow.InfoRow("nextFire",getString(com.openpets.android.R.string.ui_next_routine),next),
        sectionResetRow(),
    )}
    override fun onViewCreated(view:android.view.View,stateBundle:Bundle?){super.onViewCreated(view,stateBundle);view.post{if(isAdded&&this.view===view)acquireForView()}}
    private fun acquireForView(){val c=reacquireViewDependency{(activity as? MainActivity)?.routineControllerForSettings()}?:return;controller=c;val settings=c.settings();if(state.draft==null)state.draft=settings.toDraft();state.live=settings;updateRowsIncrementally();binding?.releaseView();binding=ControllerBinding(viewLifecycleOwner.lifecycle,{c.settings()},{c.addListener(it)},{c.removeListener(it)},{c.refresh()},render={state.live=it;updateRowsIncrementally()});observer?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};observer=LifecycleEventObserver{_,e->when(e){Lifecycle.Event.ON_START->binding?.start();Lifecycle.Event.ON_STOP->binding?.stop();else->Unit}};viewLifecycleOwner.lifecycle.addObserver(observer!!)}
    override fun onFieldChanged(key:String,value:String){val d=state.draft?:return;when(key){"morningHour"->d.morningHour=value;"morningMinute"->d.morningMinute=value;"eveningHour"->d.eveningHour=value;"eveningMinute"->d.eveningMinute=value};state.errors=state.errors-key;val adapter=list?.adapter as? SettingsScreenAdapter;adapter?.setFieldValue(key,value);adapter?.updateRowsByKey(rows(),key)}
    override fun onSwitchChanged(key:String,value:Boolean){val d=state.draft?:return;when(key){"morningEnabled"->d.morningEnabled=value;"eveningEnabled"->d.eveningEnabled=value};(list?.adapter as? SettingsScreenAdapter)?.updateRowsByKey(rows(),key)}
    override fun onSave(){val d=state.draft?:return;state.errors=validateRoutineDraft(d);refreshRows();if(state.errors.isNotEmpty()){focusFirstError();return};val morningHour=(SettingsValidators.routineHour(d.morningHour) as SettingsValidators.Result.Ok).value;val morningMinute=(SettingsValidators.routineMinute(d.morningMinute) as SettingsValidators.Result.Ok).value;val eveningHour=(SettingsValidators.routineHour(d.eveningHour) as SettingsValidators.Result.Ok).value;val eveningMinute=(SettingsValidators.routineMinute(d.eveningMinute) as SettingsValidators.Result.Ok).value;controller?.update(d.morningEnabled,morningHour,morningMinute,d.eveningEnabled,eveningHour,eveningMinute);parentFragmentManager.popBackStack()}
    override fun onDiscard(){state.draft=controller?.settings()?.toDraft();state.errors=emptyMap();forceRebindRows()}
    override fun sectionResetDraft(){state.draft=RoutineDraft(true,"9","0",true,"20","0");state.errors=emptyMap();forceRebindRows()}
    override fun requestClose():Boolean{val d=state.draft?:return false;if(d==controller?.settings()?.toDraft())return false;androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle(com.openpets.android.R.string.ui_discard_title).setMessage(com.openpets.android.R.string.ui_unsaved_routine).setPositiveButton(com.openpets.android.R.string.ui_discard){_,_->onDiscard();parentFragmentManager.popBackStack()}.setNegativeButton(com.openpets.android.R.string.ui_keep_editing,null).show();return true}
    private fun focusFirstError(){val key=state.errors.keys.firstOrNull()?:return;val rv=list?:return;val position=rows().indexOfFirst{it.id==key.hashCode().toLong()};if(position>=0)rv.post{if(!isAdded||view==null||rv!==list)return@post;rv.scrollToPosition(position);rv.postOnAnimation{if(!isAdded||view==null||rv!==list)return@postOnAnimation;rv.findViewHolderForAdapterPosition(position)?.itemView?.findViewWithTag<android.view.View>(key)?.requestFocus()}}}
    override fun onDestroyView(){observer?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};observer=null;binding?.releaseView();binding=null;controller=null;super.onDestroyView()}
}

fun routineDependenciesAvailable(controller:RoutineController?):Boolean=controller!=null

private fun RoutineSettings.toDraft()=RoutineDraft(morningEnabled,morningHour.toString(),morningMinute.toString(),eveningEnabled,eveningHour.toString(),eveningMinute.toString())
private fun routineError(error:SettingsValidators.Error,context:android.content.Context)=context.getString(when(error){SettingsValidators.Error.REQUIRED->com.openpets.android.R.string.settings_required;SettingsValidators.Error.NOT_A_NUMBER->com.openpets.android.R.string.settings_enter_number;SettingsValidators.Error.OUT_OF_RANGE->com.openpets.android.R.string.settings_range_error;else->com.openpets.android.R.string.settings_invalid_value})
