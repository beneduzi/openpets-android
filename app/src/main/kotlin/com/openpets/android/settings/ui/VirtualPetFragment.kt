package com.openpets.android.settings.ui

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openpets.android.MainActivity
import com.openpets.android.settings.ui.SettingsHost
import com.openpets.android.companion.PetStats
import com.openpets.android.companion.PetMood
import com.openpets.android.companion.VirtualPetController

class VirtualPetFragment:SettingsScreenFragment(){
    override val screenTitle get()=getString(com.openpets.android.R.string.ui_virtual_pet)
    private var controller:VirtualPetController?=null
    private var binding:ControllerBinding<PetStats>?=null
    private var observer:LifecycleEventObserver?=null
    private var latest=PetStats(0,0,0,0)
    override fun rows():List<SettingRow> = virtualRows(latest,controller?.mood()?:PetMood.CONTENT,requireContext())
    override fun onViewCreated(view:android.view.View,state:Bundle?){super.onViewCreated(view,state);controller=(requireActivity() as MainActivity).virtualPetControllerForSettings();val c=controller?:return
        latest=c.snapshot();updateRowsIncrementally()
        binding=ControllerBinding(viewLifecycleOwner.lifecycle,{c.snapshot()},{c.addListener(it)},{c.removeListener(it)},{c.refresh()},render={latest=it;(list!!.adapter as? SettingsScreenAdapter)?.updateChangedRows(virtualRows(it,c.mood(),requireContext()))})
        c.showOnPet()
        observer=LifecycleEventObserver{_,e->when(e){Lifecycle.Event.ON_START->binding?.start();Lifecycle.Event.ON_STOP->binding?.stop();else->Unit}};viewLifecycleOwner.lifecycle.addObserver(observer!!)
    }
    override fun onRowClicked(row:SettingRow){val c=controller?:return;when((row as? SettingRow.ActionRow)?.key){
        "feed"->{c.feed();c.showOnPet()};"play"->{c.play();c.showOnPet()};"pet"->{c.cuddle();c.showOnPet()};"nap"->{c.rest();c.showOnPet()}
        "showVirtualPet"->{c.showOnPet();(requireActivity() as SettingsHost).revealStage()}
    }}
    override fun onDestroyView(){observer?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};observer=null;binding?.releaseView();binding=null;controller=null;super.onDestroyView()}
}

private fun virtualRows(s:PetStats,mood:PetMood,context:android.content.Context)=listOf(
    SettingRow.InfoRow("petMood",context.getString(com.openpets.android.R.string.ui_mood),context.getString(when(mood){PetMood.HAPPY->com.openpets.android.R.string.virtualpet_mood_happy;PetMood.CONTENT->com.openpets.android.R.string.virtualpet_mood_content;PetMood.TIRED->com.openpets.android.R.string.virtualpet_mood_tired;PetMood.HUNGRY->com.openpets.android.R.string.virtualpet_mood_hungry;PetMood.EXCITED->com.openpets.android.R.string.virtualpet_mood_excited})),
    SettingRow.InfoRow("satiety",context.getString(com.openpets.android.R.string.ui_satiety),"${s.satiety}%"),SettingRow.InfoRow("happiness",context.getString(com.openpets.android.R.string.ui_happiness),"${s.happiness}%"),SettingRow.InfoRow("energy",context.getString(com.openpets.android.R.string.ui_energy),"${s.energy}%"),
    SettingRow.ActionRow("feed",context.getString(com.openpets.android.R.string.ui_feed),null),SettingRow.ActionRow("play",context.getString(com.openpets.android.R.string.ui_play_action),null),SettingRow.ActionRow("pet",context.getString(com.openpets.android.R.string.ui_pet_action),null),SettingRow.ActionRow("nap",context.getString(com.openpets.android.R.string.ui_nap),null),SettingRow.ActionRow("showVirtualPet",context.getString(com.openpets.android.R.string.ui_show_on_pet),null),
)
