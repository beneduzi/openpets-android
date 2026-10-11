package com.openpets.android.settings.ui

import com.openpets.android.MainActivity
import com.openpets.android.PetBus
import com.openpets.android.net.RemoteCommand

class CommandsFragment:SettingsScreenFragment(){
    override val screenTitle="Plugin commands"
    private var commandByRowKey=emptyMap<String,RemoteCommand>()
    override fun rows():List<SettingRow>{
        val commands=(requireActivity() as MainActivity).pluginCommandsForSettings()
        commandByRowKey=commands.mapIndexed{i,command->"command:$i" to command}.toMap()
        val rows=commands.mapIndexed{i,command->SettingRow.ActionRow("command:$i",command.title,command.description?.takeIf{it.isNotBlank()}?:command.source)}.toMutableList<SettingRow>()
        if(rows.isEmpty())rows+=SettingRow.InfoRow("emptyCommands","Plugin commands","No commands registered")
        rows+=SettingRow.ActionRow("refreshCommands","Refresh",null)
        return rows
    }
    override fun onRowClicked(row:SettingRow){
        val action=row as? SettingRow.ActionRow?:return
        if(action.key=="refreshCommands"){(list!!.adapter as? SettingsScreenAdapter)?.updateChangedRows(rows());return}
        val command=commandByRowKey[action.key]?:return
        PetBus.invokeCommand(command.source,command.id)
    }
}
