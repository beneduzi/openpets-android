package com.openpets.android.settings.ui

import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openpets.android.MainActivity
import com.openpets.android.companion.*

fun timerPresetMillis(minutes:Int):Long=minutes*60_000L

class TimerFragment:SettingsScreenFragment(){
    override val screenTitle="Timer"
    private var controller:TimerController?=null
    private var binding:ControllerBinding<TimerSnapshot>?=null
    private var observer:LifecycleEventObserver?=null
    private var snapshot=TimerSnapshot(TimerRunState.IDLE,0,0,null)
    private var customMinutes="";private var label=""
    private val handler=Handler(Looper.getMainLooper())
    private val ticker=object:Runnable{override fun run(){val c=controller?:return;renderSnapshot(c.snapshot());if(c.snapshot().state==TimerRunState.RUNNING)handler.postDelayed(this,1000L)}}
    override fun rows():List<SettingRow> = timerRows(snapshot,customMinutes,label)
    override fun onViewCreated(view:android.view.View,state:Bundle?){super.onViewCreated(view,state);controller=(requireActivity() as MainActivity).timerControllerForSettings();val c=controller?:return
        snapshot=c.snapshot()
        binding=ControllerBinding(viewLifecycleOwner.lifecycle,{c.snapshot()},{c.addListener(it)},{c.removeListener(it)},{c.refresh()},render={renderSnapshot(it)})
        observer=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_START->{binding?.start();startTicker()};Lifecycle.Event.ON_STOP->{binding?.stop();handler.removeCallbacks(ticker)};else->Unit}};viewLifecycleOwner.lifecycle.addObserver(observer!!)
    }
    private fun renderSnapshot(value:TimerSnapshot){snapshot=value;(list?.adapter as? SettingsScreenAdapter)?.updateChangedRows(rows())}
    private fun startTicker(){handler.removeCallbacks(ticker);if(shouldRunSessionTicker(controller?.snapshot()?.state==TimerRunState.RUNNING))handler.post(ticker)}
    override fun onFieldChanged(key:String,value:String){when(key){"customMinutes"->customMinutes=value;"timerLabel"->label=value};(list?.adapter as? SettingsScreenAdapter)?.setFieldValue(key,value)}
    override fun onRowClicked(row:SettingRow){val c=controller?:return;val key=(row as? SettingRow.ActionRow)?.key?:return;when(key){
        "preset5"->c.start(timerPresetMillis(5));"preset10"->c.start(timerPresetMillis(10));"preset25"->c.start(timerPresetMillis(25));"preset60"->c.start(timerPresetMillis(60))
        "startTimer"->{val minutes=customMinutes.toIntOrNull();if(minutes==null||minutes<=0)Toast.makeText(requireContext(),"Enter timer duration in minutes",Toast.LENGTH_SHORT).show()else c.start(timerPresetMillis(minutes),label.trim().ifEmpty{null})}
        "pauseResume"->if(snapshot.state==TimerRunState.RUNNING)c.pause()else c.resume()
        "addFive"->c.addMinutes(5);"cancelTimer"->c.cancel();"dismissTimer"->c.dismissFinished()
    };renderSnapshot(c.snapshot());startTicker();(activity as? MainActivity)?.rearmSessionMaintenance()}
    override fun onStop(){handler.removeCallbacks(ticker);super.onStop()}
    override fun onDestroyView(){handler.removeCallbacksAndMessages(null);observer?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};observer=null;binding?.releaseView();binding=null;controller=null;super.onDestroyView()}
}

private fun timerRows(s:TimerSnapshot,minutes:String,label:String):List<SettingRow> = when(s.state){
    TimerRunState.IDLE->listOf(SettingRow.ActionRow("preset5","5 min",null),SettingRow.ActionRow("preset10","10 min",null),SettingRow.ActionRow("preset25","25 min",null),SettingRow.ActionRow("preset60","60 min",null),SettingRow.TextFieldRow("customMinutes","Custom minutes",minutes,"Minutes",null,true),SettingRow.TextFieldRow("timerLabel","Label (optional)",label,null,null,false),SettingRow.ActionRow("startTimer","Start",null))
    TimerRunState.RUNNING,TimerRunState.PAUSED->listOf(SettingRow.InfoRow("remaining","Remaining",TimerEngine.formatRemaining(s.remainingMs)),SettingRow.InfoRow("timerState","State",if(s.state==TimerRunState.RUNNING)"Running" else "Paused"),SettingRow.InfoRow("activeLabel","Label",s.label.orEmpty()),SettingRow.ActionRow("pauseResume",if(s.state==TimerRunState.RUNNING)"Pause" else "Resume",null),SettingRow.ActionRow("addFive","+5 minutes",null),SettingRow.ActionRow("cancelTimer","Cancel",null))
    TimerRunState.FINISHED->listOf(SettingRow.InfoRow("finished","Timer","Finished"),SettingRow.InfoRow("activeLabel","Label",s.label.orEmpty()),SettingRow.ActionRow("dismissTimer","Dismiss",null))
}
