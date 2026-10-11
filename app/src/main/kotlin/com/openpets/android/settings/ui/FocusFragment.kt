package com.openpets.android.settings.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openpets.android.MainActivity
import com.openpets.android.companion.*

fun focusPreset(focusMinutes:Int,breakMinutes:Int)=focusMinutes to breakMinutes

class FocusFragment:SettingsScreenFragment(){
    override val screenTitle="Focus"
    private var controller:FocusController?=null
    private var binding:ControllerBinding<FocusSnapshot>?=null
    private var observer:LifecycleEventObserver?=null
    private var snapshot=FocusSnapshot(FocusState.IDLE,null,0,0,0)
    private var focusMinutes="25";private var breakMinutes="5"
    private val handler=Handler(Looper.getMainLooper())
    private val ticker=object:Runnable{override fun run(){val c=controller?:return;renderSnapshot(c.snapshot());if(c.snapshot().state==FocusState.FOCUS||c.snapshot().state==FocusState.BREAK)handler.postDelayed(this,1000L)}}
    override fun rows():List<SettingRow> = focusRows(snapshot,focusMinutes,breakMinutes,(activity as? MainActivity))
    override fun onViewCreated(view:android.view.View,state:Bundle?){super.onViewCreated(view,state);controller=(requireActivity() as MainActivity).focusControllerForSettings();val c=controller?:return;snapshot=c.snapshot()
        binding=ControllerBinding(viewLifecycleOwner.lifecycle,{c.snapshot()},{c.addListener(it)},{c.removeListener(it)},{c.refresh()},render={renderSnapshot(it)})
        observer=LifecycleEventObserver{_,e->when(e){Lifecycle.Event.ON_START->{binding?.start();startTicker()};Lifecycle.Event.ON_STOP->{binding?.stop();handler.removeCallbacks(ticker)};else->Unit}};viewLifecycleOwner.lifecycle.addObserver(observer!!)
    }
    private fun renderSnapshot(value:FocusSnapshot){snapshot=value;(list?.adapter as? SettingsScreenAdapter)?.updateChangedRows(rows())}
    private fun startTicker(){handler.removeCallbacks(ticker);if(shouldRunSessionTicker(controller?.snapshot()?.state==FocusState.FOCUS||controller?.snapshot()?.state==FocusState.BREAK))handler.post(ticker)}
    override fun onFieldChanged(key:String,value:String){when(key){"focusMinutes"->focusMinutes=value;"breakMinutes"->breakMinutes=value};(list?.adapter as? SettingsScreenAdapter)?.setFieldValue(key,value)}
    override fun onRowClicked(row:SettingRow){val c=controller?:return;val key=(row as? SettingRow.ActionRow)?.key?:return;when(key){
        "preset25"->c.start(focusPreset(25,5).first,focusPreset(25,5).second);"preset50"->c.start(focusPreset(50,10).first,focusPreset(50,10).second)
        "startFocus"->{val f=focusMinutes.toIntOrNull();val b=breakMinutes.toIntOrNull();if(f==null||f<=0||b==null||b<=0)Toast.makeText(requireContext(),"Enter positive focus and break durations",Toast.LENGTH_SHORT).show()else c.start(f,b)}
        "pauseFocus"->c.pause();"resumeFocus"->c.resume();"skipFocus"->c.skip();"endFocus"->c.end()
    };renderSnapshot(c.snapshot());startTicker();(activity as? MainActivity)?.rearmSessionMaintenance()}
    override fun onStop(){handler.removeCallbacks(ticker);super.onStop()}
    override fun onDestroyView(){handler.removeCallbacksAndMessages(null);observer?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};observer=null;binding?.releaseView();binding=null;controller=null;super.onDestroyView()}
}

private fun focusRows(s:FocusSnapshot,focus:String,breakTime:String,host:MainActivity?) = when(s.state){
    FocusState.IDLE->listOf(SettingRow.ActionRow("preset25","25/5",null),SettingRow.ActionRow("preset50","50/10",null),SettingRow.TextFieldRow("focusMinutes","Focus minutes",focus,"Minutes",null,true),SettingRow.TextFieldRow("breakMinutes","Break minutes",breakTime,"Minutes",null,true),SettingRow.ActionRow("startFocus","Start",null))
    FocusState.FOCUS,FocusState.BREAK,FocusState.PAUSED->{val label=host?.focusStatusLabelForSettings(s).orEmpty();buildList{add(SettingRow.InfoRow("focusRemaining","Remaining",TimerEngine.formatRemaining(s.phaseRemainingMs)));add(SettingRow.InfoRow("focusStatus","Status",label));if(s.state==FocusState.PAUSED)add(SettingRow.ActionRow("resumeFocus","Resume",null))else add(SettingRow.ActionRow("pauseFocus","Pause",null));if(s.state!=FocusState.PAUSED)add(SettingRow.ActionRow("skipFocus",host?.focusSkipLabelForSettings(s.state)?:if(s.state==FocusState.FOCUS)"Skip to break" else "Skip to focus",null));add(SettingRow.ActionRow("endFocus","End",null))}}
}
