package com.openpets.android.settings.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.openpets.android.MainActivity
import com.openpets.android.PetBus
import com.openpets.android.R
import com.openpets.android.companion.SttController
import com.openpets.android.companion.SttState

private const val VOICE_PERMISSION_REQUEST=1003
class VoiceFragment:SettingsScreenFragment(),SettingsPermissionResultHandler{
    override val screenTitle="Voice input"
    private var stt:SttController?=null
    private var state=SttState.IDLE
    private val callbackEpoch=CallbackEpoch()
    private var captureId=""
    private var permissionDenied=false
    private var hasPermission=false
    override fun rows():List<SettingRow>{
        val status=when{!hasPermission->if(permissionDenied)getString(R.string.voice_permission)else "Microphone permission required";else->when(state){SttState.IDLE->"Ready";SttState.LISTENING->getString(R.string.voice_listening);SttState.ERROR->getString(R.string.voice_error);SttState.UNAVAILABLE->getString(R.string.voice_unavailable)}}
        return listOf(SettingRow.InfoRow("voiceStatus","Status",status),SettingRow.ActionRow("voiceListen",if(!hasPermission)"Grant microphone access" else if(state==SttState.LISTENING)"Stop" else getString(R.string.voice_speak),null))
    }
    override fun onViewCreated(view:android.view.View,savedInstanceState:Bundle?){
        hasPermission=ContextCompat.checkSelfPermission(requireContext(),Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
        super.onViewCreated(view,savedInstanceState);createControllerCallbacks()
        updateRowsIncrementally()
    }
    private fun createControllerCallbacks(){val token=callbackEpoch.advance();stt?.destroy();stt=SttController(requireContext(),{text->if(isCurrent(token)){PetBus.inputText(text,captureId);android.widget.Toast.makeText(requireContext(),R.string.voice_sent,android.widget.Toast.LENGTH_SHORT).show()}},{newState->if(isCurrent(token)){state=newState;updateRowsIncrementally()}})}
    private fun isCurrent(token:Long)=callbackEpoch.isCurrent(token)&&isAdded&&view!=null&&viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    override fun onRowClicked(row:SettingRow){if((row as? SettingRow.ActionRow)?.key!="voiceListen")return
        if(!hasPermission){AlertDialog.Builder(requireContext()).setTitle(R.string.voice_title).setMessage(R.string.voice_permission)
            .setPositiveButton("Grant"){_,_->(requireActivity() as SettingsHost).requestSettingsPermission(Manifest.permission.RECORD_AUDIO,VOICE_PERMISSION_REQUEST)}
            .setNegativeButton(R.string.dialog_close,null).show();return}
        val controller=stt?:return
        if(state==SttState.LISTENING){controller.stop();state=SttState.IDLE}
        else{captureId=java.util.UUID.randomUUID().toString();state=SttState.LISTENING;controller.start()}
        (list?.adapter as? SettingsScreenAdapter)?.updateChangedRows(rows())
    }
    override fun onSettingsPermissionResult(requestCode:Int,granted:Boolean){if(requestCode!=VOICE_PERMISSION_REQUEST)return;hasPermission=granted;permissionDenied=!granted;if(granted)state=SttState.IDLE;updateRowsIncrementally()}
    override fun onStart(){super.onStart();createControllerCallbacks();updateRowsIncrementally()}
    override fun onStop(){callbackEpoch.advance();stt?.cancel();state=SttState.IDLE;super.onStop()}
    override fun onDestroyView(){callbackEpoch.advance();stt?.cancel();stt?.destroy();stt=null;super.onDestroyView()}
}
