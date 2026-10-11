package com.openpets.android.settings.ui

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.openpets.android.MainActivity
import com.openpets.android.protocol.PetDescriptor

class PetsFragment:SettingsScreenFragment(){
    override val screenTitle get()=getString(com.openpets.android.R.string.ui_pets)
    private val host get()=requireActivity() as MainActivity
    override fun rows():List<SettingRow>{
        val pets=host.availablePets()
        val choices=pets.map { pet->pet.id to pet.displayName+when { pet.builtIn->getString(com.openpets.android.R.string.settings_pet_builtin_suffix);host.isImportedPet(pet.id)->getString(com.openpets.android.R.string.settings_pet_imported_suffix);else->"" } }
        return listOf(
            SettingRow.ChoiceRow("selectedPet",getString(com.openpets.android.R.string.ui_current_pet),choices,host.selectedPetId()),
            SettingRow.ActionRow("onlineCatalog",getString(com.openpets.android.R.string.ui_online_catalog),null),
            SettingRow.ActionRow("importZip",getString(com.openpets.android.R.string.ui_import_zip),null),
            SettingRow.ActionRow("importFolder",getString(com.openpets.android.R.string.ui_import_folder),null),
            SettingRow.ActionRow("deleteImported",getString(com.openpets.android.R.string.ui_delete_imported),null,host.isImportedPet(host.selectedPetId())),
        )
    }
    override fun onChoiceChanged(key:String,value:String){if(key!="selectedPet")return;host.selectPetById(value);(list!!.adapter as? SettingsScreenAdapter)?.setChoiceValue(key,value);(list!!.adapter as? SettingsScreenAdapter)?.updateRowsByKey(rows(),"deleteImported")}
    override fun onRowClicked(row:SettingRow){when((row as? SettingRow.ActionRow)?.key){
        "onlineCatalog"->host.openOnlinePetCatalog { if(isAdded&&view!=null)refreshRows() }
        "importZip"->host.launchPetZipImport { if(isAdded&&view!=null)refreshRows() }
        "importFolder"->host.launchPetFolderImport { if(isAdded&&view!=null)refreshRows() }
        "deleteImported"->{AlertDialog.Builder(requireContext()).setTitle(com.openpets.android.R.string.ui_delete_pet_title).setMessage(com.openpets.android.R.string.ui_delete_pet_message).setNegativeButton(com.openpets.android.R.string.ui_cancel,null).setPositiveButton(com.openpets.android.R.string.ui_delete){_,_->if(host.deleteSelectedImportedPet { if(isAdded&&view!=null)refreshRows() })refreshRows()}.show()}
    }}
}
