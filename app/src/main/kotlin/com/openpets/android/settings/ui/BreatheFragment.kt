package com.openpets.android.settings.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openpets.android.MainActivity
import com.openpets.android.companion.*

class BreatheFragment:SettingsScreenFragment(){
    override val screenTitle="Breathe"
    private var controller:BreathingController?=null
    private var binding:ControllerBinding<BreathSnapshot>?=null
    private var observer:LifecycleEventObserver?=null
    private var snapshot=BreathSnapshot(false,null,BreathPhase.DONE,0,0,0)
    private var minutes="3"
    private val handler=Handler(Looper.getMainLooper())
    private val ticker=object:Runnable{override fun run(){val c=controller?:return;renderSnapshot(c.snapshot());if(c.snapshot().active)handler.postDelayed(this,1000L)}}
    override fun rows():List<SettingRow> = breatheRows(snapshot,minutes,(activity as? MainActivity))
    override fun onViewCreated(view:android.view.View,state:Bundle?){super.onViewCreated(view,state);controller=(requireActivity() as MainActivity).breathingControllerForSettings();val c=controller?:return;snapshot=c.snapshot()
        binding=ControllerBinding(viewLifecycleOwner.lifecycle,{c.snapshot()},{c.addListener(it)},{c.removeListener(it)},{c.refresh()},render={renderSnapshot(it)})
        observer=LifecycleEventObserver{_,e->when(e){Lifecycle.Event.ON_START->{binding?.start();startTicker()};Lifecycle.Event.ON_STOP->{binding?.stop();handler.removeCallbacks(ticker)};else->Unit}};viewLifecycleOwner.lifecycle.addObserver(observer!!)
    }
    private fun renderSnapshot(value:BreathSnapshot){snapshot=value;(list?.adapter as? SettingsScreenAdapter)?.updateChangedRows(rows())}
    private fun startTicker(){handler.removeCallbacks(ticker);if(shouldRunSessionTicker(controller?.snapshot()?.active==true))handler.post(ticker)}
    override fun onFieldChanged(key:String,value:String){if(key=="breatheMinutes"){minutes=value;(list?.adapter as? SettingsScreenAdapter)?.setFieldValue(key,value)}}
    override fun onRowClicked(row:SettingRow){val c=controller?:return;val key=(row as? SettingRow.ActionRow)?.key?:return;when(key){
        "startBox","start478"->{val duration=minutes.toIntOrNull();if(duration==null||duration<=0)Toast.makeText(requireContext(),"Enter a positive duration",Toast.LENGTH_SHORT).show()else c.start(if(key=="startBox")BreathingPattern.BOX.id else BreathingPattern.RELAX_478.id,duration)}
        "stopBreathe"->c.stop()
    };renderSnapshot(c.snapshot());startTicker();(activity as? MainActivity)?.rearmSessionMaintenance()}
    override fun onStop(){handler.removeCallbacks(ticker);super.onStop()}
    override fun onDestroyView(){handler.removeCallbacksAndMessages(null);observer?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};observer=null;binding?.releaseView();binding=null;controller=null;super.onDestroyView()}
}

private fun breatheRows(s:BreathSnapshot,minutes:String,host:MainActivity?):List<SettingRow> = if(s.active)listOf(
    SettingRow.InfoRow("breathPhase","Phase","${host?.breathingPhaseLabelForSettings(s.phase)?:s.phase.name} · ${s.phaseRemainingSec}s"),
    SettingRow.InfoRow("breathCycles","Cycles completed",s.cyclesCompleted.toString()),
    SettingRow.InfoRow("breathRemaining","Session remaining",TimerEngine.formatRemaining(s.sessionRemainingMs)),
    SettingRow.ActionRow("stopBreathe","Stop",null),
) else listOf(
    SettingRow.TextFieldRow("breatheMinutes","Duration (minutes)",minutes,"Minutes",null,true),
    SettingRow.ActionRow("startBox","Box",null),SettingRow.ActionRow("start478","4-7-8",null),
)
