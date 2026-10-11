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
    protected open val transactional: Boolean = false
    protected lateinit var list: RecyclerView
    protected lateinit var content: LinearLayout
    protected abstract fun rows(): List<SettingRow>
    protected open fun onSave() {}
    protected open fun onDiscard() {}
    protected open fun onRowsBound() {}

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val ctx=requireContext()
        content=LinearLayout(ctx).apply { orientation=LinearLayout.VERTICAL }
        val bar=LinearLayout(ctx).apply { gravity=Gravity.CENTER_VERTICAL; minimumHeight=dp(56); setPadding(dp(8),0,dp(8),0) }
        val back=TextView(ctx).apply { text="‹"; textSize=28f; gravity=Gravity.CENTER; contentDescription="Back"; setOnClickListener { if(!requestClose()) parentFragmentManager.popBackStack() } }
        bar.addView(back,LinearLayout.LayoutParams(dp(48),dp(48)))
        bar.addView(TextView(ctx).apply { text=screenTitle; textSize=20f; gravity=Gravity.CENTER_VERTICAL },LinearLayout.LayoutParams(0,dp(56),1f))
        content.addView(bar)
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
            val params=list.layoutParams
            if(params.width!=desired){ params.width=desired; list.layoutParams=params; (params as? FrameLayout.LayoutParams)?.gravity=Gravity.CENTER }
        }
        frame.addView(wrapper,FrameLayout.LayoutParams(-1,-1))
        content.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        if(transactional) {
            val footer=LinearLayout(ctx).apply { setPadding(dp(16),dp(8),dp(16),dp(8)) }
            footer.orientation=LinearLayout.VERTICAL
            footer.addView(com.google.android.material.button.MaterialButton(ctx).apply { text="Discard"; setOnClickListener { onDiscard() } },LinearLayout.LayoutParams(-1,dp(48)))
            footer.addView(com.google.android.material.button.MaterialButton(ctx).apply { text="Save"; setOnClickListener { onSave() } },LinearLayout.LayoutParams(-1,dp(48)))
            content.addView(footer)
        }
        return content
    }
    protected open fun onRowClicked(row: SettingRow) {}
    open fun requestClose():Boolean = false
    protected fun refreshRows() { (list.adapter as? SettingsScreenAdapter)?.replaceRows(rows()) }
    private fun createAdapter() = SettingsScreenAdapter(rows(), { key,value->onFieldChanged(key,value) }, {key,value->onSwitchChanged(key,value)}, {onRowClicked(it)})
    protected open fun onFieldChanged(key:String,value:String) {}
    protected open fun onSwitchChanged(key:String,value:Boolean) {}
    override fun onDestroyView() { list.adapter=null; super.onDestroyView() }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
}
