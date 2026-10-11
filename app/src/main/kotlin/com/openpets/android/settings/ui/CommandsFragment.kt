package com.openpets.android.settings.ui

import com.openpets.android.MainActivity
import com.openpets.android.PetBus
import com.openpets.android.net.RemoteCommand

class CommandsFragment:SettingsScreenFragment(){
    override val screenTitle get()=getString(com.openpets.android.R.string.ui_plugin_commands)
    private var commandByRowKey=emptyMap<String,RemoteCommand>()
    override fun rows():List<SettingRow>{
        val commands=(requireActivity() as MainActivity).pluginCommandsForSettings()
        commandByRowKey=commands.associateBy(::commandRowKey)
        val ids=commandRowIds(commands.map(::commandRowKey))
        val rows=commands.map{command->val key=commandRowKey(command);SettingRow.ActionRow(key,command.title,command.description?.takeIf{it.isNotBlank()}?:command.source,id=ids.getValue(key))}.toMutableList<SettingRow>()
        if(rows.isEmpty())rows+=SettingRow.InfoRow("emptyCommands",getString(com.openpets.android.R.string.ui_plugin_commands),getString(com.openpets.android.R.string.ui_no_commands),id=ids.getValue("emptyCommands"))
        rows+=SettingRow.ActionRow("refreshCommands",getString(com.openpets.android.R.string.ui_refresh),null,id=ids.getValue("refreshCommands"))
        return rows
    }
    override fun onRowClicked(row:SettingRow){
        val action=row as? SettingRow.ActionRow?:return
        if(action.key=="refreshCommands"){(list!!.adapter as? SettingsScreenAdapter)?.updateChangedRows(rows());return}
        val command=commandByRowKey[action.key]?:return
        PetBus.invokeCommand(command.source,command.id)
    }
}

fun commandRowKey(command:RemoteCommand)="cmd|${command.source.length}|${command.source}|${command.id}"
fun commandRowIds(keys:List<String>):Map<String,Long>{val all=(keys+FIXED_COMMAND_ROWS).distinct().sorted();val ids=uniqueSettingRowIds(all);return all.zip(ids).toMap()}
private val FIXED_COMMAND_ROWS=listOf("emptyCommands","refreshCommands")
