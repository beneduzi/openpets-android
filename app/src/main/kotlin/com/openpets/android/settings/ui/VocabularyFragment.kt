package com.openpets.android.settings.ui

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import com.openpets.android.MainActivity
import com.openpets.android.PetBus
import com.openpets.android.R
import com.openpets.android.protocol.SayRequest
import com.openpets.android.vocab.VocabDefinition

fun normalizeVocabularyLanguage(value:String)=value.trim()
fun normalizeAnkiHost(value:String)=value.trim()
fun isAnkiHostAvailable(value:String)=value.isNotBlank()
class VocabularyDraftViewModel:ViewModel(){var initialized=false;var language="en";var ankiHost="";var word="";var definitions="";var translation="";var definitionBusy=false;var translationBusy=false}

class VocabularyFragment:SettingsScreenFragment(){
    override val screenTitle="Vocabulary"
    override val transactional=true
    private val state:VocabularyDraftViewModel by viewModels()
    private var persistedLanguage="en";private var persistedHost=""
    private var requestGeneration=0L
    private var definitionGeneration=0L
    private var translationGeneration=0L
    companion object {
        private const val ARG_SHARED_TEXT="vocabulary_shared_text"
        fun newInstance(sharedText:String)=VocabularyFragment().apply{arguments=Bundle().apply{putString(ARG_SHARED_TEXT,sharedText)}}
    }
    override fun onViewCreated(view:android.view.View,savedInstanceState:Bundle?){super.onViewCreated(view,savedInstanceState);val fresh=!state.initialized;reloadPersistedConfig(false);if(fresh){state.initialized=true;state.language=persistedLanguage;state.ankiHost=persistedHost};state.word=initialWord(initialized=!fresh,retained=state.word,sharedText=arguments?.getString(ARG_SHARED_TEXT));updateRowsIncrementally()}
    override fun rows():List<SettingRow>{val hostAvailable=isAnkiHostAvailable(persistedHost);return listOf(
        SettingRow.TextFieldRow("vocabLanguage","Target language",state.language,"Language code",null,false),
        SettingRow.TextFieldRow("ankiHost","Anki host",state.ankiHost,"Host or host:port",null,false),
        SettingRow.SectionHeader("Look up a word"),
        SettingRow.TextFieldRow("lookupWord","Word",state.word,"Word or text",null,false),
        SettingRow.ActionRow("define","Define",null),SettingRow.InfoRow("definitions","Definitions",if(state.definitionBusy)"…" else state.definitions.ifBlank { "—" }),
        SettingRow.ActionRow("translate","Translate",null),SettingRow.InfoRow("translation","Translation",if(state.translationBusy)"…" else state.translation.ifBlank { "—" }),
        SettingRow.ActionRow("showVocab","Show on pet",null),
        SettingRow.ActionRow("addAnki",getString(R.string.vocab_anki),null,enabled=hostAvailable),
    )}
    override fun onFieldChanged(key:String,value:String){when(key){"vocabLanguage"->{state.language=value;invalidateLookupResults()};"ankiHost"->state.ankiHost=value;"lookupWord"->{state.word=value;invalidateLookupResults()}};(list?.adapter as? SettingsScreenAdapter)?.setFieldValue(key,value)}
    override fun onRowClicked(row:SettingRow){val key=(row as? SettingRow.ActionRow)?.key?:return;val host=requireActivity() as MainActivity;val word=state.word.trim();when(key){
        "define"->{if(word.isNotEmpty())define(host,word)}
        "translate"->{if(word.isNotEmpty())translate(host,word)}
        "showVocab"->{if(requestClose())return;val message=state.definitions.takeIf{it.isNotBlank()&&it!="…"}?:state.translation.takeIf{it.isNotBlank()&&it!="…"}?:word;if(message.isNotBlank()){PetBus.sink.onSay(SayRequest(message=message,reaction=null,source="vocab",bubbleId="vocab",pinned=false));host.revealStage()}}
        "addAnki"->{if(!isAnkiHostAvailable(persistedHost))return;val back=state.definitions.ifBlank{state.translation};val token=requestGeneration;val requestWord=word;val language=lookupRequestLanguage(persistedLanguage);host.vocabularyClientForSettings().addToAnki(persistedHost,requestWord,back){ok->if(isViewCurrent(token)&&state.word.trim()==requestWord&&persistedLanguage==language)android.widget.Toast.makeText(requireContext(),if(ok)getString(R.string.vocab_sent)else getString(R.string.vocab_error),android.widget.Toast.LENGTH_SHORT).show()}}
    }}
    private fun define(host:MainActivity,word:String){val generation=++definitionGeneration;val viewToken=requestGeneration;val language=lookupRequestLanguage(persistedLanguage);state.definitionBusy=true;state.definitions="";updateResultRows();host.vocabularyClientForSettings().define(word,{values->completeLookup(viewToken,generation,word,language,true,values.take(4).joinToString("\n"){formatDefinition(it)})},{error->if(isViewCurrent(viewToken))completeLookup(viewToken,generation,word,language,true,"${getString(R.string.vocab_error)}: $error")else completeLookup(viewToken,generation,word,language,true,"")})}
    private fun translate(host:MainActivity,word:String){val generation=++translationGeneration;val viewToken=requestGeneration;val language=lookupRequestLanguage(persistedLanguage);state.translationBusy=true;state.translation="";updateResultRows();host.vocabularyClientForSettings().translate(word,"auto",language,{value->completeLookup(viewToken,generation,word,language,false,value)},{error->if(isViewCurrent(viewToken))completeLookup(viewToken,generation,word,language,false,"${getString(R.string.vocab_error)}: $error")else completeLookup(viewToken,generation,word,language,false,"")})}
    private fun completeLookup(token:Long,generation:Long,word:String,language:String,definition:Boolean,value:String){val currentKey=LookupKey(state.word.trim(),persistedLanguage);val matches=lookupKeyMatches(LookupKey(word,language),currentKey);val latest=generation==(if(definition)definitionGeneration else translationGeneration);val accepted=shouldApplyLookupResult(isViewCurrent(token),token==requestGeneration&&latest,matches);val previous=if(definition)state.definitions else state.translation;val completion=reconcileLookupCompletion(LookupCompletion(if(definition)state.definitionBusy else state.translationBusy,previous),accepted,value,clearRejectedBusy=latest);if(definition){state.definitionBusy=completion.busy;state.definitions=completion.result}else{state.translationBusy=completion.busy;state.translation=completion.result};if(isViewCurrent())updateResultRows()}
    private fun invalidateLookupResults(){requestGeneration++;definitionGeneration++;translationGeneration++;state.definitionBusy=false;state.translationBusy=false;state.definitions="";state.translation="";updateResultRows()}
    private fun formatDefinition(definition:VocabDefinition)="${definition.partOfSpeech?.let{"$it — "}?:""}${definition.definition}"
    private fun updateResultRows(){(list?.adapter as? SettingsScreenAdapter)?.updateChangedRows(rows())}
    private fun isViewCurrent(token:Long=requestGeneration)=token==requestGeneration&&isAdded&&view!=null&&viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    private fun reloadPersistedConfig(updateDraft:Boolean){val host=activity as? MainActivity?:return;val config=host.vocabularySettingsForSettings();persistedLanguage=config.first;persistedHost=config.second;if(updateDraft){state.language=persistedLanguage;state.ankiHost=persistedHost}}
    override fun onResume(){super.onResume();reloadPersistedConfig(false);updateRowsIncrementally()}
    override fun onSave(){val host=requireActivity() as MainActivity;host.saveVocabularySettingsForSettings(normalizeVocabularyLanguage(state.language),normalizeAnkiHost(state.ankiHost));persistedLanguage=normalizeVocabularyLanguage(state.language);persistedHost=normalizeAnkiHost(state.ankiHost);state.language=persistedLanguage;state.ankiHost=persistedHost;forceRebindRows()}
    override fun onDiscard(){state.language=persistedLanguage;state.ankiHost=persistedHost;forceRebindRows()}
    override fun requestClose():Boolean{if(normalizeVocabularyLanguage(state.language)==persistedLanguage&&normalizeAnkiHost(state.ankiHost)==persistedHost)return false;AlertDialog.Builder(requireContext()).setTitle("Discard changes?").setMessage("Unsaved Vocabulary settings will be discarded.").setPositiveButton("Discard"){_,_->onDiscard();parentFragmentManager.popBackStack()}.setNegativeButton("Keep editing",null).show();return true}
    override fun onDestroyView(){requestGeneration++;definitionGeneration++;translationGeneration++;state.definitionBusy=false;state.translationBusy=false;state.definitions=state.definitions.takeUnless{it=="…"}.orEmpty();state.translation=state.translation.takeUnless{it=="…"}.orEmpty();super.onDestroyView()}
}
