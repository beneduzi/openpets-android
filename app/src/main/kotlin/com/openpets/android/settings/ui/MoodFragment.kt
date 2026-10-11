package com.openpets.android.settings.ui

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openpets.android.MainActivity
import com.openpets.android.companion.MoodController
import com.openpets.android.companion.MoodEntry
import com.openpets.android.settings.ui.SettingsHost

class MoodFragment:SettingsScreenFragment(){
    override val screenTitle="Mood"
    private var controller:MoodController?=null
    private var binding:ControllerBinding<List<MoodEntry>>?=null
    private var observer:LifecycleEventObserver?=null
    private var latest=emptyList<MoodEntry>()
    private var note=""
    override fun rows():List<SettingRow> = moodRows(latest,controller,note,(1..5).map{(activity as? MainActivity)?.moodRatingLabelForSettings(it)?:"$it/5"})
    override fun onViewCreated(view:android.view.View,state:Bundle?){super.onViewCreated(view,state);controller=(requireActivity() as MainActivity).moodControllerForSettings();val c=controller?:return
        latest=c.recent(200)
        binding=ControllerBinding(viewLifecycleOwner.lifecycle,{c.recent(200)},{c.addListener(it)},{c.removeListener(it)},{c.refresh()},render={latest=it;(list!!.adapter as? SettingsScreenAdapter)?.updateChangedRows(moodRows(it,c,note,(1..5).map{n->(activity as? MainActivity)?.moodRatingLabelForSettings(n)?:"$n/5"}))})
        c.showOnPet()
        observer=LifecycleEventObserver{_,e->when(e){Lifecycle.Event.ON_START->binding?.start();Lifecycle.Event.ON_STOP->binding?.stop();else->Unit}};viewLifecycleOwner.lifecycle.addObserver(observer!!)
    }
    override fun onFieldChanged(key:String,value:String){if(key=="moodNote"){note=value;(list!!.adapter as? SettingsScreenAdapter)?.setFieldValue(key,value)}}
    override fun onRowClicked(row:SettingRow){val c=controller?:return;val action=row as? SettingRow.ActionRow?:return;when(action.key){
        "rate1","rate2","rate3","rate4","rate5"->{c.record(action.key.last().digitToInt(),note);c.showOnPet();(list!!.adapter as? SettingsScreenAdapter)?.updateChangedRows(moodRows(c.recent(200),c,note,(1..5).map{n->(activity as? MainActivity)?.moodRatingLabelForSettings(n)?:"$n/5"}))}
        "nudge"->{c.nudge();(requireActivity() as SettingsHost).revealStage()}
        "showMood"->{c.showOnPet();(requireActivity() as SettingsHost).revealStage()}
    }}
    override fun onDestroyView(){observer?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};observer=null;binding?.releaseView();binding=null;controller=null;super.onDestroyView()}
}

private fun moodRows(entries:List<MoodEntry>,controller:MoodController?,note:String,labels:List<String>):List<SettingRow>{
    val latest=entries.firstOrNull();val average=controller?.average(7)
    val rows= mutableListOf<SettingRow>(SettingRow.TextFieldRow("moodNote","Note",note,"Add a note",null,false))
    (1..5).forEach{rows+=SettingRow.ActionRow("rate$it",labels[it-1],null)}
    rows+=SettingRow.InfoRow("latestMood","Latest",latest?.let{"${labels[it.value-1]}${it.note?.let{n->" · $n"}?:""} · ${moodRelative(it.atMs)}"}?:"No mood recorded")
    rows+=SettingRow.InfoRow("averageMood","7-day average",average?.let{"%.1f".format(it)}?:"—")
    if(entries.isEmpty())rows+=SettingRow.InfoRow("moodHistoryEmpty","History","No mood history") else {
        val ids=moodRowIds(entries)
        entries.forEachIndexed{i,e->rows+=SettingRow.InfoRow("moodHistory:$i","${e.value}/5",listOfNotNull(e.note,moodRelative(e.atMs)).joinToString(" · "),id=ids[i])}
    }
    rows+=SettingRow.ActionRow("nudge","Nudge",null);rows+=SettingRow.ActionRow("showMood","Show on pet",null)
    return rows
}
fun moodRowIds(entries:List<MoodEntry>):List<Long>{
    val occurrences=mutableMapOf<Long,Int>()
    val used=mutableSetOf<Long>()
    return entries.map{entry->
        val ordinal=occurrences.getOrDefault(entry.atMs,0);occurrences[entry.atMs]=ordinal+1
        var id="mood:${entry.atMs}:$ordinal".hashCode().toLong()
        while(!used.add(id))id++
        id
    }
}
private fun moodRelative(at:Long):String{val elapsed=(System.currentTimeMillis()-at).coerceAtLeast(0);return when{elapsed<60_000->"just now";elapsed<3_600_000->"${elapsed/60_000} min ago";elapsed<86_400_000->"${elapsed/3_600_000} h ago";else->"${elapsed/86_400_000} d ago"}}
