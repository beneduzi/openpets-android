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
    var sound:Boolean,var haptics:Boolean,var tts:Boolean,var notify:Boolean,var hostStatus:Boolean,var clearSound:Boolean=false,
)
class AppearanceViewModel:ViewModel(){ var draft:AppearanceDraft?=null }

class AppearanceFragment:SettingsScreenFragment(){
    override val screenTitle get()=getString(com.openpets.android.R.string.ui_appearance_title)
    override val transactional=true
    private val state:AppearanceViewModel by viewModels()
    private lateinit var settings:AppSettings
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);settings=AppSettings(requireContext());if(state.draft==null)resetDraft()}
    private fun persisted()=AppearanceDraft(settings.soundEnabled,settings.hapticsEnabled,settings.ttsEnabled,settings.notifyEnabled,settings.hostStatusEnabled)
    private fun resetDraft(){state.draft=persisted()}
    override fun rows():List<SettingRow>{
        val d=state.draft?:return emptyList()
        val soundName=if(d.clearSound) getString(com.openpets.android.R.string.ui_default_sound) else settings.customSoundUri?.let(::soundDisplayName) ?: getString(com.openpets.android.R.string.ui_default_sound)
        return listOf(
            SettingRow.SwitchRow("soundEnabled",getString(com.openpets.android.R.string.ui_notification_sound),null,d.sound),
            SettingRow.SwitchRow("hapticsEnabled",getString(com.openpets.android.R.string.ui_vibration),null,d.haptics),
            SettingRow.SwitchRow("ttsEnabled",getString(com.openpets.android.R.string.ui_tts),null,d.tts),
            SettingRow.SwitchRow("notifyEnabled",getString(com.openpets.android.R.string.ui_system_notifications),getString(com.openpets.android.R.string.ui_notification_summary),d.notify),
            SettingRow.SwitchRow("hostStatusEnabled",getString(com.openpets.android.R.string.ui_host_status),getString(com.openpets.android.R.string.ui_host_status_summary),d.hostStatus),
            SettingRow.InfoRow("soundFile",getString(com.openpets.android.R.string.ui_sound_file),soundName,getString(com.openpets.android.R.string.settings_choose)),
            SettingRow.ActionRow("testSound",getString(com.openpets.android.R.string.ui_test_sound),getString(com.openpets.android.R.string.ui_test_sound_warning)),
            sectionResetRow(),
        )
    }
    override fun onResume(){super.onResume();refreshSoundRow()}
    fun refreshSoundRow(){if(view==null)return;(list!!.adapter as? SettingsScreenAdapter)?.updateRowsByKey(rows(),"soundFile")}
    /** Called after a successful explicit sound selection (not on picker cancellation). */
    fun onSoundSelected(){state.draft?.clearSound=false;refreshSoundRow()}
    override fun onSwitchChanged(key:String,value:Boolean){state.draft?.let{d->when(key){"soundEnabled"->d.sound=value;"hapticsEnabled"->d.haptics=value;"ttsEnabled"->d.tts=value;"notifyEnabled"->d.notify=value;"hostStatusEnabled"->d.hostStatus=value}};(list!!.adapter as? SettingsScreenAdapter)?.updateRowsByKey(rows(),key)}
    override fun onRowClicked(row:SettingRow){when(row.id){"soundFile".hashCode().toLong()->showSoundChoices();"testSound".hashCode().toLong()->AlertDialog.Builder(requireContext()).setTitle(com.openpets.android.R.string.ui_test_sound).setMessage(com.openpets.android.R.string.ui_test_sound_warning).setPositiveButton(com.openpets.android.R.string.ui_play) { _,_->SoundNotifier(requireContext()).playTest() }.setNegativeButton(com.openpets.android.R.string.ui_cancel,null).show()}}
    private fun showSoundChoices(){AlertDialog.Builder(requireContext()).setTitle(com.openpets.android.R.string.ui_sound_file).setItems(arrayOf(getString(com.openpets.android.R.string.ui_default_sound),getString(com.openpets.android.R.string.ui_choose_sound))){_,which->if(which==0)(requireActivity() as MainActivity).chooseDefaultSound() else (requireActivity() as MainActivity).chooseCustomSound()}.show()}
    override fun onSave(){val d=state.draft?:return;val wasHost=settings.hostStatusEnabled;if(d.clearSound)settings.customSoundUri=null;settings.applyAppearance(d.sound,d.haptics,d.tts,d.notify,d.hostStatus);OpenPetsService.reload(requireContext());if(wasHost&&!d.hostStatus)PetBus.hostMetrics(null);resetDraft();parentFragmentManager.popBackStack()}
    override fun onDiscard(){resetDraft();refreshRows()}
    override fun sectionResetDraft(){state.draft=AppearanceDraft(com.openpets.android.settings.SettingsDefaults.SOUND_ENABLED,com.openpets.android.settings.SettingsDefaults.HAPTICS_ENABLED,com.openpets.android.settings.SettingsDefaults.TTS_ENABLED,com.openpets.android.settings.SettingsDefaults.NOTIFY_ENABLED,com.openpets.android.settings.SettingsDefaults.HOST_STATUS_ENABLED,clearSound=true);forceRebindRows()}
    override fun requestClose():Boolean{val d=state.draft?:return false;if(d==persisted())return false;AlertDialog.Builder(requireContext()).setTitle(com.openpets.android.R.string.ui_discard_title).setMessage(com.openpets.android.R.string.ui_unsaved_appearance).setPositiveButton(com.openpets.android.R.string.ui_discard){_,_->onDiscard();parentFragmentManager.popBackStack()}.setNegativeButton(com.openpets.android.R.string.ui_keep_editing,null).show();return true}
    private fun soundDisplayName(uri:String):String{val parsed=android.net.Uri.parse(uri);return runCatching{requireContext().contentResolver.query(parsed,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null}}.getOrNull() ?: parsed.lastPathSegment ?: getString(com.openpets.android.R.string.settings_custom_sound)}
}
