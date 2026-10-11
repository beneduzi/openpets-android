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
    override val screenTitle="Connections & security"
    override val transactional=true
    private val state:ConnectionsDraftViewModel by viewModels()
    private lateinit var appSettings:AppSettings
    private var revealed=false
    private var priorSecure=false
    private var tokenDialog:AlertDialog?=null
    private val listener:(ServersStatus)->Unit={ status-> state.status=status; if(isAdded && view!=null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) activity?.runOnUiThread { statusChanged() } }
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); appSettings=AppSettings(requireContext()); if(state.draft==null){ showClearedTokenNotice=savedInstanceState?.getBoolean(STATE_UNSAVED_TOKEN_EDITS)==true; loadDraft() } }
    override fun onSaveInstanceState(outState:Bundle) { outState.putBoolean(STATE_UNSAVED_TOKEN_EDITS,state.draft?.let { it.ipcTokenEdited || it.remoteTokenEdited }==true); super.onSaveInstanceState(outState) }
    override fun onResume() { super.onResume(); if(showClearedTokenNotice){showClearedTokenNotice=false; Toast.makeText(requireContext(),"Unsaved token edits were cleared. Re-enter them to apply.",Toast.LENGTH_LONG).show()} }
    private fun loadDraft() { state.draft=ConnectionDraft(appSettings.bindAddress,appSettings.ipcEnabled,appSettings.ipcPort.toString(),appSettings.remoteEnabled,appSettings.remotePort.toString(),appSettings.wsEnabled,appSettings.wsPort.toString(),appSettings.ipcToken,appSettings.remoteToken,appSettings.requireLease); state.errors=emptyMap() }
    override fun onStart() { super.onStart(); state.status=ConnectionStatus.current; statusChanged(); ConnectionStatus.addListener(listener) }
    override fun onStop() { ConnectionStatus.removeListener(listener); maskSecrets(); super.onStop() }
    override fun onDestroyView() { maskSecrets(); tokenDialog?.dismiss(); tokenDialog=null; pendingFocusListeners.forEach { (observer,listener)->if(observer.isAlive)observer.removeOnPreDrawListener(listener) }; pendingFocusListeners.clear(); super.onDestroyView() }

    override fun rows():List<SettingRow> {
        val d=state.draft ?: return emptyList()
        fun err(key:String)=state.errors[key]?.let(::errorText)
        val statuses="IPC: ${stateText(state.status.ipc)}\nRemote: ${stateText(state.status.remote)}\nWebSocket: ${stateText(state.status.webSocket)}\nConfigured bind address: ${state.status.configuredBindAddress}"
        return listOf(
            SettingRow.TextFieldRow("bind","Bind address",d.bind,"Empty means all network interfaces",err("bind"),false),
            SettingRow.SectionHeader("Servers"),
            SettingRow.SwitchDetailRow("ipcEnabled","IPC",null,d.ipcEnabled,"ipcPort"), SettingRow.TextFieldRow("ipcPort","IPC port",d.ipcPort,null,err("ipcPort"),true,d.ipcEnabled),
            SettingRow.SwitchDetailRow("remoteEnabled","Remote",null,d.remoteEnabled,"remotePort"), SettingRow.TextFieldRow("remotePort","Remote port",d.remotePort,null,err("remotePort"),true,d.remoteEnabled),
            SettingRow.SwitchDetailRow("wsEnabled","WebSocket","Experimental; uses the IPC token",d.wsEnabled,"wsPort"), SettingRow.TextFieldRow("wsPort","WebSocket port",d.wsPort,null,err("wsPort"),true,d.wsEnabled),
            SettingRow.SectionHeader("Security"),
            SettingRow.InfoRow("ipcTokenDisplay","IPC token","••••••••"),
            SettingRow.ActionRow("editIpc","Edit IPC token",null),
            SettingRow.InfoRow("remoteTokenDisplay","Remote token","••••••••"),
            SettingRow.ActionRow("editRemote","Edit Remote token",null),
            SettingRow.SwitchRow("requireLease","Require lease","Applies to IPC/WebSocket commands, not Remote",d.requireLease),
            SettingRow.InfoRow("status","Effective server status",statuses),
            SettingRow.InfoRow("remoteWarning","Remote token warning",if(!d.remoteTokenEdited && !com.openpets.android.protocol.isValidRemoteToken(d.remoteToken)) "The saved Remote token is incompatible; saving other fields will preserve it." else "No warning"),
            SettingRow.ActionRow("copyIpc","Copy IPC token",null), SettingRow.ActionRow("copyRemote","Copy Remote token",null),
        )
    }
    override fun onFieldChanged(key:String,value:String) { state.draft?.let { d-> when(key){"bind"->d.bind=value;"ipcPort"->d.ipcPort=value;"remotePort"->d.remotePort=value;"wsPort"->d.wsPort=value;"ipcToken"->{d.ipcToken=value;d.ipcTokenEdited=true};"remoteToken"->{d.remoteToken=value;d.remoteTokenEdited=true} }; (list.adapter as? SettingsScreenAdapter)?.setFieldValue(key,value) } }
    override fun onSwitchChanged(key:String,value:Boolean) { state.draft?.let { d-> when(key){"ipcEnabled"->d.ipcEnabled=value;"remoteEnabled"->d.remoteEnabled=value;"wsEnabled"->d.wsEnabled=value;"requireLease"->d.requireLease=value} }; (list.adapter as? SettingsScreenAdapter)?.updateRowsByKey(rows(),key); val port=when(key){"ipcEnabled"->"ipcPort";"remoteEnabled"->"remotePort";"wsEnabled"->"wsPort";else->null};port?.let{(list.adapter as? SettingsScreenAdapter)?.updateRowsByKey(rows(),it)} }
    private fun statusChanged() { val adapter=list.adapter as? SettingsScreenAdapter ?:return; val index=rows().indexOfFirst { it.id=="status".hashCode().toLong() }; if(index>=0) adapter.replaceStatus(index, rows()[index]) }
    override fun onRowClicked(row:SettingRow) {
        if(row is SettingRow.SwitchDetailRow){ val index=rows().indexOfFirst { it.id==row.detailDestination.hashCode().toLong() }; if(index>=0)focusAfterLayout(index,row.detailDestination,null); return }
        when((row as? SettingRow.ActionRow)?.id) {
        "editIpc".hashCode().toLong() -> editToken(true)
        "editRemote".hashCode().toLong() -> editToken(false)
        "copyIpc".hashCode().toLong() -> copy("IPC token",state.draft!!.ipcToken)
        "copyRemote".hashCode().toLong() -> copy("Remote token",state.draft!!.remoteToken)
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
        Toast.makeText(requireContext(),"Preferences saved; reload requested",Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
    }
    private fun persistedDraft()=ConnectionDraft(appSettings.bindAddress,appSettings.ipcEnabled,appSettings.ipcPort.toString(),appSettings.remoteEnabled,appSettings.remotePort.toString(),appSettings.wsEnabled,appSettings.wsPort.toString(),appSettings.ipcToken,appSettings.remoteToken,appSettings.requireLease)
    override fun requestClose():Boolean {
        val d=state.draft?:return false
        if(d==persistedDraft() && !d.ipcTokenEdited && !d.remoteTokenEdited)return false
        AlertDialog.Builder(requireContext()).setTitle("Discard changes?").setMessage("Unsaved connection settings will be discarded.")
            .setPositiveButton("Discard") { _,_->onDiscard();parentFragmentManager.popBackStack() }
            .setNegativeButton("Keep editing",null).show()
        return true
    }
    override fun onDiscard() { list.post { list.findFocus()?.clearFocus(); loadDraft(); maskSecrets(); refreshRows() } }
    private fun focusFirstError() { val key=state.errors.keys.firstOrNull() ?: return; val index=rows().indexOfFirst { it.id==key.hashCode().toLong() }; if(index>=0)focusAfterLayout(index,key,state.errors[key]?.let(::errorText)) }
    private fun focusAfterLayout(position:Int,tag:String,announce:String?) {
        val rv=list
        rv.scrollToPosition(position)
        val observer=rv.viewTreeObserver
        lateinit var listener:android.view.ViewTreeObserver.OnPreDrawListener
        listener=object:android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw():Boolean {
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
        val dialog=AlertDialog.Builder(requireContext()).setTitle(if(ipc)"Edit IPC token" else "Edit Remote token").setView(layout)
            .setNeutralButton("Show token",null).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create()
        tokenDialog=dialog
        var dialogWasSecure=false
        dialog.setOnShowListener {
            dialogWasSecure=dialog.window?.let { it.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0 } ?: false
            dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            val toggle=dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            fun updateToggle(){ toggle.text=if(visible)"Hide" else "Show"; toggle.contentDescription="${if(visible)"Hide" else "Show"} ${if(ipc)"IPC" else "Remote"} token" }
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
    private fun copy(label:String,value:String) { val cb=requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; val clip=ClipData.newPlainText(label,value); if(android.os.Build.VERSION.SDK_INT>=33) clip.description.extras=android.os.PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE,true) }; cb.setPrimaryClip(clip); Toast.makeText(requireContext(),if(value!=persisted(label)) "Copied unsaved token. Save to activate it." else "Copied $label",Toast.LENGTH_SHORT).show() }
    private fun persisted(label:String)=if(label.startsWith("IPC"))appSettings.ipcToken else appSettings.remoteToken
    private fun setSecure(enabled:Boolean) { val w=requireActivity().window; if(enabled){priorSecure=w.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0;w.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}else if(!priorSecure)w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    private fun maskSecrets(){ revealed=false; tokenDialog?.dismiss() }
    private fun errorText(error:SettingsValidators.Error)=when(error){SettingsValidators.Error.OUT_OF_RANGE->"Enter a port from 1 to 65535.";SettingsValidators.Error.CONFLICT->"Enabled servers must use different ports.";SettingsValidators.Error.TOO_SHORT->"Value is too short.";SettingsValidators.Error.TOO_LONG->"Value is too long.";SettingsValidators.Error.INVALID_CHARACTERS->"Value contains invalid characters.";else->"Enter a valid value."}
    private fun stateText(s:ServerState)=when(s){ServerState.Disabled->"Disabled";ServerState.Applying->"Applying";ServerState.Stopped->"Stopped";is ServerState.Listening->"Listening on ${s.port}";is ServerState.Failed->"Failed: ${s.message}"}
}

private const val STATE_UNSAVED_TOKEN_EDITS="unsaved_connection_token_edits"
