package com.openpets.android.settings.ui

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import com.openpets.android.MainActivity
import com.openpets.android.companion.WaterEngine
import com.openpets.android.companion.WaterSettings
import com.openpets.android.companion.WaterController
import com.openpets.android.settings.SettingsValidators

data class WaterDraft(var enabled:Boolean,var interval:String,var from:String,var to:String)
fun validateWaterDraft(d:WaterDraft):Map<String,SettingsValidators.Error>{
    val errors=linkedMapOf<String,SettingsValidators.Error>()
    val interval=(SettingsValidators.waterInterval(d.interval) as? SettingsValidators.Result.Ok)?.value
    (SettingsValidators.waterInterval(d.interval) as? SettingsValidators.Result.Invalid)?.let{errors["interval"]=it.error}
    val from=(SettingsValidators.waterHour(d.from) as? SettingsValidators.Result.Ok)?.value
    (SettingsValidators.waterHour(d.from) as? SettingsValidators.Result.Invalid)?.let{errors["fromHour"]=it.error}
    val to=(SettingsValidators.waterHour(d.to) as? SettingsValidators.Result.Ok)?.value
    (SettingsValidators.waterHour(d.to) as? SettingsValidators.Result.Invalid)?.let{errors["toHour"]=it.error}
    if(from!=null&&to!=null)(SettingsValidators.waterWindow(from,to) as? SettingsValidators.Result.Invalid)?.let{errors["toHour"]=it.error}
    return errors
}
class WaterDraftViewModel:ViewModel(){var draft:WaterDraft?=null;var errors:Map<String,SettingsValidators.Error> = emptyMap();var live:WaterSettings?=null}

class WaterFragment:SettingsScreenFragment(){
    override val screenTitle="Water reminder";override val transactional=true
    private val state:WaterDraftViewModel by viewModels()
    private var controller:WaterController?=null
    private var binding:ControllerBinding<WaterSettings>?=null
    private var observer:LifecycleEventObserver?=null
    override fun rows():List<SettingRow>{val d=state.draft?:return emptyList();val live=state.live?:return emptyList();val next=WaterEngine().nextFire(System.currentTimeMillis(),live)?.let{android.text.format.DateFormat.format("MMM d, HH:mm",it).toString()}?:"—";return listOf(
        SettingRow.SwitchRow("enabled","Enabled",null,d.enabled),
        SettingRow.TextFieldRow("interval","Interval (minutes)",d.interval,null,state.errors["interval"]?.let(::waterError),true),
        SettingRow.TextFieldRow("fromHour","From hour",d.from,null,state.errors["fromHour"]?.let(::waterError),true),
        SettingRow.TextFieldRow("toHour","To hour",d.to,null,state.errors["toHour"]?.let(::waterError),true),
        SettingRow.InfoRow("nextFire","Next scheduled fire",next),
    )}
    override fun onViewCreated(view:android.view.View,stateBundle:Bundle?){super.onViewCreated(view,stateBundle);view.post{if(isAdded&&this.view===view)acquireForView()}}
    private fun acquireForView(){val c=reacquireViewDependency{(activity as? MainActivity)?.waterControllerForSettings()}?:return;controller=c;val settings=c.settings();if(state.draft==null)state.draft=settings.toDraft();state.live=settings;updateRowsIncrementally();binding?.releaseView();binding=ControllerBinding(viewLifecycleOwner.lifecycle,{c.settings()},{c.addListener(it)},{c.removeListener(it)},{c.refresh()},render={state.live=it;updateRowsIncrementally()});observer?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};observer=LifecycleEventObserver{_,e->when(e){Lifecycle.Event.ON_START->binding?.start();Lifecycle.Event.ON_STOP->binding?.stop();else->Unit}};viewLifecycleOwner.lifecycle.addObserver(observer!!)}
    override fun onFieldChanged(key:String,value:String){val d=state.draft?:return;when(key){"interval"->d.interval=value;"fromHour"->d.from=value;"toHour"->d.to=value};state.errors=state.errors-key;val adapter=list?.adapter as? SettingsScreenAdapter;adapter?.setFieldValue(key,value);adapter?.updateRowsByKey(rows(),key)}
    override fun onSwitchChanged(key:String,value:Boolean){if(key!="enabled")return;state.draft?.enabled=value;(list?.adapter as? SettingsScreenAdapter)?.updateRowsByKey(rows(),key)}
    override fun onSave(){val d=state.draft?:return;state.errors=validateWaterDraft(d);refreshRows();if(state.errors.isNotEmpty()){focusFirstError();return};val interval=(SettingsValidators.waterInterval(d.interval) as SettingsValidators.Result.Ok).value;val from=(SettingsValidators.waterHour(d.from) as SettingsValidators.Result.Ok).value;val to=(SettingsValidators.waterHour(d.to) as SettingsValidators.Result.Ok).value;controller?.update(d.enabled,interval,from,to);parentFragmentManager.popBackStack()}
    override fun onDiscard(){state.draft=controller?.settings()?.toDraft();state.errors=emptyMap();forceRebindRows()}
    override fun requestClose():Boolean{val d=state.draft?:return false;if(d==controller?.settings()?.toDraft())return false;androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Discard changes?").setMessage("Unsaved Water reminder settings will be discarded.").setPositiveButton("Discard"){_,_->onDiscard();parentFragmentManager.popBackStack()}.setNegativeButton("Keep editing",null).show();return true}
    private fun focusFirstError(){val key=state.errors.keys.firstOrNull()?:return;val rv=list?:return;val position=rows().indexOfFirst{it.id==key.hashCode().toLong()};if(position>=0)rv.post{if(!isAdded||view==null||rv!==list)return@post;rv.scrollToPosition(position);rv.postOnAnimation{if(!isAdded||view==null||rv!==list)return@postOnAnimation;rv.findViewHolderForAdapterPosition(position)?.itemView?.findViewWithTag<android.view.View>(key)?.requestFocus()}}}
    override fun onDestroyView(){observer?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};observer=null;binding?.releaseView();binding=null;controller=null;super.onDestroyView()}
}

fun waterDependenciesAvailable(controller:WaterController?):Boolean=controller!=null

private fun WaterSettings.toDraft()=WaterDraft(enabled,intervalMinutes.toString(),fromHour.toString(),toHour.toString())
private fun waterError(error:SettingsValidators.Error)=when(error){SettingsValidators.Error.REQUIRED->"Required";SettingsValidators.Error.NOT_A_NUMBER->"Enter a number";SettingsValidators.Error.OUT_OF_RANGE->"Value is outside the allowed range";SettingsValidators.Error.START_EQUALS_END->"From and to hours must differ";else->"Invalid value"}
