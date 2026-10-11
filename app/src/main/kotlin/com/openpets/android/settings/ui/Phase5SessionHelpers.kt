package com.openpets.android.settings.ui

fun shouldRunSessionTicker(active:Boolean)=active
class CallbackEpoch { var value=0L; fun advance()=++value; fun isCurrent(token:Long)=token==value }
fun <T> reacquireViewDependency(provider:()->T?):T?=provider()
/** Shared-text/argument word must only apply on first initialization, never overwrite a retained draft. */
fun initialWord(initialized:Boolean,retained:String,sharedText:String?):String=if(initialized)retained else sharedText.orEmpty()
data class LookupKey(val word:String,val language:String)
fun lookupKeyMatches(request:LookupKey,current:LookupKey)=request==current
fun rejectedLookupBusy(busy:Boolean,latestRequest:Boolean)=busy&&latestRequest
fun lookupRequestLanguage(persisted:String)=persisted
fun savedLanguageChanged(previous:String,current:String)=previous!=current
fun <T> discardThen(onDiscard:()->Unit,action:()->T):T { onDiscard();return action() }
fun shouldApplyLookupResult(viewCurrent:Boolean,requestCurrent:Boolean,keyMatches:Boolean)=viewCurrent&&requestCurrent&&keyMatches
data class LookupCompletion(val busy:Boolean,val result:String)
fun reconcileLookupCompletion(completion:LookupCompletion,accepted:Boolean,value:String="",clearRejectedBusy:Boolean=true):LookupCompletion =
    if(accepted) LookupCompletion(false,value) else LookupCompletion(completion.busy&&!clearRejectedBusy,completion.result.takeUnless{it=="…"}.orEmpty())
