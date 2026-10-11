package com.openpets.android.settings.ui

import android.app.AlertDialog
import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import com.openpets.android.settings.AppSettings

class StartupViewModel:ViewModel(){var staged:Boolean?=null}
class AdvancedFragment:SettingsScreenFragment(){
    override val screenTitle="Startup"
    override val transactional=true
    private val state:StartupViewModel by viewModels()
    private lateinit var settings:AppSettings
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);settings=AppSettings(requireContext());if(state.staged==null)state.staged=settings.autoStartOnBoot}
    override fun rows()=listOf(SettingRow.SwitchRow("autoStartOnBoot","Start on boot",null,state.staged?:false))
    override fun onSwitchChanged(key:String,value:Boolean){state.staged=value;(list!!.adapter as? SettingsScreenAdapter)?.updateRowsByKey(rows(),key)}
    override fun onSave(){settings.autoStartOnBoot=state.staged?:settings.autoStartOnBoot;state.staged=settings.autoStartOnBoot;parentFragmentManager.popBackStack()}
    override fun onDiscard(){state.staged=settings.autoStartOnBoot;refreshRows()}
    override fun requestClose():Boolean{if(state.staged==settings.autoStartOnBoot)return false;AlertDialog.Builder(requireContext()).setTitle("Discard changes?").setMessage("Unsaved startup settings will be discarded.").setPositiveButton("Discard"){_,_->onDiscard();parentFragmentManager.popBackStack()}.setNegativeButton("Keep editing",null).show();return true}
}
