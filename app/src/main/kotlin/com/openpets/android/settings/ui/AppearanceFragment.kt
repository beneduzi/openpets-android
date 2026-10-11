package com.openpets.android.settings.ui

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import com.openpets.android.MainActivity
import com.openpets.android.PetBus
import com.openpets.android.settings.AppSettings
import com.openpets.android.net.OpenPetsService
import com.openpets.android.SoundNotifier

data class AppearanceDraft(
    var sound:Boolean,var haptics:Boolean,var tts:Boolean,var notify:Boolean,var hostStatus:Boolean,
)
class AppearanceViewModel:ViewModel(){ var draft:AppearanceDraft?=null }

class AppearanceFragment:SettingsScreenFragment(){
    override val screenTitle="Appearance & output"
    override val transactional=true
    private val state:AppearanceViewModel by viewModels()
    private lateinit var settings:AppSettings
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);settings=AppSettings(requireContext());if(state.draft==null)resetDraft()}
    private fun persisted()=AppearanceDraft(settings.soundEnabled,settings.hapticsEnabled,settings.ttsEnabled,settings.notifyEnabled,settings.hostStatusEnabled)
    private fun resetDraft(){state.draft=persisted()}
    override fun rows():List<SettingRow>{
        val d=state.draft?:return emptyList()
        val soundName=settings.customSoundUri?.let(::soundDisplayName) ?: "Default sound"
        return listOf(
            SettingRow.SwitchRow("soundEnabled","Notification sound",null,d.sound),
            SettingRow.SwitchRow("hapticsEnabled","Vibration",null,d.haptics),
            SettingRow.SwitchRow("ttsEnabled","Read bubble text aloud",null,d.tts),
            SettingRow.SwitchRow("notifyEnabled","System notifications","Controls background agent-event notifications. OS permission or notification-channel settings may restrict delivery.",d.notify),
            SettingRow.SwitchRow("hostStatusEnabled","Show host status","Requires a WebSocket host-status sender.",d.hostStatus),
            SettingRow.InfoRow("soundFile","Sound file",soundName,"Choose"),
            SettingRow.ActionRow("testSound","Test sound","Sets alarm volume to maximum for about 2.5 seconds."),
        )
    }
    override fun onResume(){super.onResume();refreshSoundRow()}
    fun refreshSoundRow(){if(view==null)return;(list!!.adapter as? SettingsScreenAdapter)?.updateRowsByKey(rows(),"soundFile")}
    override fun onSwitchChanged(key:String,value:Boolean){state.draft?.let{d->when(key){"soundEnabled"->d.sound=value;"hapticsEnabled"->d.haptics=value;"ttsEnabled"->d.tts=value;"notifyEnabled"->d.notify=value;"hostStatusEnabled"->d.hostStatus=value}};(list!!.adapter as? SettingsScreenAdapter)?.updateRowsByKey(rows(),key)}
    override fun onRowClicked(row:SettingRow){when(row.id){"soundFile".hashCode().toLong()->showSoundChoices();"testSound".hashCode().toLong()->AlertDialog.Builder(requireContext()).setTitle("Test sound").setMessage("This test sets alarm volume to maximum for about 2.5 seconds.").setPositiveButton("Play") { _,_->SoundNotifier(requireContext()).playTest() }.setNegativeButton("Cancel",null).show()}}
    private fun showSoundChoices(){AlertDialog.Builder(requireContext()).setTitle("Sound file").setItems(arrayOf("Default sound","Choose audio file…")){_,which->if(which==0)(requireActivity() as MainActivity).chooseDefaultSound() else (requireActivity() as MainActivity).chooseCustomSound()}.show()}
    override fun onSave(){val d=state.draft?:return;val wasHost=settings.hostStatusEnabled;settings.applyAppearance(d.sound,d.haptics,d.tts,d.notify,d.hostStatus);OpenPetsService.reload(requireContext());if(wasHost&&!d.hostStatus)PetBus.hostMetrics(null);resetDraft();parentFragmentManager.popBackStack()}
    override fun onDiscard(){resetDraft();refreshRows()}
    override fun requestClose():Boolean{val d=state.draft?:return false;if(d==persisted())return false;AlertDialog.Builder(requireContext()).setTitle("Discard changes?").setMessage("Unsaved appearance settings will be discarded.").setPositiveButton("Discard"){_,_->onDiscard();parentFragmentManager.popBackStack()}.setNegativeButton("Keep editing",null).show();return true}
    private fun soundDisplayName(uri:String):String{val parsed=android.net.Uri.parse(uri);return runCatching{requireContext().contentResolver.query(parsed,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null}}.getOrNull() ?: parsed.lastPathSegment ?: "Custom sound"}
}
