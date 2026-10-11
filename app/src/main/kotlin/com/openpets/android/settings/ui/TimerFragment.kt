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
    override val screenTitle get()=getString(com.openpets.android.R.string.ui_timer)
    private var controller:TimerController?=null
    private var binding:ControllerBinding<TimerSnapshot>?=null
    private var observer:LifecycleEventObserver?=null
    private var snapshot=TimerSnapshot(TimerRunState.IDLE,0,0,null)
    private var customMinutes="";private var label=""
    private val handler=Handler(Looper.getMainLooper())
    private val ticker=object:Runnable{override fun run(){val c=controller?:return;renderSnapshot(c.snapshot());if(c.snapshot().state==TimerRunState.RUNNING)handler.postDelayed(this,1000L)}}
    override fun rows():List<SettingRow> = timerRows(snapshot,customMinutes,label,requireContext())
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
        "startTimer"->{val minutes=customMinutes.toIntOrNull();if(minutes==null||minutes<=0)Toast.makeText(requireContext(),com.openpets.android.R.string.ui_timer_duration_error,Toast.LENGTH_SHORT).show()else c.start(timerPresetMillis(minutes),label.trim().ifEmpty{null})}
        "pauseResume"->if(snapshot.state==TimerRunState.RUNNING)c.pause()else c.resume()
        "addFive"->c.addMinutes(5);"cancelTimer"->c.cancel();"dismissTimer"->c.dismissFinished()
    };renderSnapshot(c.snapshot());startTicker();(activity as? MainActivity)?.rearmSessionMaintenance()}
    override fun onStop(){handler.removeCallbacks(ticker);super.onStop()}
    override fun onDestroyView(){handler.removeCallbacksAndMessages(null);observer?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};observer=null;binding?.releaseView();binding=null;controller=null;super.onDestroyView()}
}

private fun timerRows(s:TimerSnapshot,minutes:String,label:String,context:android.content.Context):List<SettingRow> {
    fun text(id:Int)=context.getString(id)
    return when(s.state){
    TimerRunState.IDLE->listOf(SettingRow.ActionRow("preset5",context.resources.getQuantityString(com.openpets.android.R.plurals.ui_minutes_count,5,5),null),SettingRow.ActionRow("preset10",context.resources.getQuantityString(com.openpets.android.R.plurals.ui_minutes_count,10,10),null),SettingRow.ActionRow("preset25",context.resources.getQuantityString(com.openpets.android.R.plurals.ui_minutes_count,25,25),null),SettingRow.ActionRow("preset60",context.resources.getQuantityString(com.openpets.android.R.plurals.ui_minutes_count,60,60),null),SettingRow.TextFieldRow("customMinutes",text(com.openpets.android.R.string.ui_custom_minutes),minutes,text(com.openpets.android.R.string.ui_minutes),null,true),SettingRow.TextFieldRow("timerLabel",text(com.openpets.android.R.string.ui_label_optional),label,null,null,false),SettingRow.ActionRow("startTimer",text(com.openpets.android.R.string.ui_start),null))
    TimerRunState.RUNNING,TimerRunState.PAUSED->listOf(SettingRow.InfoRow("remaining",text(com.openpets.android.R.string.ui_remaining),TimerEngine.formatRemaining(s.remainingMs)),SettingRow.InfoRow("timerState",text(com.openpets.android.R.string.ui_state),text(if(s.state==TimerRunState.RUNNING)com.openpets.android.R.string.ui_running else com.openpets.android.R.string.ui_paused)),SettingRow.InfoRow("activeLabel",text(com.openpets.android.R.string.ui_label),s.label.orEmpty()),SettingRow.ActionRow("pauseResume",text(if(s.state==TimerRunState.RUNNING)com.openpets.android.R.string.ui_pause else com.openpets.android.R.string.ui_resume),null),SettingRow.ActionRow("addFive",text(com.openpets.android.R.string.ui_add_five),null),SettingRow.ActionRow("cancelTimer",text(com.openpets.android.R.string.ui_cancel_action),null))
    TimerRunState.FINISHED->listOf(SettingRow.InfoRow("finished",text(com.openpets.android.R.string.ui_timer),text(com.openpets.android.R.string.ui_finished)),SettingRow.InfoRow("activeLabel",text(com.openpets.android.R.string.ui_label),s.label.orEmpty()),SettingRow.ActionRow("dismissTimer",text(com.openpets.android.R.string.ui_dismiss),null))
    }
}
