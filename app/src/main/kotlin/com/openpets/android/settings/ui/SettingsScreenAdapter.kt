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
    private val sliderChanged:(String,Float)->Unit={_,_->},
    private val choiceChanged:(String,String)->Unit={_,_->},
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
        is SettingRow.SliderRow -> 5
        is SettingRow.ChoiceRow -> 6
        else -> 4
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(0), dp(12), dp(0), dp(12)); gravity = Gravity.CENTER_VERTICAL }
        return Holder(layout)
    }
    override fun onBindViewHolder(holder: Holder, position: Int) = bind(holder,position)
    override fun onBindViewHolder(holder:Holder,position:Int,payloads:MutableList<Any>){
        if(payloads.contains("info-in-place")){val row=rows[position] as? SettingRow.InfoRow;val text=(holder.itemView as? LinearLayout)?.getChildAt(0) as? TextView;if(row!=null&&text!=null){text.text=row.title+"\n"+row.value;return}}
        bind(holder,position)
    }
    private fun bind(holder:Holder,position:Int) {
        val root = holder.itemView as LinearLayout
        root.setOnClickListener(null)
        root.isEnabled=true; root.isClickable=false
        holder.slider?.let { slider->holder.sliderListener?.let(slider::removeOnChangeListener) }
        holder.radioGroup?.setOnCheckedChangeListener(null)
        holder.slider=null;holder.sliderListener=null;holder.radioGroup=null
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
                is SettingRow.ActionRow -> { addText(root, row.title + (row.summary?.let { "\n$it" } ?: ""), false); root.isEnabled=row.enabled; root.isClickable=row.enabled;root.setOnClickListener(if(row.enabled){{clicked(row)}}else null) }
                is SettingRow.SliderRow -> {
                    val label=TextView(root.context).apply { text="${row.title}  ${row.valueLabel}"; tag="sliderLabel:${row.key}"; textSize=16f; labelFor=View.generateViewId() }
                    root.addView(label)
                    val slider=com.google.android.material.slider.Slider(root.context).apply { id=View.generateViewId();tag=row.key; valueFrom=row.min; valueTo=row.max; stepSize=0f; value=row.value; contentDescription="${row.title}, ${row.valueLabel} of ${row.min}–${row.max}" }
                    holder.sliderListener=com.google.android.material.slider.Slider.OnChangeListener { _,v,fromUser->if(fromUser)sliderChanged(row.key,v) }.also(slider::addOnChangeListener)
                    holder.slider=slider
                    label.labelFor=slider.id
                    root.addView(slider)
                }
                is SettingRow.ChoiceRow -> {
                    addText(root,row.title,false)
                    val group=android.widget.RadioGroup(root.context).apply { orientation=android.widget.RadioGroup.VERTICAL }
                    row.options.forEach { (key,label)->
                        val radio=android.widget.RadioButton(root.context).apply { id=View.generateViewId(); text=label; tag=key; isChecked=key==row.selectedKey; minimumHeight=dp(48) }
                        group.addView(radio)
                    }
                    group.setOnCheckedChangeListener { g,checkedId-> val checked=g.findViewById<android.widget.RadioButton>(checkedId); if(checked!=null)choiceChanged(row.key,checked.tag.toString()) }
                    group.contentDescription=row.title
                    holder.radioGroup=group
                    root.addView(group)
                }
                else -> Unit
            }
        }
    }
    fun updateRowsByKey(updated:List<SettingRow>,key:String) {
        val position=rows.indexOfFirst { it.id==key.hashCode().toLong() }
        if(position>=0 && position<updated.size){ rows=rows.toMutableList().also { it[position]=updated[position] }; notifyItemChanged(position) }
    }
    fun updateChangedRows(updated:List<SettingRow>) {
        val working=rows.toMutableList()
        val targetIds=updated.map{it.id}.toSet()
        for(i in working.lastIndex downTo 0)if(working[i].id !in targetIds){working.removeAt(i);rows=working.toList();notifyItemRemoved(i)}
        updated.forEachIndexed{index,row->
            val at=working.indexOfFirst{it.id==row.id}
            if(at<0){val insertAt=index.coerceAtMost(working.size);working.add(insertAt,row);rows=working.toList();notifyItemInserted(insertAt)}
            else if(at!=index){val moved=working.removeAt(at);working.add(index,moved);rows=working.toList();notifyItemMoved(at,index)}
            if(working[index]!=row){val previous=working[index];working[index]=row;rows=working.toList();if(previous is SettingRow.InfoRow&&row is SettingRow.InfoRow)notifyItemChanged(index,"info-in-place")else notifyItemChanged(index)}
        }
    }
    fun setFieldValue(key:String,value:String) {
        rows=withTextFieldValue(rows,key,value)
    }
    fun setSliderValue(key:String,value:Float){ val p=rows.indexOfFirst{it.id==key.hashCode().toLong()};if(p>=0)rows=rows.toMutableList().also{it[p]=(it[p] as SettingRow.SliderRow).copy(value=value,valueLabel=formatWalkaboutSpeed(value))} }
    fun setChoiceValue(key:String,value:String){ val p=rows.indexOfFirst{it.id==key.hashCode().toLong()};if(p>=0)rows=rows.toMutableList().also{it[p]=(it[p] as SettingRow.ChoiceRow).copy(selectedKey=value)} }
    override fun onViewRecycled(holder: Holder) { holder.itemView.setOnClickListener(null); holder.slider?.let{slider->holder.sliderListener?.let(slider::removeOnChangeListener)};holder.radioGroup?.setOnCheckedChangeListener(null);holder.slider=null;holder.sliderListener=null;holder.radioGroup=null;holder.watcher?.let { (holder.itemView as LinearLayout).getChildAt(1)?.let { child -> (child as? com.google.android.material.textfield.TextInputLayout)?.editText?.removeTextChangedListener(it) } }; holder.watcher=null; super.onViewRecycled(holder) }
    fun rebindAllIgnoringFocus(){notifyDataSetChanged()}
    private fun addSwitch(root: LinearLayout,title:String,summary:String?,checked:Boolean,onChange:(Boolean)->Unit) {
        val button=com.google.android.material.materialswitch.MaterialSwitch(root.context).apply { text=title; isChecked=checked; minimumHeight=dp(48); setOnCheckedChangeListener { _,v->onChange(v) } }
        root.addView(button)
        summary?.let { addText(root,it,false,14f) }
    }
    private fun addText(root:LinearLayout,value:String,clickable:Boolean,size:Float=16f) { root.addView(TextView(root.context).apply { text=value; textSize=size; isClickable=clickable; minimumHeight=dp(48) }) }
    private fun dp(value:Int)=(value*android.content.res.Resources.getSystem().displayMetrics.density).toInt()
    class Holder(view: View):RecyclerView.ViewHolder(view) { var watcher:android.text.TextWatcher?=null;var slider:com.google.android.material.slider.Slider?=null;var sliderListener:com.google.android.material.slider.Slider.OnChangeListener?=null;var radioGroup:android.widget.RadioGroup?=null }
}
