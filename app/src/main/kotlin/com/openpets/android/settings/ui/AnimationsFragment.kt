package com.openpets.android.settings.ui

import android.app.AlertDialog
import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import com.openpets.android.MainActivity
import com.openpets.android.protocol.REACTIONS
import com.openpets.android.protocol.SELECTABLE_ANIMATIONS
import com.openpets.android.protocol.resolveReactionSpriteState
import com.openpets.android.settings.AppSettings
import com.openpets.android.settings.SettingsValidators

data class AnimationsDraft(val overrides:MutableMap<String,String>,var waiting:String)
class AnimationsViewModel:ViewModel(){var draft:AnimationsDraft?=null;var waitingError:String?=null}
class AnimationsFragment:SettingsScreenFragment(){
    override val screenTitle="Animations";override val transactional=true
    private val state:AnimationsViewModel by viewModels();private lateinit var settings:AppSettings
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);settings=AppSettings(requireContext());if(state.draft==null)resetDraft()}
    private fun resetDraft(){state.draft=AnimationsDraft(settings.reactionOverrides.toMutableMap(),settings.waitingDurationMs.toString());state.waitingError=null}
    override fun rows():List<SettingRow>{val d=state.draft?:return emptyList();val items=REACTIONS.map{reaction->val chosen=d.overrides[reaction]?:resolveReactionSpriteState(reaction);SettingRow.ActionRow("reaction:$reaction",reaction,"$reaction → ${if(reaction in d.overrides)chosen else "Default ($chosen)"}")};return items+SettingRow.TextFieldRow("waitingDuration","Waiting duration (ms)",d.waiting,null,state.waitingError,true)}
    override fun onRowClicked(row:SettingRow){val key=(row as? SettingRow.ActionRow)?.key?:return;val reaction=key.removePrefix("reaction:");if(reaction !in REACTIONS)return;val options=listOf("Default")+SELECTABLE_ANIMATIONS;AlertDialog.Builder(requireContext()).setTitle("Animation for $reaction").setItems(options.toTypedArray()){_,index->val d=state.draft?:return@setItems;if(index==0)d.overrides.remove(reaction)else d.overrides[reaction]=options[index];(list.adapter as? SettingsScreenAdapter)?.replaceRows(rows())}.show()}
    override fun onFieldChanged(key:String,value:String){if(key=="waitingDuration"){state.draft?.waiting=value;(list.adapter as? SettingsScreenAdapter)?.setFieldValue(key,value)}}
    override fun onSave(){val d=state.draft?:return;when(val result=SettingsValidators.waitingDuration(d.waiting)){is SettingsValidators.Result.Invalid->{state.waitingError="Waiting duration must be 200–10000 ms.";refreshRows();focusError()};is SettingsValidators.Result.Ok->{settings.applyAnimations(d.overrides,result.value);(requireActivity() as MainActivity).refreshAnimationConfig();resetDraft();parentFragmentManager.popBackStack()}}}
    override fun onDiscard(){resetDraft();forceRebindRows()}
    override fun requestClose():Boolean{val d=state.draft?:return false;if(d.overrides==settings.reactionOverrides&&d.waiting==settings.waitingDurationMs.toString())return false;AlertDialog.Builder(requireContext()).setTitle("Discard changes?").setMessage("Unsaved animation settings will be discarded.").setPositiveButton("Discard"){_,_->onDiscard();parentFragmentManager.popBackStack()}.setNegativeButton("Keep editing",null).show();return true}
    private fun focusError(){val position=rows().indexOfFirst{it.id=="waitingDuration".hashCode().toLong()};if(position>=0)list.post{list.scrollToPosition(position);list.postOnAnimation{list.findViewHolderForAdapterPosition(position)?.itemView?.findViewWithTag<android.view.View>("waitingDuration")?.requestFocus()}}}
}
