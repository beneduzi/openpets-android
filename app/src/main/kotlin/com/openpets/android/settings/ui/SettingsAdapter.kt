package com.openpets.android.settings.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MaterialR

class SettingsAdapter(
    private var rows: List<Row>,
    private val onSelect: (Row.Navigation) -> Unit,
    private var collapsedSectionIds: Set<Long> = emptySet(),
    private val onSectionClick: (Row.SectionHeader) -> Unit = {},
) : RecyclerView.Adapter<SettingsAdapter.Holder>() {
    fun replaceRows(newRows: List<Row>) { rows = newRows; notifyDataSetChanged() }
    fun setCollapsedSectionIds(ids: Set<Long>) { collapsedSectionIds = ids; notifyDataSetChanged() }

    init { setHasStableIds(true) }

    override fun getItemId(position: Int) = rows[position].id

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is Row.SectionHeader -> 0
        is Row.Navigation -> 1
        is Row.Info -> 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder = Holder(
        when (type) {
            0 -> LinearLayout(parent.context)
            else -> LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
        },
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        val context = holder.itemView.context
        when (row) {
            is Row.SectionHeader -> bindHeader(holder.itemView as LinearLayout, row, context, position)
            is Row.Navigation -> bindItem(holder.itemView as LinearLayout, context, row.titleRes,
                (row.breadcrumbRes?.let(context::getString)?.let { "$it · " } ?: "") + context.getString(row.titleRes),
                row.summaryRes?.let(context::getString) ?: row.summaryValue, true, position)
            is Row.Info -> bindItem(holder.itemView as LinearLayout, context, row.titleRes,
                context.getString(row.titleRes), row.value.ifEmpty { context.getString(com.openpets.android.R.string.ui_unavailable) }, false, position)
        }
    }

    private fun bindHeader(view: LinearLayout, row: Row.SectionHeader, context: Context, position: Int) {
        view.removeAllViews()
        view.orientation = LinearLayout.HORIZONTAL
        view.gravity = Gravity.CENTER_VERTICAL
        view.setPadding(dp(context, 16), dp(context, 24), dp(context, 16), dp(context, 8))
        view.minimumHeight = dp(context, 50)
        val collapsed = row.id in collapsedSectionIds
        val ripple = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true) && ripple.resourceId != 0) view.setBackgroundResource(ripple.resourceId) else view.background = null
        view.isClickable = true
        view.isFocusable = true
        view.setOnClickListener { onSectionClick(row) }
        val title = TextView(context).apply {
            text = context.getString(row.titleRes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(color(context, MaterialR.attr.colorPrimary, 0xff6750a4.toInt()))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            letterSpacing = 0.08f
            ViewCompat.setAccessibilityHeading(this, true)
            val state = if (collapsed) "collapsed" else "expanded"
            ViewCompat.setStateDescription(this, state)
            contentDescription = "${text}, $state"
        }
        view.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        view.addView(TextView(context).apply {
            text = if (collapsed) "▸" else "▾"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(color(context, MaterialR.attr.colorOnSurfaceVariant, 0xff49454f.toInt()))
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(context, 24), LinearLayout.LayoutParams.WRAP_CONTENT))
        ViewCompat.setAccessibilityHeading(view, true)
    }

    private fun bindItem(container: LinearLayout, context: Context, @androidx.annotation.StringRes titleRes: Int,
                         title: String, summary: String?, clickable: Boolean, position: Int) {
        container.removeAllViews()
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(0, 0, 0, 0)
        container.minimumHeight = dp(context, 0)
        container.isClickable = clickable
        container.isFocusable = clickable
        container.background = null
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 64)
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
            if (clickable) {
                val out = TypedValue()
                if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)) setBackgroundResource(out.resourceId)
            }
            isClickable = clickable
            isFocusable = clickable
        }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        val titleView = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(color(context, MaterialR.attr.colorOnSurface, 0xff1d1b20.toInt()))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        column.addView(titleView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        if (summary != null) {
            val summaryView = TextView(context).apply {
                text = summary
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(color(context, MaterialR.attr.colorOnSurfaceVariant, 0xff49454f.toInt()))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(context, 2), 0, 0)
            }
            column.addView(summaryView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        content.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (clickable) {
            content.addView(TextView(context).apply {
                text = "›"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(color(context, MaterialR.attr.colorOnSurfaceVariant, 0xff49454f.toInt()))
                gravity = Gravity.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(context, 24), LinearLayout.LayoutParams.MATCH_PARENT))
        }
        if (clickable) {
            content.setOnClickListener { (rows.getOrNull(position) as? Row.Navigation)?.let(onSelect) }
        } else content.setOnClickListener(null)
        container.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val divider = View(context).apply { setBackgroundColor(color(context, MaterialR.attr.colorOutlineVariant, 0x1f000000)) }
        val nextIsHeader = rows.getOrNull(position + 1) is Row.SectionHeader
        if (!nextIsHeader) container.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)))
    }

    override fun getItemCount() = rows.size
    class Holder(view: View) : RecyclerView.ViewHolder(view)

    private fun color(context: Context, attr: Int, fallback: Int): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(attr, value, true)) value.data else fallback
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
