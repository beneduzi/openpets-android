package com.openpets.android.settings.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.openpets.android.settings.AppSettings
import com.openpets.android.MainActivity

interface SettingsHost {
    fun openSettingsDestination(destination: SettingsDestination)
    fun closeSettings()
}

class SettingsOverviewFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setOnTouchListener { _, _ -> false }
        }
        val bar = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(56); setPadding(dp(8), 0, dp(8), 0) }
        val title = TextView(context).apply { text = "Settings"; textSize = 20f; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(title, LinearLayout.LayoutParams(0, dp(56), 1f))
        val close = TextView(context).apply { text = "×"; textSize = 28f; gravity = Gravity.CENTER; contentDescription = "Close settings"; isClickable = true; setOnClickListener { (requireActivity() as SettingsHost).closeSettings() } }
        bar.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(bar)
        val recycler = RecyclerView(context).apply {
            setPadding(dp(16), 0, dp(16), 0); clipToPadding = false
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
            adapter = SettingsAdapter(overviewRows((requireActivity() as MainActivity).selectedPetName(), AppSettings.findLanIpv4())) {
                (requireActivity() as SettingsHost).openSettingsDestination(it)
            }
        }
        val centered = FrameLayout(context)
        centered.addView(recycler, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        root.addView(centered, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
