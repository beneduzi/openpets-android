package com.openpets.android.settings.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.text.TextWatcher
import android.text.Editable
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.openpets.android.settings.AppSettings
import com.openpets.android.MainActivity

interface SettingsHost {
    fun openSettingsDestination(destination: SettingsDestination)
    fun closeSettings()
    fun revealStage()
    fun requestSettingsPermission(permission:String,requestCode:Int)
    fun openSettingsSearchResult(destination:SettingsDestination,targetKey:String?)
    fun showPreferenceResetChooser()
}

interface SettingsPermissionResultHandler { fun onSettingsPermissionResult(requestCode:Int,granted:Boolean) }
interface SettingsSearchBackHandler { fun closeSearchOnBack():Boolean }

class SettingsOverviewFragment : Fragment(),SettingsSearchBackHandler {
    private var recycler:RecyclerView?=null
    private var searchField:EditText?=null
    private var searching=false
    private var query=""
    private var searchEntries=emptyMap<Long,SettingsSearchEntry>()
    private var recyclerState: android.os.Parcelable? = null
    override fun onSaveInstanceState(outState: Bundle) {
        recyclerState = view?.findViewById<RecyclerView>(OVERVIEW_LIST_ID)?.layoutManager?.onSaveInstanceState()
        recyclerState?.let { outState.putParcelable("overview_scroll", it) }
        super.onSaveInstanceState(outState)
    }
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        recyclerState = state?.getParcelable("overview_scroll")
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setOnTouchListener { _, _ -> false }
        }
        val bar = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(56); setPadding(dp(8), 0, dp(8), 0) }
        val title = TextView(context).apply { text = getString(com.openpets.android.R.string.settings_title); textSize = 20f; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(title, LinearLayout.LayoutParams(0, dp(56), 1f))
        val search=TextView(context).apply{text="⌕";textSize=24f;gravity=Gravity.CENTER;contentDescription=getString(com.openpets.android.R.string.settings_search_action);setOnClickListener{expandSearch(bar)}}
        bar.addView(search,LinearLayout.LayoutParams(dp(48),dp(48)))
        val close = TextView(context).apply { text = "×"; textSize = 28f; gravity = Gravity.CENTER; contentDescription = getString(com.openpets.android.R.string.settings_overview_close); isClickable = true; setOnClickListener { (requireActivity() as SettingsHost).closeSettings() } }
        bar.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(bar)
        recycler = RecyclerView(context).apply {
            id = OVERVIEW_LIST_ID
            setPadding(dp(16), 0, dp(16), 0); clipToPadding = false
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
            adapter = SettingsAdapter(overviewRows((requireActivity() as MainActivity).selectedPetName(), AppSettings.findLanIpv4())) { row ->
                if(row.destination==SettingsDestination.RESET_PREFERENCES)(requireActivity() as SettingsHost).showPreferenceResetChooser()
                else {val entry=searchEntries[row.id];if(searching&&entry!=null)(requireActivity() as SettingsHost).openSettingsSearchResult(entry.destination,entry.targetKey) else (requireActivity() as SettingsHost).openSettingsDestination(row.destination)}
            }
        }
        val restore=recyclerState
        val rv=recycler
        if(restore!=null&&rv!=null)rv.post{if(!isAdded||view!==root||rv!==recycler)return@post;rv.layoutManager?.onRestoreInstanceState(restore)}
        if(searching)expandSearch(bar)
        val centered = FrameLayout(context)
        centered.addView(recycler, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        root.addView(centered, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }
    private fun expandSearch(bar:LinearLayout){if(searchField!=null)return;searching=true;val context=requireContext();searchField=EditText(context).apply{hint=getString(com.openpets.android.R.string.settings_search_action);contentDescription=hint;setSingleLine(true);setText(query);addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){query=s?.toString().orEmpty();showSearchResults()};override fun afterTextChanged(s:Editable?){}})};bar.addView(searchField,1,LinearLayout.LayoutParams(0,dp(48),1f));(bar.getChildAt(0) as? TextView)?.text=getString(com.openpets.android.R.string.settings_title);searchField?.requestFocus();showSearchResults()}
    private fun showSearchResults(){val rv=recycler?:return;if(!searching){(rv.adapter as? SettingsAdapter)?.replaceRows(overviewRows((requireActivity() as MainActivity).selectedPetName(),AppSettings.findLanIpv4()));return};val entries=searchSettings(query,resolve={getString(it)});searchEntries=entries.associateBy{it.hashCode().toLong()};(rv.adapter as? SettingsAdapter)?.replaceRows(entries.map{Row.Navigation(it.titleRes,it.summaryRes,it.destination,it.hashCode().toLong(),it.breadcrumbRes)})}
    override fun closeSearchOnBack():Boolean{if(!searching)return false;searching=false;query="";searchField?.let{(it.parent as? ViewGroup)?.removeView(it)};searchField=null;view?.findViewById<LinearLayout>(android.R.id.content)?.let{};showSearchResults();return true}
    override fun onResume(){super.onResume();if(searching)showSearchResults()else{val rv=recycler?:return;(rv.adapter as? SettingsAdapter)?.replaceRows(overviewRows((requireActivity() as MainActivity).selectedPetName(),AppSettings.findLanIpv4()))};recycler?.let{it.isFocusable=true;it.requestFocus()}}
    fun refreshOverview(){if(searching)showSearchResults()else{val rv=recycler?:return;(rv.adapter as? SettingsAdapter)?.replaceRows(overviewRows((requireActivity() as MainActivity).selectedPetName(),AppSettings.findLanIpv4()))}}
    override fun onDestroyView(){recycler=null;searchField=null;super.onDestroyView()}
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

private const val OVERVIEW_LIST_ID = 0x0a11cf
