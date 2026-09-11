package com.attendpro.employee

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.attendpro.core.AppLanguage
import com.attendpro.core.UiKit

class EmployeeUserGuideActivity : Activity() {
    private val p by lazy { UiKit.palette(this) }
    private lateinit var list: LinearLayout

    private data class Section(val title: Int, val body: Int)

    private val sections = listOf(
        Section(R.string.guide_pairing_title, R.string.guide_pairing_body),
        Section(R.string.guide_qr_title, R.string.guide_qr_body),
        Section(R.string.guide_ble_title, R.string.guide_ble_body),
        Section(R.string.guide_wifi_title, R.string.guide_wifi_body),
        Section(R.string.guide_attendance_title, R.string.guide_attendance_body),
        Section(R.string.guide_presence_title, R.string.guide_presence_body),
        Section(R.string.guide_location_title, R.string.guide_location_body),
        Section(R.string.guide_messages_title, R.string.guide_messages_body),
        Section(R.string.guide_lock_title, R.string.guide_lock_body),
        Section(R.string.guide_update_title, R.string.guide_update_body),
        Section(R.string.guide_troubleshoot_title, R.string.guide_troubleshoot_body)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguage.applyToResources(this)
        render("")
    }

    private fun render(initialQuery: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (AppLanguage.isEnglish(this@EmployeeUserGuideActivity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@EmployeeUserGuideActivity, 14), UiKit.dp(this@EmployeeUserGuideActivity, 16), UiKit.dp(this@EmployeeUserGuideActivity, 14), UiKit.dp(this@EmployeeUserGuideActivity, 28))
            setBackgroundColor(p.bg)
        }
        val header = UiKit.heroCard(this, p, 10).apply {
            addView(UiKit.title(this@EmployeeUserGuideActivity, p, getString(R.string.guide_title), 21f).apply { gravity = Gravity.CENTER })
            addView(UiKit.button(this@EmployeeUserGuideActivity, p, getString(R.string.language), false).apply {
                setOnClickListener { AppLanguage.showPicker(this@EmployeeUserGuideActivity) { recreate() } }
            })
        }
        root.addView(header)

        val search = EditText(this).apply {
            hint = getString(R.string.guide_search_hint)
            setText(initialQuery)
            textSize = 15f
            setSingleLine(true)
            setPadding(UiKit.dp(this@EmployeeUserGuideActivity, 16), UiKit.dp(this@EmployeeUserGuideActivity, 12), UiKit.dp(this@EmployeeUserGuideActivity, 16), UiKit.dp(this@EmployeeUserGuideActivity, 12))
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = UiKit.dp(this@EmployeeUserGuideActivity, 8)
            bottomMargin = UiKit.dp(this@EmployeeUserGuideActivity, 8)
        })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        populate(initialQuery)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = populate(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(p.bg)
            addView(root)
        })
    }

    private fun populate(query: String) {
        list.removeAllViews()
        val q = query.trim().lowercase()
        val matches = sections.filter {
            q.isBlank() || getString(it.title).lowercase().contains(q) || getString(it.body).lowercase().contains(q)
        }
        if (matches.isEmpty()) {
            list.addView(UiKit.card(this, p, 10).apply {
                addView(UiKit.subtitle(this@EmployeeUserGuideActivity, p, getString(R.string.guide_no_results)).apply { gravity = Gravity.CENTER })
            })
            return
        }
        matches.forEach { section ->
            list.addView(UiKit.card(this, p, 10).apply {
                addView(UiKit.title(this@EmployeeUserGuideActivity, p, getString(section.title), 16.5f))
                addView(UiKit.subtitle(this@EmployeeUserGuideActivity, p, getString(section.body)).apply {
                    setLineSpacing(0f, 1.12f)
                })
            })
        }
    }
}
