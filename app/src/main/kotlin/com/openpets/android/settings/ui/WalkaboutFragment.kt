package com.openpets.android.settings.ui

import android.app.AlertDialog
import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import com.openpets.android.MainActivity
import com.openpets.android.settings.AppSettings
import com.openpets.android.settings.SettingsValidators

data class WalkaboutDraft(var mode:String,var speed:Float,var rawSpeed:String)
fun parseWalkaboutSpeed(raw:String):Float? {
    val value=raw.trim()
    fun parse(locale:java.util.Locale):Float? { val position=java.text.ParsePosition(0);val number=java.text.NumberFormat.getNumberInstance(locale).parse(value,position)?:return null;return number.toFloat().takeIf{position.index==value.length} }
    if(',' in value && '.' !in value) value.replace(',','.').toFloatOrNull()?.let{return it}
    return parse(java.util.Locale.US) ?: parse(java.util.Locale.getDefault())
}
fun formatWalkaboutSpeed(value:Float)=String.format(java.util.Locale.US,"%.3f",value).trimEnd('0').trimEnd('.')
fun validateWalkaboutSpeed(raw:String):Boolean=parseWalkaboutSpeed(raw)?.let { it in 0.5f..3.0f } ?: false
fun walkaboutEditorText(value:Float)=value.toString()
fun stageWalkaboutSliderValue(draft:WalkaboutDraft,value:Float){draft.speed=value;draft.rawSpeed=walkaboutEditorText(value)}
fun stageWalkaboutFieldValue(draft:WalkaboutDraft,raw:String):Float?{draft.rawSpeed=raw;val parsed=parseWalkaboutSpeed(raw)?.takeIf{it in .5f..3f}?:return null;draft.speed=parsed;return parsed}
fun isWalkaboutDraftDirty(draft:WalkaboutDraft,persistedMode:String,persistedSpeed:Float):Boolean =
    draft.mode!=persistedMode || draft.speed!=persistedSpeed || draft.rawSpeed!=walkaboutEditorText(persistedSpeed)
class WalkaboutViewModel:ViewModel(){var draft:WalkaboutDraft?=null;var speedError:String?=null}
class WalkaboutFragment:SettingsScreenFragment(){
    override val screenTitle get()=getString(com.openpets.android.R.string.ui_walkabout);override val transactional=true
    private val state:WalkaboutViewModel by viewModels();private lateinit var settings:AppSettings
    private var syncingSlider=false
    private val modes get()=listOf("off" to getString(com.openpets.android.R.string.walkabout_off),"roam" to getString(com.openpets.android.R.string.walkabout_roam),"patrol" to getString(com.openpets.android.R.string.walkabout_patrol),"follow" to getString(com.openpets.android.R.string.walkabout_follow))
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);settings=AppSettings(requireContext());if(state.draft==null)resetDraft()}
    private fun resetDraft(){val speed=settings.walkaboutSpeed;state.draft=WalkaboutDraft(settings.walkaboutMode,speed,walkaboutEditorText(speed));state.speedError=null}
    override fun rows():List<SettingRow>{val d=state.draft?:return emptyList();return listOf(SettingRow.ChoiceRow("walkaboutMode",getString(com.openpets.android.R.string.ui_mode),modes,d.mode),SettingRow.SliderRow("walkaboutSpeedSlider",getString(com.openpets.android.R.string.walkabout_speed),d.speed,.5f,3f,formatWalkaboutSpeed(d.speed)),SettingRow.TextFieldRow("walkaboutSpeed",getString(com.openpets.android.R.string.ui_speed_range),d.rawSpeed,getString(com.openpets.android.R.string.ui_speed_hint),state.speedError,false),sectionResetRow())}
    override fun onChoiceChanged(key:String,value:String){if(key=="walkaboutMode")state.draft?.mode=value;(list!!.adapter as? SettingsScreenAdapter)?.setChoiceValue(key,value)}
    override fun onSliderChanged(key:String,value:Float){val d=state.draft?:return;stageWalkaboutSliderValue(d,value);val adapter=list!!.adapter as? SettingsScreenAdapter ?:return;adapter.setSliderValue(key,value);adapter.setFieldValue("walkaboutSpeed",d.rawSpeed);list!!.findViewWithTag<android.widget.EditText>("walkaboutSpeed")?.let { field->if(!field.hasFocus() && field.text.toString()!=d.rawSpeed){syncingSlider=true;try{field.setText(d.rawSpeed)}finally{syncingSlider=false}} };updateSliderLabel(key,value)}
    override fun onFieldChanged(key:String,value:String){if(key!="walkaboutSpeed")return;val adapter=list!!.adapter as? SettingsScreenAdapter;if(syncingSlider){adapter?.setFieldValue(key,value);return};val d=state.draft?:return;val parsed=stageWalkaboutFieldValue(d,value);adapter?.setFieldValue(key,value);if(parsed!=null){adapter?.setSliderValue("walkaboutSpeedSlider",parsed);list!!.findViewWithTag<com.google.android.material.slider.Slider>("walkaboutSpeedSlider")?.value=parsed;updateSliderLabel("walkaboutSpeedSlider",parsed)}}
    private fun updateSliderLabel(key:String,value:Float){val label=formatWalkaboutSpeed(value);val title=getString(com.openpets.android.R.string.ui_speed_range);list!!.findViewWithTag<android.widget.TextView>("sliderLabel:$key")?.text=getString(com.openpets.android.R.string.settings_walkabout_speed_label,label);list!!.findViewWithTag<com.google.android.material.slider.Slider>(key)?.contentDescription="$title, $label"}
    override fun onSave(){val d=state.draft?:return;if(!validateWalkaboutSpeed(d.rawSpeed)){state.speedError=getString(com.openpets.android.R.string.ui_speed_invalid);refreshRows();focusError();return};val speed=d.speed;settings.applyWalkabout(d.mode,speed);(requireActivity() as MainActivity).refreshWalkabout(d.mode,speed);resetDraft();parentFragmentManager.popBackStack()}
    override fun onDiscard(){resetDraft();forceRebindRows()}
    override fun sectionResetDraft(){state.draft=WalkaboutDraft(com.openpets.android.settings.SettingsDefaults.WALKABOUT_MODE,com.openpets.android.settings.SettingsDefaults.WALKABOUT_SPEED,walkaboutEditorText(com.openpets.android.settings.SettingsDefaults.WALKABOUT_SPEED));state.speedError=null;forceRebindRows()}
    override fun requestClose():Boolean{val d=state.draft?:return false;if(!isWalkaboutDraftDirty(d,settings.walkaboutMode,settings.walkaboutSpeed))return false;AlertDialog.Builder(requireContext()).setTitle(com.openpets.android.R.string.ui_discard_title).setMessage(com.openpets.android.R.string.ui_unsaved_walkabout).setPositiveButton(com.openpets.android.R.string.ui_discard){_,_->onDiscard();parentFragmentManager.popBackStack()}.setNegativeButton(com.openpets.android.R.string.ui_keep_editing,null).show();return true}
    private fun focusError(){val rv=list?:return;val position=rows().indexOfFirst{it.id=="walkaboutSpeed".hashCode().toLong()};if(position>=0)rv.post{if(!isAdded||view==null||rv!==list)return@post;rv.scrollToPosition(position);rv.postOnAnimation{if(!isAdded||view==null||rv!==list)return@postOnAnimation;rv.findViewHolderForAdapterPosition(position)?.itemView?.findViewWithTag<android.view.View>("walkaboutSpeed")?.requestFocus()}}}
}
