package com.openpets.android.settings.ui

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SettingsScreenAdapter(
    private var rows: List<SettingRow>,
    private val changed: (String, String) -> Unit = { _, _ -> },
    private val toggled: (String, Boolean) -> Unit = { _, _ -> },
    private val clicked: (SettingRow) -> Unit = {},
) : RecyclerView.Adapter<SettingsScreenAdapter.Holder>() {
    fun replaceRows(value: List<SettingRow>) { rows=value; notifyDataSetChanged() }
    fun replaceStatus(index:Int,row:SettingRow) { val mutable=rows.toMutableList(); if(index in mutable.indices){ mutable[index]=row; rows=mutable; notifyItemChanged(index) } }
    init { setHasStableIds(true) }
    override fun getItemId(position: Int) = rows[position].id
    override fun getItemCount() = rows.size
    override fun getItemViewType(position: Int) = when (rows[position]) {
        is SettingRow.SectionHeader -> 0
        is SettingRow.TextFieldRow -> 1
        is SettingRow.SecretRow -> 2
        is SettingRow.SwitchRow, is SettingRow.SwitchDetailRow -> 3
        else -> 4
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(0), dp(12), dp(0), dp(12)); gravity = Gravity.CENTER_VERTICAL }
        return Holder(layout)
    }
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val root = holder.itemView as LinearLayout
        root.setOnClickListener(null)
        val row = rows[position]
        if (row is SettingRow.TextFieldRow || row is SettingRow.SecretRow) {
            holder.watcher?.let { (root.getChildAt(1) as? com.google.android.material.textfield.TextInputLayout)?.editText?.removeTextChangedListener(it) }
            holder.watcher=null
            val key: String
            val title: String
            val value: String
            val hint: String?
            val error: String?
            val numeric: Boolean
            val secret: Boolean
            val enabled: Boolean
            when(row) {
                is SettingRow.TextFieldRow -> { key=row.key; title=row.title; value=row.value; hint=row.hint; error=row.error; numeric=row.numeric; secret=false; enabled=row.enabled }
                is SettingRow.SecretRow -> { key=row.key; title=row.title; value=row.value; hint=null; error=row.error; numeric=false; secret=true; enabled=true }
                else -> return
            }
            val existing = (root.getChildAt(1) as? com.google.android.material.textfield.TextInputLayout)?.editText
            val label = (root.getChildAt(0) as? TextView) ?: TextView(root.context).also { root.addView(it,0) }
            label.text = title; label.textSize = 16f
            val field = if(existing==null) com.google.android.material.textfield.TextInputLayout(root.context).also { it.addView(com.google.android.material.textfield.TextInputEditText(root.context)); root.addView(it) } else root.getChildAt(1) as com.google.android.material.textfield.TextInputLayout
            val edit = field.editText!!
            edit.tag = key
            edit.isEnabled=enabled
            edit.hint = hint
            val desiredInputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
            if (edit.inputType != desiredInputType) edit.inputType = desiredInputType
            edit.error = error
            if (!holder.itemView.hasFocus() && !edit.hasFocus() && edit.text.toString() != value) edit.setText(value)
            holder.watcher=object:android.text.TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { changed(key,s?.toString().orEmpty()) }
                override fun afterTextChanged(s:android.text.Editable?) {}
            }.also(edit::addTextChangedListener)
            edit.setOnFocusChangeListener { _, _ -> }
            edit.setOnEditorActionListener { _,_,_ -> changed(key, edit.text.toString()); false }
        } else {
            holder.watcher?.let { (root.getChildAt(1) as? com.google.android.material.textfield.TextInputLayout)?.editText?.removeTextChangedListener(it) }
            holder.watcher=null
            root.removeAllViews()
            when (row) {
                is SettingRow.SectionHeader -> TextView(root.context).apply { text=row.title; textSize=16f; root.addView(this); root.setPadding(0,dp(24),0,dp(8)) }
                is SettingRow.SwitchRow -> addSwitch(root,row.title,row.summary,row.checked) { toggled(row.key,it) }
                is SettingRow.SwitchDetailRow -> {
                    addSwitch(root,row.title,row.summary,row.checked) { toggled(row.key,it) }
                    val detail=TextView(root.context).apply { text="Details  ›"; contentDescription="Open ${row.title} details"; isClickable=true; minimumHeight=dp(48); setOnClickListener { clicked(row) } }; root.addView(detail)
                }
                is SettingRow.InfoRow -> { addText(root,row.title + "\n" + row.value, false); row.actionLabel?.let { label-> root.addView(com.google.android.material.button.MaterialButton(root.context).apply { text=label; setOnClickListener { clicked(row) } }) } }
                is SettingRow.ActionRow -> { addText(root, row.title + (row.summary?.let { "\n$it" } ?: ""), false); root.isClickable=true;root.setOnClickListener { clicked(row) } }
                else -> Unit
            }
        }
    }
    fun updateRowsByKey(updated:List<SettingRow>,key:String) {
        val position=rows.indexOfFirst { it.id==key.hashCode().toLong() }
        if(position>=0 && position<updated.size){ rows=rows.toMutableList().also { it[position]=updated[position] }; notifyItemChanged(position) }
    }
    fun setFieldValue(key:String,value:String) {
        val position=rows.indexOfFirst { it.id==key.hashCode().toLong() }
        if(position<0)return
        rows=rows.toMutableList().also { items-> items[position]=when(val row=items[position]) {
            is SettingRow.TextFieldRow -> row.copy(value=value)
            is SettingRow.SecretRow -> row.copy(value=value)
            else -> row
        } }
    }
    override fun onViewRecycled(holder: Holder) { holder.itemView.setOnClickListener(null); holder.watcher?.let { (holder.itemView as LinearLayout).getChildAt(1)?.let { child -> (child as? com.google.android.material.textfield.TextInputLayout)?.editText?.removeTextChangedListener(it) } }; holder.watcher=null; super.onViewRecycled(holder) }
    private fun addSwitch(root: LinearLayout,title:String,summary:String?,checked:Boolean,onChange:(Boolean)->Unit) {
        val button=com.google.android.material.materialswitch.MaterialSwitch(root.context).apply { text=title; isChecked=checked; minimumHeight=dp(48); setOnCheckedChangeListener { _,v->onChange(v) } }
        root.addView(button)
        summary?.let { addText(root,it,false,14f) }
    }
    private fun addText(root:LinearLayout,value:String,clickable:Boolean,size:Float=16f) { root.addView(TextView(root.context).apply { text=value; textSize=size; isClickable=clickable; minimumHeight=dp(48) }) }
    private fun dp(value:Int)=(value*android.content.res.Resources.getSystem().displayMetrics.density).toInt()
    class Holder(view: View):RecyclerView.ViewHolder(view) { var watcher:android.text.TextWatcher?=null }
}
