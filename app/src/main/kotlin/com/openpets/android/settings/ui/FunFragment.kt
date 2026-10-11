package com.openpets.android.settings.ui

import com.openpets.android.MainActivity

class FunFragment:SettingsScreenFragment(){
    override val screenTitle="Fun"
    override fun rows()=listOf(SettingRow.ActionRow("greeting","Greeting",null),SettingRow.ActionRow("fortune","Fortune",null),SettingRow.ActionRow("eightBall","8-ball",null))
    override fun onRowClicked(row:SettingRow){val host=requireActivity() as MainActivity;when((row as? SettingRow.ActionRow)?.key){
        "greeting"->{host.sayFunMessageForSettings(host.funGreetingForSettings(),"greeting","waving");host.revealStage()}
        "fortune"->{host.sayFunMessageForSettings(host.funFortuneForSettings(),"fortune","thinking");host.revealStage()}
        "eightBall"->host.showEightBallForSettings{if(isAdded&&view!=null)host.revealStage()}
    }}
}
