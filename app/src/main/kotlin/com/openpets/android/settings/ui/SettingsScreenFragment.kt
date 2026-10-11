package com.openpets.android.settings.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

abstract class SettingsScreenFragment : Fragment() {
    protected abstract val screenTitle: String
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);pendingFocusKey=savedInstanceState?.getString(STATE_TARGET_KEY)}
    override fun onSaveInstanceState(outState:Bundle){pendingFocusKey?.let{outState.putString(STATE_TARGET_KEY,it)};super.onSaveInstanceState(outState)}
    protected open val transactional: Boolean = false
    protected var list: RecyclerView? = null
    private var pendingFocusKey:String?=null
    protected var content: LinearLayout? = null
    protected abstract fun rows(): List<SettingRow>
    protected open fun onSave() {}
    protected open fun onDiscard() {}
    protected open fun onRowsBound() {}
    protected fun updateRowsIncrementally() { (list?.adapter as? SettingsScreenAdapter)?.updateChangedRows(rows()) }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val ctx=requireContext()
        content=LinearLayout(ctx).apply { orientation=LinearLayout.VERTICAL }
        val bar=LinearLayout(ctx).apply { gravity=Gravity.CENTER_VERTICAL; minimumHeight=dp(56); setPadding(dp(8),0,dp(8),0) }
        val back=TextView(ctx).apply { text="‹"; textSize=28f; gravity=Gravity.CENTER; contentDescription=getString(com.openpets.android.R.string.settings_back); setOnClickListener { if(!requestClose()) parentFragmentManager.popBackStack() } }
        bar.addView(back,LinearLayout.LayoutParams(dp(48),dp(48)))
        bar.addView(TextView(ctx).apply { text=screenTitle; textSize=20f; gravity=Gravity.CENTER_VERTICAL },LinearLayout.LayoutParams(0,dp(56),1f))
        content!!.addView(bar)
        val frame=FrameLayout(ctx)
        list=RecyclerView(ctx).apply {
            layoutManager=LinearLayoutManager(ctx)
            setPadding(dp(16),0,dp(16),0); clipToPadding=false
            adapter=createAdapter()
        }
        val wrapper=FrameLayout(ctx)
        wrapper.addView(list,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
        wrapper.addOnLayoutChangeListener { parent,_,_,_,_,_,_,_,_->
            val maxWidth=(600*ctx.resources.displayMetrics.density).toInt()
            val desired=parent.width.coerceAtMost(maxWidth)
            val current=list?:return@addOnLayoutChangeListener
            val params=current.layoutParams
            if(params.width!=desired){ params.width=desired; current.layoutParams=params; (params as? FrameLayout.LayoutParams)?.gravity=Gravity.CENTER }
        }
        frame.addView(wrapper,FrameLayout.LayoutParams(-1,-1))
        content!!.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        if(transactional) {
            val footer=LinearLayout(ctx).apply { setPadding(dp(16),dp(8),dp(16),dp(8)) }
            footer.orientation=LinearLayout.VERTICAL
            footer.addView(com.google.android.material.button.MaterialButton(ctx).apply { text=getString(com.openpets.android.R.string.settings_discard); setOnClickListener { onDiscard() } },LinearLayout.LayoutParams(-1,dp(48)))
            footer.addView(com.google.android.material.button.MaterialButton(ctx).apply { text=getString(com.openpets.android.R.string.settings_save); setOnClickListener { onSave() } },LinearLayout.LayoutParams(-1,dp(48)))
            content!!.addView(footer)
        }
        bar.post { if(isAdded&&view===content) bar.getChildAt(1)?.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED) }
        return content!!
    }
    protected open fun onRowClicked(row: SettingRow) {}
    protected fun sectionResetRow(): SettingRow = SettingRow.ActionRow("resetSection", getString(com.openpets.android.R.string.settings_reset_this_section), null)
    /** Screens override this to fill their draft with the section defaults (nothing is persisted). */
    protected open fun sectionResetDraft() {}
    /** Optional per-section consequence text shown in the reset confirmation. */
    protected open fun sectionResetMessage(): String? = null
    private fun handleRowClick(row: SettingRow) {
        if ((row as? SettingRow.ActionRow)?.key == "resetSection") {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(com.openpets.android.R.string.settings_reset_section_confirm)
                .setMessage(listOfNotNull(sectionResetMessage() ?: getString(com.openpets.android.R.string.settings_reset_section_consequences), getString(com.openpets.android.R.string.settings_reset_kept)).joinToString("\n\n"))
                .setPositiveButton(android.R.string.ok) { _,_ -> sectionResetDraft() }
                .setNegativeButton(com.openpets.android.R.string.ui_cancel, null)
                .show()
            return
        }
        onRowClicked(row)
    }
    open fun requestClose():Boolean = false
    fun setSearchTarget(key:String){pendingFocusKey=key;view?.post{performPendingFocus()}}
    private var pendingFocusListener:android.view.ViewTreeObserver.OnPreDrawListener?=null
    private fun clearPendingFocusListener(){pendingFocusListener?.let{l->val rv=list;if(rv!=null&&rv.viewTreeObserver.isAlive)rv.viewTreeObserver.removeOnPreDrawListener(l)};pendingFocusListener=null}
    private fun performPendingFocus(){val key=pendingFocusKey?:return;val rv=list?:return;val position=rows().indexOfFirst{row->when(row){is SettingRow.TextFieldRow->row.key==key;is SettingRow.SwitchRow->row.key==key;is SettingRow.SwitchDetailRow->row.key==key;is SettingRow.SecretRow->row.key==key;is SettingRow.InfoRow->row.key==key;is SettingRow.ActionRow->row.key==key;is SettingRow.SliderRow->row.key==key;is SettingRow.ChoiceRow->row.key==key;else->false}};if(position<0)return;rv.scrollToPosition(position);clearPendingFocusListener();var attempts=0;lateinit var listener:android.view.ViewTreeObserver.OnPreDrawListener;listener=android.view.ViewTreeObserver.OnPreDrawListener{if(!isAdded||rv!==list){clearPendingFocusListener();true}else{val holder=rv.findViewHolderForAdapterPosition(position);if(holder!=null){holder.itemView.requestFocus();pendingFocusKey=null;clearPendingFocusListener()}else if(++attempts<8){rv.scrollToPosition(position);rv.requestLayout()}else{clearPendingFocusListener()};true}};pendingFocusListener=listener;rv.viewTreeObserver.addOnPreDrawListener(listener)}
    fun scrollToRow(key:String){setSearchTarget(key)}
    protected fun refreshRows() { (list?.adapter as? SettingsScreenAdapter)?.replaceRows(rows()) }
    protected fun forceRebindRows() { list?.findFocus()?.clearFocus(); (list?.adapter as? SettingsScreenAdapter)?.replaceRows(rows()); (list?.adapter as? SettingsScreenAdapter)?.rebindAllIgnoringFocus() }
    private fun createAdapter() = SettingsScreenAdapter(rows(), { key,value->onFieldChanged(key,value) }, {key,value->onSwitchChanged(key,value)}, {handleRowClick(it)}, {key,value->onSliderChanged(key,value)}, {key,value->onChoiceChanged(key,value)})
    protected open fun onFieldChanged(key:String,value:String) {}
    protected open fun onSwitchChanged(key:String,value:Boolean) {}
    protected open fun onSliderChanged(key:String,value:Float) {}
    protected open fun onChoiceChanged(key:String,value:String) {}
    override fun onDestroyView() { list?.adapter=null;list=null;content=null;super.onDestroyView() }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private companion object{const val STATE_TARGET_KEY="settings_target_key"}
}
