package com.openpets.android.settings.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ClipDescription
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.openpets.android.MainActivity
import com.openpets.android.net.ConnectionStatus
import com.openpets.android.net.ServerState
import com.openpets.android.net.ServersStatus
import com.openpets.android.net.OpenPetsService
import com.openpets.android.settings.AppSettings
import com.openpets.android.settings.SettingsValidators

data class ConnectionDraft(
    var bind: String, var ipcEnabled:Boolean,var ipcPort:String,var remoteEnabled:Boolean,var remotePort:String,
    var wsEnabled:Boolean,var wsPort:String,var ipcToken:String,var remoteToken:String,var requireLease:Boolean,
    var ipcTokenEdited:Boolean=false,var remoteTokenEdited:Boolean=false,
    val loadedIpcPort:String=ipcPort,val loadedRemotePort:String=remotePort,val loadedWsPort:String=wsPort,
)
fun validateConnectionDraft(d: ConnectionDraft, validateIpcToken:Boolean=d.ipcTokenEdited, validateRemoteToken:Boolean=d.remoteTokenEdited): Map<String, SettingsValidators.Error> {
    val errors=linkedMapOf<String,SettingsValidators.Error>()
    val ports=linkedMapOf<String,Int>()
    listOf(Triple("ipcPort",d.ipcEnabled,d.ipcPort) to d.loadedIpcPort,Triple("remotePort",d.remoteEnabled,d.remotePort) to d.loadedRemotePort,Triple("wsPort",d.wsEnabled,d.wsPort) to d.loadedWsPort).forEach { (port,loaded)->
        val (key,enabled,raw)=port
        if(!enabled && raw==loaded)return@forEach
        when(val result=SettingsValidators.port(raw)) { is SettingsValidators.Result.Ok -> { if(enabled) ports[key]=result.value }; is SettingsValidators.Result.Invalid -> errors[key]=result.error }
    }
    val duplicate=ports.entries.groupBy { it.value }.values.filter { it.size>1 }.flatten()
    duplicate.forEach { errors[it.key]=SettingsValidators.Error.CONFLICT }
    (SettingsValidators.bindAddress(d.bind) as? SettingsValidators.Result.Invalid)?.let { errors["bind"]=it.error }
    if(validateIpcToken)(SettingsValidators.ipcToken(d.ipcToken) as? SettingsValidators.Result.Invalid)?.let { errors["ipcToken"]=it.error }
    if(validateRemoteToken)(SettingsValidators.remoteToken(d.remoteToken) as? SettingsValidators.Result.Invalid)?.let { errors["remoteToken"]=it.error }
    return errors
}
fun connectionPortForSave(raw:String, enabled:Boolean, persisted:Int, loaded:String=raw):Int = if(enabled || raw!=loaded) when(val result=SettingsValidators.port(raw)){ is SettingsValidators.Result.Ok->result.value; is SettingsValidators.Result.Invalid->throw IllegalArgumentException("Invalid port: ${result.error}") } else persisted
fun tokenForSave(raw:String, edited:Boolean, ipc:Boolean):String = if(!edited) raw else (if(ipc) SettingsValidators.ipcToken(raw) else SettingsValidators.remoteToken(raw)).let { (it as SettingsValidators.Result.Ok).value }

class ConnectionsDraftViewModel: androidx.lifecycle.ViewModel() {
    var draft:ConnectionDraft?=null
    var errors:Map<String,SettingsValidators.Error> = emptyMap()
    var status:ServersStatus=ConnectionStatus.current
}

class ConnectionsFragment: SettingsScreenFragment() {
    private val pendingFocusListeners=mutableListOf<Pair<android.view.ViewTreeObserver,android.view.ViewTreeObserver.OnPreDrawListener>>()
    private var showClearedTokenNotice=false
    override val screenTitle get()=getString(com.openpets.android.R.string.settings_connections_title)
    override val transactional=true
    private val state:ConnectionsDraftViewModel by viewModels()
    private lateinit var appSettings:AppSettings
    private var revealed=false
    private var priorSecure=false
    private var tokenDialog:AlertDialog?=null
    private val listener:(ServersStatus)->Unit={ status-> state.status=status; if(isAdded && view!=null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) activity?.runOnUiThread { statusChanged() } }
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); appSettings=AppSettings(requireContext()); if(state.draft==null){ showClearedTokenNotice=savedInstanceState?.getBoolean(STATE_UNSAVED_TOKEN_EDITS)==true; loadDraft() } }
    override fun onSaveInstanceState(outState:Bundle) { outState.putBoolean(STATE_UNSAVED_TOKEN_EDITS,state.draft?.let { it.ipcTokenEdited || it.remoteTokenEdited }==true); super.onSaveInstanceState(outState) }
    override fun onResume() { super.onResume(); if(showClearedTokenNotice){showClearedTokenNotice=false; Toast.makeText(requireContext(),com.openpets.android.R.string.settings_connections_token_notice,Toast.LENGTH_LONG).show()} }
    private fun loadDraft() { state.draft=ConnectionDraft(appSettings.bindAddress,appSettings.ipcEnabled,appSettings.ipcPort.toString(),appSettings.remoteEnabled,appSettings.remotePort.toString(),appSettings.wsEnabled,appSettings.wsPort.toString(),appSettings.ipcToken,appSettings.remoteToken,appSettings.requireLease); state.errors=emptyMap() }
    override fun onStart() { super.onStart(); state.status=ConnectionStatus.current; statusChanged(); ConnectionStatus.addListener(listener) }
    override fun onStop() { ConnectionStatus.removeListener(listener); maskSecrets(); super.onStop() }
    override fun onDestroyView() { maskSecrets(); tokenDialog?.dismiss(); tokenDialog=null; pendingFocusListeners.forEach { (observer,listener)->if(observer.isAlive)observer.removeOnPreDrawListener(listener) }; pendingFocusListeners.clear(); super.onDestroyView() }

    override fun rows():List<SettingRow> {
        val d=state.draft ?: return emptyList()
        fun err(key:String)=state.errors[key]?.let(::errorText)
        val statuses=getString(com.openpets.android.R.string.settings_connections_status_block,stateText(state.status.ipc),stateText(state.status.remote),stateText(state.status.webSocket),state.status.configuredBindAddress)
        return listOf(
            SettingRow.TextFieldRow("bind",getString(com.openpets.android.R.string.settings_connections_bind),d.bind,getString(com.openpets.android.R.string.ui_bind_hint),err("bind"),false),
            SettingRow.SectionHeader(getString(com.openpets.android.R.string.ui_servers)),
            SettingRow.SwitchDetailRow("ipcEnabled","IPC",null,d.ipcEnabled,"ipcPort"), SettingRow.TextFieldRow("ipcPort",getString(com.openpets.android.R.string.settings_ipc_port),d.ipcPort,null,err("ipcPort"),true,d.ipcEnabled),
            SettingRow.SwitchDetailRow("remoteEnabled","Remote",null,d.remoteEnabled,"remotePort"), SettingRow.TextFieldRow("remotePort",getString(com.openpets.android.R.string.settings_remote_port),d.remotePort,null,err("remotePort"),true,d.remoteEnabled),
            SettingRow.SwitchDetailRow("wsEnabled",getString(com.openpets.android.R.string.settings_ws),getString(com.openpets.android.R.string.settings_connections_experimental),d.wsEnabled,"wsPort"), SettingRow.TextFieldRow("wsPort",getString(com.openpets.android.R.string.settings_ws_port),d.wsPort,null,err("wsPort"),true,d.wsEnabled),
            SettingRow.SectionHeader(getString(com.openpets.android.R.string.ui_security)),
            SettingRow.InfoRow("ipcTokenDisplay",getString(com.openpets.android.R.string.settings_connections_ipc_token),"••••••••"),
            SettingRow.ActionRow("editIpc",getString(com.openpets.android.R.string.settings_connections_edit_ipc),null),
            SettingRow.InfoRow("remoteTokenDisplay",getString(com.openpets.android.R.string.settings_connections_remote_token),"••••••••"),
            SettingRow.ActionRow("editRemote",getString(com.openpets.android.R.string.settings_connections_edit_remote),null),
            SettingRow.SwitchRow("requireLease",getString(com.openpets.android.R.string.settings_connections_require_lease),getString(com.openpets.android.R.string.ui_require_lease_help),d.requireLease),
            SettingRow.InfoRow("status",getString(com.openpets.android.R.string.settings_connections_status),statuses),
            SettingRow.InfoRow("remoteWarning",getString(com.openpets.android.R.string.settings_connections_warning),if(!d.remoteTokenEdited && !com.openpets.android.protocol.isValidRemoteToken(d.remoteToken)) getString(com.openpets.android.R.string.settings_connections_remote_warning) else getString(com.openpets.android.R.string.settings_connections_none)),
            SettingRow.ActionRow("copyIpc",getString(com.openpets.android.R.string.settings_connections_copy_ipc),null), SettingRow.ActionRow("copyRemote",getString(com.openpets.android.R.string.settings_connections_copy_remote),null),
            sectionResetRow(),
        )
    }
    override fun onFieldChanged(key:String,value:String) { state.draft?.let { d-> when(key){"bind"->d.bind=value;"ipcPort"->d.ipcPort=value;"remotePort"->d.remotePort=value;"wsPort"->d.wsPort=value;"ipcToken"->{d.ipcToken=value;d.ipcTokenEdited=true};"remoteToken"->{d.remoteToken=value;d.remoteTokenEdited=true} }; (list!!.adapter as? SettingsScreenAdapter)?.setFieldValue(key,value) } }
    override fun sectionResetDraft() { val d=state.draft?:return;d.ipcEnabled=com.openpets.android.settings.SettingsDefaults.IPC_ENABLED;d.remoteEnabled=com.openpets.android.settings.SettingsDefaults.REMOTE_ENABLED;d.wsEnabled=com.openpets.android.settings.SettingsDefaults.WS_ENABLED;d.bind=com.openpets.android.settings.SettingsDefaults.BIND_ADDRESS;d.ipcPort=com.openpets.android.settings.SettingsDefaults.IPC_PORT.toString();d.remotePort=com.openpets.android.settings.SettingsDefaults.REMOTE_PORT.toString();d.wsPort=com.openpets.android.settings.SettingsDefaults.WS_PORT.toString();d.requireLease=com.openpets.android.settings.SettingsDefaults.REQUIRE_LEASE;state.errors=emptyMap();forceRebindRows() }
    override fun sectionResetMessage(): String? = getString(com.openpets.android.R.string.settings_reset_connections_detail)
    override fun onSwitchChanged(key:String,value:Boolean) { state.draft?.let { d-> when(key){"ipcEnabled"->d.ipcEnabled=value;"remoteEnabled"->d.remoteEnabled=value;"wsEnabled"->d.wsEnabled=value;"requireLease"->d.requireLease=value} }; (list!!.adapter as? SettingsScreenAdapter)?.updateRowsByKey(rows(),key); val port=when(key){"ipcEnabled"->"ipcPort";"remoteEnabled"->"remotePort";"wsEnabled"->"wsPort";else->null};port?.let{(list!!.adapter as? SettingsScreenAdapter)?.updateRowsByKey(rows(),it)} }
    private fun statusChanged() { val adapter=list!!.adapter as? SettingsScreenAdapter ?:return; val index=rows().indexOfFirst { it.id=="status".hashCode().toLong() }; if(index>=0) adapter.replaceStatus(index, rows()[index]) }
    override fun onRowClicked(row:SettingRow) {
        if(row is SettingRow.SwitchDetailRow){ val index=rows().indexOfFirst { it.id==row.detailDestination.hashCode().toLong() }; if(index>=0)focusAfterLayout(index,row.detailDestination,null); return }
        when((row as? SettingRow.ActionRow)?.id) {
        "editIpc".hashCode().toLong() -> editToken(true)
        "editRemote".hashCode().toLong() -> editToken(false)
        "copyIpc".hashCode().toLong() -> copy(getString(com.openpets.android.R.string.settings_connections_ipc_token),state.draft!!.ipcToken)
        "copyRemote".hashCode().toLong() -> copy(getString(com.openpets.android.R.string.settings_connections_remote_token),state.draft!!.remoteToken)
    } }
    override fun onSave() {
        val d=state.draft?:return
        state.errors=validateConnectionDraft(d); refreshRows()
        if(state.errors.isNotEmpty()) { focusFirstError(); return }
        val bind=(SettingsValidators.bindAddress(d.bind) as SettingsValidators.Result.Ok).value
        val ipc=connectionPortForSave(d.ipcPort,d.ipcEnabled,appSettings.ipcPort,d.loadedIpcPort)
        val remote=connectionPortForSave(d.remotePort,d.remoteEnabled,appSettings.remotePort,d.loadedRemotePort)
        val ws=connectionPortForSave(d.wsPort,d.wsEnabled,appSettings.wsPort,d.loadedWsPort)
        val ipcToken=tokenForSave(d.ipcToken,d.ipcTokenEdited,true)
        val remoteToken=tokenForSave(d.remoteToken,d.remoteTokenEdited,false)
        appSettings.applyConnections(d.ipcEnabled,d.remoteEnabled,d.wsEnabled,bind,ipc,remote,ws,ipcToken,remoteToken,d.requireLease)
        OpenPetsService.reload(requireContext())
        Toast.makeText(requireContext(),com.openpets.android.R.string.settings_connections_saved,Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
    }
    private fun persistedDraft()=ConnectionDraft(appSettings.bindAddress,appSettings.ipcEnabled,appSettings.ipcPort.toString(),appSettings.remoteEnabled,appSettings.remotePort.toString(),appSettings.wsEnabled,appSettings.wsPort.toString(),appSettings.ipcToken,appSettings.remoteToken,appSettings.requireLease)
    override fun requestClose():Boolean {
        val d=state.draft?:return false
        if(d==persistedDraft() && !d.ipcTokenEdited && !d.remoteTokenEdited)return false
        AlertDialog.Builder(requireContext()).setTitle(com.openpets.android.R.string.ui_discard_title).setMessage(com.openpets.android.R.string.ui_unsaved_connections)
            .setPositiveButton(com.openpets.android.R.string.settings_discard) { _,_->onDiscard();parentFragmentManager.popBackStack() }
            .setNegativeButton(com.openpets.android.R.string.ui_keep_editing,null).show()
        return true
    }
    override fun onDiscard() { val rv=list?:return;rv.post { if(!isAdded||view==null||rv!==list)return@post;rv.findFocus()?.clearFocus();loadDraft();maskSecrets();refreshRows() } }
    private fun focusFirstError() { val key=state.errors.keys.firstOrNull() ?: return; val index=rows().indexOfFirst { it.id==key.hashCode().toLong() }; if(index>=0)focusAfterLayout(index,key,state.errors[key]?.let(::errorText)) }
    private fun focusAfterLayout(position:Int,tag:String,announce:String?) {
        val rv=list?:return
        rv.scrollToPosition(position)
        val observer=rv.viewTreeObserver
        lateinit var listener:android.view.ViewTreeObserver.OnPreDrawListener
        listener=object:android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw():Boolean {
                if(!isAdded||view==null||rv!==list){if(observer.isAlive)observer.removeOnPreDrawListener(this);pendingFocusListeners.removeAll{it.second===this};return true}
                val holder=rv.findViewHolderForAdapterPosition(position) ?: return true
                val target=holder.itemView.findViewWithTag<View>(tag) ?: holder.itemView
                target.requestFocus(); announce?.let(target::announceForAccessibility)
                if(observer.isAlive)observer.removeOnPreDrawListener(this)
                pendingFocusListeners.removeAll { it.second===this }
                return true
            }
        }
        pendingFocusListeners.add(observer to listener)
        observer.addOnPreDrawListener(listener)
    }
    private fun editToken(ipc:Boolean) {
        val d=state.draft?:return; val key=if(ipc)"ipcToken" else "remoteToken"
        val input=com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            setText(if(ipc)d.ipcToken else d.remoteToken)
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            if(android.os.Build.VERSION.SDK_INT>=26){ importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO; setAutofillHints() }
        }
        val layout=com.google.android.material.textfield.TextInputLayout(requireContext()).apply { addView(input); setPadding(24,8,24,0) }
        var visible=false
        val dialog=AlertDialog.Builder(requireContext()).setTitle(getString(if(ipc)com.openpets.android.R.string.settings_connections_edit_ipc else com.openpets.android.R.string.settings_connections_edit_remote)).setView(layout)
            .setNeutralButton(com.openpets.android.R.string.settings_connections_show_token,null).setPositiveButton(com.openpets.android.R.string.settings_save,null).setNegativeButton(com.openpets.android.R.string.settings_cancel,null).create()
        tokenDialog=dialog
        var dialogWasSecure=false
        dialog.setOnShowListener {
            dialogWasSecure=dialog.window?.let { it.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0 } ?: false
            dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            val toggle=dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            fun updateToggle(){ toggle.text=getString(if(visible)com.openpets.android.R.string.settings_connections_hide else com.openpets.android.R.string.settings_connections_show); toggle.contentDescription=getString(if(visible)com.openpets.android.R.string.settings_connections_hide_token else com.openpets.android.R.string.settings_connections_show_token_for, getString(if(ipc)com.openpets.android.R.string.settings_connections_ipc_token else com.openpets.android.R.string.settings_connections_remote_token)) }
            updateToggle()
            toggle.setOnClickListener { visible=!visible;input.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or if(visible)android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;input.transformationMethod=if(visible)null else android.text.method.PasswordTransformationMethod.getInstance(); updateToggle() }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value=input.text?.toString().orEmpty()
                val result=if(ipc)SettingsValidators.ipcToken(value) else SettingsValidators.remoteToken(value)
                if(result is SettingsValidators.Result.Invalid){layout.error=errorText(result.error);return@setOnClickListener}
                layout.error=null
                val persisted=if(ipc)appSettings.ipcToken else appSettings.remoteToken
                if(ipc){d.ipcToken=value;d.ipcTokenEdited=value!=persisted}else{d.remoteToken=value;d.remoteTokenEdited=value!=persisted}
                dialog.dismiss(); refreshRows()
            }
        }
        dialog.setOnDismissListener { if(!dialogWasSecure)dialog.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE); tokenDialog=null; visible=false }
        dialog.show()
    }
    private fun copy(label:String,value:String) { val cb=requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; val clip=ClipData.newPlainText(label,value); if(android.os.Build.VERSION.SDK_INT>=33) clip.description.extras=android.os.PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE,true) }; cb.setPrimaryClip(clip); Toast.makeText(requireContext(),if(value!=persisted(label)) getString(com.openpets.android.R.string.settings_connections_copied_unsaved) else getString(com.openpets.android.R.string.settings_connections_copied,label),Toast.LENGTH_SHORT).show() }
    private fun persisted(label:String)=if(label.startsWith("IPC"))appSettings.ipcToken else appSettings.remoteToken
    private fun setSecure(enabled:Boolean) { val w=requireActivity().window; if(enabled){priorSecure=w.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0;w.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}else if(!priorSecure)w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    private fun maskSecrets(){ revealed=false; tokenDialog?.dismiss() }
    private fun errorText(error:SettingsValidators.Error)=getString(when(error){SettingsValidators.Error.OUT_OF_RANGE->com.openpets.android.R.string.settings_error_port_range;SettingsValidators.Error.CONFLICT->com.openpets.android.R.string.settings_error_port_conflict;SettingsValidators.Error.TOO_SHORT->com.openpets.android.R.string.settings_error_too_short;SettingsValidators.Error.TOO_LONG->com.openpets.android.R.string.settings_error_too_long;SettingsValidators.Error.INVALID_CHARACTERS->com.openpets.android.R.string.settings_error_invalid_characters;else->com.openpets.android.R.string.settings_error_valid_value})
    private fun stateText(s:ServerState)=when(s){ServerState.Disabled->getString(com.openpets.android.R.string.settings_server_disabled);ServerState.Applying->getString(com.openpets.android.R.string.settings_server_applying);ServerState.Stopped->getString(com.openpets.android.R.string.settings_server_stopped);is ServerState.Listening->getString(com.openpets.android.R.string.settings_server_listening,s.port);is ServerState.Failed->getString(com.openpets.android.R.string.settings_server_failed,s.message)}
}

private const val STATE_UNSAVED_TOKEN_EDITS="unsaved_connection_token_edits"
