package com.openpets.android.settings.ui

import android.widget.Toast
import com.openpets.android.MainActivity
import com.openpets.android.protocol.PetDescriptor

class PetsFragment:SettingsScreenFragment(){
    override val screenTitle="Pets"
    private val host get()=requireActivity() as MainActivity
    override fun rows():List<SettingRow>{
        val pets=host.availablePets()
        val choices=pets.map { pet->pet.id to pet.displayName+when { pet.builtIn->" (built-in)";host.isImportedPet(pet.id)->" (imported)";else->"" } }
        return listOf(
            SettingRow.ChoiceRow("selectedPet","Current pet",choices,host.selectedPetId()),
            SettingRow.ActionRow("onlineCatalog","Online catalog…",null),
            SettingRow.ActionRow("importZip","Import .zip…",null),
            SettingRow.ActionRow("importFolder","Import folder…",null),
            SettingRow.ActionRow("deleteImported","Delete imported pet",null,host.isImportedPet(host.selectedPetId())),
        )
    }
    override fun onChoiceChanged(key:String,value:String){if(key!="selectedPet")return;host.selectPetById(value);(list!!.adapter as? SettingsScreenAdapter)?.setChoiceValue(key,value);(list!!.adapter as? SettingsScreenAdapter)?.updateRowsByKey(rows(),"deleteImported")}
    override fun onRowClicked(row:SettingRow){when((row as? SettingRow.ActionRow)?.key){
        "onlineCatalog"->host.openOnlinePetCatalog { if(isAdded&&view!=null)refreshRows() }
        "importZip"->host.launchPetZipImport { if(isAdded&&view!=null)refreshRows() }
        "importFolder"->host.launchPetFolderImport { if(isAdded&&view!=null)refreshRows() }
        "deleteImported"->{if(host.deleteSelectedImportedPet { if(isAdded&&view!=null)refreshRows() })refreshRows()}
    }}
}
