package com.attendpro.store

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
import android.widget.TextView
import com.attendpro.core.AppLanguage
import com.attendpro.core.UiKit

class StoreUserGuideActivity : Activity() {
    private val p by lazy { UiKit.palette(this) }
    private lateinit var list: LinearLayout

    private data class Section(val title: Int, val body: Int)

    private val sections = listOf(
        Section(R.string.guide_pairing_title, R.string.guide_pairing_body),
        Section(R.string.guide_qr_title, R.string.guide_qr_body),
        Section(R.string.guide_ble_title, R.string.guide_ble_body),
        Section(R.string.guide_wifi_title, R.string.guide_wifi_body),
        Section(R.string.guide_attendance_title, R.string.guide_attendance_body),
        Section(R.string.guide_shift_title, R.string.guide_shift_body),
        Section(R.string.guide_presence_title, R.string.guide_presence_body),
        Section(R.string.guide_backup_title, R.string.guide_backup_body),
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
            layoutDirection = if (AppLanguage.isEnglish(this@StoreUserGuideActivity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@StoreUserGuideActivity, 14), UiKit.dp(this@StoreUserGuideActivity, 16), UiKit.dp(this@StoreUserGuideActivity, 14), UiKit.dp(this@StoreUserGuideActivity, 28))
            setBackgroundColor(p.bg)
        }
        val header = UiKit.heroCard(this, p, 10).apply {
            addView(UiKit.title(this@StoreUserGuideActivity, p, getString(R.string.guide_title), 21f).apply { gravity = Gravity.CENTER })
            addView(UiKit.button(this@StoreUserGuideActivity, p, getString(R.string.language), false).apply {
                setOnClickListener { AppLanguage.showPicker(this@StoreUserGuideActivity) { recreate() } }
            })
        }
        root.addView(header)

        val search = EditText(this).apply {
            hint = getString(R.string.guide_search_hint)
            setText(initialQuery)
            textSize = 15f
            setSingleLine(true)
            setPadding(UiKit.dp(this@StoreUserGuideActivity, 16), UiKit.dp(this@StoreUserGuideActivity, 12), UiKit.dp(this@StoreUserGuideActivity, 16), UiKit.dp(this@StoreUserGuideActivity, 12))
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = UiKit.dp(this@StoreUserGuideActivity, 8)
            bottomMargin = UiKit.dp(this@StoreUserGuideActivity, 8)
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
                addView(UiKit.subtitle(this@StoreUserGuideActivity, p, getString(R.string.guide_no_results)).apply { gravity = Gravity.CENTER })
            })
            return
        }
        matches.forEach { section ->
            list.addView(UiKit.card(this, p, 10).apply {
                addView(UiKit.title(this@StoreUserGuideActivity, p, getString(section.title), 16.5f))
                addView(UiKit.subtitle(this@StoreUserGuideActivity, p, getString(section.body)).apply {
                    setLineSpacing(0f, 1.12f)
                })
            })
        }
    }
}
