package com.openpets.android.settings.ui

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openpets.android.MainActivity
import com.openpets.android.companion.MoodController
import com.openpets.android.companion.MoodEntry
import com.openpets.android.settings.ui.SettingsHost

class MoodFragment:SettingsScreenFragment(){
    override val screenTitle get()=getString(com.openpets.android.R.string.ui_mood)
    private var controller:MoodController?=null
    private var binding:ControllerBinding<List<MoodEntry>>?=null
    private var observer:LifecycleEventObserver?=null
    private var latest=emptyList<MoodEntry>()
    private var note=""
    override fun rows():List<SettingRow> = moodRows(latest,controller,note,(1..5).map{(activity as? MainActivity)?.moodRatingLabelForSettings(it)?:"$it/5"},requireContext())
    override fun onViewCreated(view:android.view.View,state:Bundle?){super.onViewCreated(view,state);controller=(requireActivity() as MainActivity).moodControllerForSettings();val c=controller?:return
        latest=c.recent(200)
        binding=ControllerBinding(viewLifecycleOwner.lifecycle,{c.recent(200)},{c.addListener(it)},{c.removeListener(it)},{c.refresh()},render={latest=it;(list!!.adapter as? SettingsScreenAdapter)?.updateChangedRows(moodRows(it,c,note,(1..5).map{n->(activity as? MainActivity)?.moodRatingLabelForSettings(n)?:"$n/5"},requireContext()))})
        c.showOnPet()
        observer=LifecycleEventObserver{_,e->when(e){Lifecycle.Event.ON_START->binding?.start();Lifecycle.Event.ON_STOP->binding?.stop();else->Unit}};viewLifecycleOwner.lifecycle.addObserver(observer!!)
    }
    override fun onFieldChanged(key:String,value:String){if(key=="moodNote"){note=value;(list!!.adapter as? SettingsScreenAdapter)?.setFieldValue(key,value)}}
    override fun onRowClicked(row:SettingRow){val c=controller?:return;val action=row as? SettingRow.ActionRow?:return;when(action.key){
        "rate1","rate2","rate3","rate4","rate5"->{c.record(action.key.last().digitToInt(),note);c.showOnPet();(list!!.adapter as? SettingsScreenAdapter)?.updateChangedRows(moodRows(c.recent(200),c,note,(1..5).map{n->(activity as? MainActivity)?.moodRatingLabelForSettings(n)?:"$n/5"},requireContext()))}
        "nudge"->{c.nudge();(requireActivity() as SettingsHost).revealStage()}
        "showMood"->{c.showOnPet();(requireActivity() as SettingsHost).revealStage()}
    }}
    override fun onDestroyView(){observer?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};observer=null;binding?.releaseView();binding=null;controller=null;super.onDestroyView()}
}

private fun moodRows(entries:List<MoodEntry>,controller:MoodController?,note:String,labels:List<String>,context:android.content.Context):List<SettingRow>{
    val latest=entries.firstOrNull();val average=controller?.average(7)
    val rows= mutableListOf<SettingRow>(SettingRow.TextFieldRow("moodNote",context.getString(com.openpets.android.R.string.settings_mood_note),note,context.getString(com.openpets.android.R.string.settings_mood_note_hint),null,false))
    (1..5).forEach{rows+=SettingRow.ActionRow("rate$it",labels[it-1],null)}
    rows+=SettingRow.InfoRow("latestMood",context.getString(com.openpets.android.R.string.settings_mood_latest),latest?.let{"${labels[it.value-1]}${it.note?.let{n->" · $n"}?:""} · ${moodRelative(it.atMs,context)}"}?:context.getString(com.openpets.android.R.string.settings_mood_none))
    rows+=SettingRow.InfoRow("averageMood",context.getString(com.openpets.android.R.string.ui_average_7day),average?.let{java.text.NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).format(it)}?:"—")
    if(entries.isEmpty())rows+=SettingRow.InfoRow("moodHistoryEmpty",context.getString(com.openpets.android.R.string.settings_mood_history),context.getString(com.openpets.android.R.string.settings_mood_empty)) else {
        val ids=moodRowIds(entries)
        entries.forEachIndexed{i,e->rows+=SettingRow.InfoRow("moodHistory:$i","${e.value}/5",listOfNotNull(e.note,moodRelative(e.atMs,context)).joinToString(" · "),id=ids[i])}
    }
    rows+=SettingRow.ActionRow("nudge",context.getString(com.openpets.android.R.string.settings_mood_nudge),null);rows+=SettingRow.ActionRow("showMood",context.getString(com.openpets.android.R.string.ui_show_on_pet),null)
    return rows
}
fun moodRowIds(entries:List<MoodEntry>):List<Long>{
    val occurrences=mutableMapOf<Long,Int>()
    val used=mutableSetOf<Long>()
    return entries.map{entry->
        val ordinal=occurrences.getOrDefault(entry.atMs,0);occurrences[entry.atMs]=ordinal+1
        var id=MOOD_ROW_ID_NAMESPACE or ("${entry.atMs}:$ordinal".hashCode().toLong() and MOOD_ROW_ID_MASK)
        while(!used.add(id))id++
        id
    }
}
private const val MOOD_ROW_ID_NAMESPACE=0x4d4f4f4400000000L
private const val MOOD_ROW_ID_MASK=0x00000000ffffffffL
private fun moodRelative(at:Long,context:android.content.Context):String{val elapsed=(System.currentTimeMillis()-at).coerceAtLeast(0);return when{elapsed<60_000->context.getString(com.openpets.android.R.string.ui_just_now);elapsed<3_600_000->context.getString(com.openpets.android.R.string.settings_relative_ago,context.resources.getQuantityString(com.openpets.android.R.plurals.ui_minutes_count,(elapsed/60_000).toInt(),(elapsed/60_000).toInt()));elapsed<86_400_000->context.getString(com.openpets.android.R.string.settings_relative_ago,context.resources.getQuantityString(com.openpets.android.R.plurals.ui_hours_count,(elapsed/3_600_000).toInt(),(elapsed/3_600_000).toInt()));else->context.getString(com.openpets.android.R.string.settings_relative_ago,context.resources.getQuantityString(com.openpets.android.R.plurals.ui_days_count,(elapsed/86_400_000).toInt(),(elapsed/86_400_000).toInt()))}}
