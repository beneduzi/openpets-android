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
    override val screenTitle="Routine";override val transactional=true
    private val state:RoutineDraftViewModel by viewModels()
    private var controller:RoutineController?=null
    private var binding:ControllerBinding<RoutineSettings>?=null
    private var observer:LifecycleEventObserver?=null
    override fun rows():List<SettingRow>{val d=state.draft?:return emptyList();val live=state.live?:return emptyList();val next=RoutineEngine().nextFire(System.currentTimeMillis(),live)?.let{android.text.format.DateFormat.format("MMM d, HH:mm",it).toString()}?:"—";return listOf(
        SettingRow.SectionHeader("Morning"),SettingRow.SwitchRow("morningEnabled","Enabled",null,d.morningEnabled),
        SettingRow.TextFieldRow("morningHour","Hour",d.morningHour,null,state.errors["morningHour"]?.let(::routineError),true),SettingRow.TextFieldRow("morningMinute","Minute",d.morningMinute,null,state.errors["morningMinute"]?.let(::routineError),true),
        SettingRow.SectionHeader("Evening"),SettingRow.SwitchRow("eveningEnabled","Enabled",null,d.eveningEnabled),
        SettingRow.TextFieldRow("eveningHour","Hour",d.eveningHour,null,state.errors["eveningHour"]?.let(::routineError),true),SettingRow.TextFieldRow("eveningMinute","Minute",d.eveningMinute,null,state.errors["eveningMinute"]?.let(::routineError),true),
        SettingRow.InfoRow("nextFire","Next scheduled routine",next),
    )}
    override fun onViewCreated(view:android.view.View,stateBundle:Bundle?){super.onViewCreated(view,stateBundle);view.post{if(isAdded&&this.view===view)acquireForView()}}
    private fun acquireForView(){val c=reacquireViewDependency{(activity as? MainActivity)?.routineControllerForSettings()}?:return;controller=c;val settings=c.settings();if(state.draft==null)state.draft=settings.toDraft();state.live=settings;updateRowsIncrementally();binding?.releaseView();binding=ControllerBinding(viewLifecycleOwner.lifecycle,{c.settings()},{c.addListener(it)},{c.removeListener(it)},{c.refresh()},render={state.live=it;updateRowsIncrementally()});observer?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};observer=LifecycleEventObserver{_,e->when(e){Lifecycle.Event.ON_START->binding?.start();Lifecycle.Event.ON_STOP->binding?.stop();else->Unit}};viewLifecycleOwner.lifecycle.addObserver(observer!!)}
    override fun onFieldChanged(key:String,value:String){val d=state.draft?:return;when(key){"morningHour"->d.morningHour=value;"morningMinute"->d.morningMinute=value;"eveningHour"->d.eveningHour=value;"eveningMinute"->d.eveningMinute=value};state.errors=state.errors-key;val adapter=list?.adapter as? SettingsScreenAdapter;adapter?.setFieldValue(key,value);adapter?.updateRowsByKey(rows(),key)}
    override fun onSwitchChanged(key:String,value:Boolean){val d=state.draft?:return;when(key){"morningEnabled"->d.morningEnabled=value;"eveningEnabled"->d.eveningEnabled=value};(list?.adapter as? SettingsScreenAdapter)?.updateRowsByKey(rows(),key)}
    override fun onSave(){val d=state.draft?:return;state.errors=validateRoutineDraft(d);refreshRows();if(state.errors.isNotEmpty()){focusFirstError();return};val morningHour=(SettingsValidators.routineHour(d.morningHour) as SettingsValidators.Result.Ok).value;val morningMinute=(SettingsValidators.routineMinute(d.morningMinute) as SettingsValidators.Result.Ok).value;val eveningHour=(SettingsValidators.routineHour(d.eveningHour) as SettingsValidators.Result.Ok).value;val eveningMinute=(SettingsValidators.routineMinute(d.eveningMinute) as SettingsValidators.Result.Ok).value;controller?.update(d.morningEnabled,morningHour,morningMinute,d.eveningEnabled,eveningHour,eveningMinute);parentFragmentManager.popBackStack()}
    override fun onDiscard(){state.draft=controller?.settings()?.toDraft();state.errors=emptyMap();forceRebindRows()}
    override fun requestClose():Boolean{val d=state.draft?:return false;if(d==controller?.settings()?.toDraft())return false;androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Discard changes?").setMessage("Unsaved Routine settings will be discarded.").setPositiveButton("Discard"){_,_->onDiscard();parentFragmentManager.popBackStack()}.setNegativeButton("Keep editing",null).show();return true}
    private fun focusFirstError(){val key=state.errors.keys.firstOrNull()?:return;val rv=list?:return;val position=rows().indexOfFirst{it.id==key.hashCode().toLong()};if(position>=0)rv.post{if(!isAdded||view==null||rv!==list)return@post;rv.scrollToPosition(position);rv.postOnAnimation{if(!isAdded||view==null||rv!==list)return@postOnAnimation;rv.findViewHolderForAdapterPosition(position)?.itemView?.findViewWithTag<android.view.View>(key)?.requestFocus()}}}
    override fun onDestroyView(){observer?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};observer=null;binding?.releaseView();binding=null;controller=null;super.onDestroyView()}
}

fun routineDependenciesAvailable(controller:RoutineController?):Boolean=controller!=null

private fun RoutineSettings.toDraft()=RoutineDraft(morningEnabled,morningHour.toString(),morningMinute.toString(),eveningEnabled,eveningHour.toString(),eveningMinute.toString())
private fun routineError(error:SettingsValidators.Error)=when(error){SettingsValidators.Error.REQUIRED->"Required";SettingsValidators.Error.NOT_A_NUMBER->"Enter a number";SettingsValidators.Error.OUT_OF_RANGE->"Value is outside the allowed range";else->"Invalid value"}
