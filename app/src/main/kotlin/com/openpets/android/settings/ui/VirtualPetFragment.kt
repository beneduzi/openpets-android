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
    override val screenTitle="Virtual pet"
    private var controller:VirtualPetController?=null
    private var binding:ControllerBinding<PetStats>?=null
    private var observer:LifecycleEventObserver?=null
    private var latest=PetStats(0,0,0,0)
    override fun rows():List<SettingRow> = virtualRows(latest,controller?.mood()?:PetMood.CONTENT)
    override fun onViewCreated(view:android.view.View,state:Bundle?){super.onViewCreated(view,state);controller=(requireActivity() as MainActivity).virtualPetControllerForSettings();val c=controller?:return
        binding=ControllerBinding(viewLifecycleOwner.lifecycle,{c.snapshot()},{c.addListener(it)},{c.removeListener(it)},{c.refresh()},render={latest=it;(list!!.adapter as? SettingsScreenAdapter)?.updateChangedRows(virtualRows(it,c.mood()))})
        c.showOnPet()
        observer=LifecycleEventObserver{_,e->when(e){Lifecycle.Event.ON_START->binding?.start();Lifecycle.Event.ON_STOP->binding?.stop();else->Unit}};viewLifecycleOwner.lifecycle.addObserver(observer!!)
    }
    override fun onRowClicked(row:SettingRow){val c=controller?:return;when((row as? SettingRow.ActionRow)?.key){
        "feed"->{c.feed();c.showOnPet()};"play"->{c.play();c.showOnPet()};"pet"->{c.cuddle();c.showOnPet()};"nap"->{c.rest();c.showOnPet()}
        "showVirtualPet"->{c.showOnPet();(requireActivity() as SettingsHost).revealStage()}
    }}
    override fun onDestroyView(){observer?.let{viewLifecycleOwner.lifecycle.removeObserver(it)};observer=null;binding?.releaseView();binding=null;controller=null;super.onDestroyView()}
}

private fun virtualRows(s:PetStats,mood:PetMood)=listOf(
    SettingRow.InfoRow("petMood","Mood",mood.name.lowercase().replaceFirstChar{it.uppercase()}),
    SettingRow.InfoRow("satiety","Satiety","${s.satiety}%"),SettingRow.InfoRow("happiness","Happiness","${s.happiness}%"),SettingRow.InfoRow("energy","Energy","${s.energy}%"),
    SettingRow.ActionRow("feed","Feed",null),SettingRow.ActionRow("play","Play",null),SettingRow.ActionRow("pet","Pet",null),SettingRow.ActionRow("nap","Nap",null),SettingRow.ActionRow("showVirtualPet","Show on pet",null),
)
