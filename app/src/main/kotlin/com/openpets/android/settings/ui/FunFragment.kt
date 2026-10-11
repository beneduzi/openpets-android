package com.openpets.android.settings.ui

import com.openpets.android.MainActivity

class FunFragment:SettingsScreenFragment(){
    override val screenTitle get()=getString(com.openpets.android.R.string.ui_fun)
    override fun rows()=listOf(SettingRow.ActionRow("greeting",getString(com.openpets.android.R.string.ui_greeting),null),SettingRow.ActionRow("fortune",getString(com.openpets.android.R.string.ui_fortune),null),SettingRow.ActionRow("eightBall",getString(com.openpets.android.R.string.fun_eightball),null))
    override fun onRowClicked(row:SettingRow){val host=requireActivity() as MainActivity;when((row as? SettingRow.ActionRow)?.key){
        "greeting"->{host.sayFunMessageForSettings(host.funGreetingForSettings(),"greeting","waving");host.revealStage()}
        "fortune"->{host.sayFunMessageForSettings(host.funFortuneForSettings(),"fortune","thinking");host.revealStage()}
        "eightBall"->host.showEightBallForSettings{if(isAdded&&view!=null)host.revealStage()}
    }}
}
