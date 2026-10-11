package com.openpets.android.settings.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SettingsAdapter(
    private var rows: List<Row>,
    private val onSelect: (SettingsDestination) -> Unit,
) : RecyclerView.Adapter<SettingsAdapter.Holder>() {
    fun replaceRows(newRows:List<Row>){ rows=newRows; notifyDataSetChanged() }
    init { setHasStableIds(true) }
    override fun getItemId(position: Int) = rows[position].id
    override fun getItemViewType(position: Int) = when (rows[position]) {
        is Row.SectionHeader -> 0
        is Row.Navigation -> 1
        is Row.Info -> 2
    }
    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder = Holder(TextView(parent.context))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val text = holder.itemView as TextView
        val row = rows[position]
        val context = text.context
        val surface = android.util.TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, surface, true)
        val variant = android.util.TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, variant, true)
        text.setTextColor(if (row is Row.SectionHeader) variant.data else surface.data)
        text.gravity = Gravity.CENTER_VERTICAL
        text.setPadding(dp(context, 0), dp(context, if (row is Row.SectionHeader) 0 else 12), dp(context, 0), dp(context, 12))
        when (row) {
            is Row.SectionHeader -> { text.text = row.title; text.textSize = 16f; text.contentDescription = row.title }
            is Row.Navigation -> {
                text.text = buildString { append(row.title); row.summary?.let { append("\n").append(it) }; append("     ›") }
                text.textSize = 16f
                row.summary?.let { text.setLineSpacing(dp(context, 4).toFloat(), 1f) }
                text.isClickable = true
                text.setOnClickListener { onSelect(row.destination) }
            }
            is Row.Info -> { text.text = "${row.title}\n${row.value}"; text.textSize = 16f }
        }
        text.minimumHeight = dp(context, if (row is Row.SectionHeader) 32 else 64)
        text.isClickable = row is Row.Navigation
        if (row !is Row.Navigation) text.setOnClickListener(null)
    }
    override fun getItemCount() = rows.size
    class Holder(view: View) : RecyclerView.ViewHolder(view)
    private fun dp(context: android.content.Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
