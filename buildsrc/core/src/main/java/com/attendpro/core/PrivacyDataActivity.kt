package com.attendpro.core

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

class PrivacyDataActivity : Activity() {
    private val p by lazy { UiKit.palette(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguage.applyToResources(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = if (AppLanguage.isEnglish(this@PrivacyDataActivity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
            setPadding(
                UiKit.dp(this@PrivacyDataActivity, 14),
                UiKit.dp(this@PrivacyDataActivity, 18),
                UiKit.dp(this@PrivacyDataActivity, 14),
                UiKit.dp(this@PrivacyDataActivity, 28)
            )
            setBackgroundColor(p.bg)
        }

        root.addView(UiKit.heroCard(this, p, 12).apply {
            addView(UiKit.title(this@PrivacyDataActivity, p, getString(R.string.privacy_center_title), 22f).apply { gravity = Gravity.CENTER })
            addView(UiKit.subtitle(this@PrivacyDataActivity, p, getString(R.string.privacy_center_summary)).apply { gravity = Gravity.CENTER })
        })

        fun section(title: Int, body: Int) {
            root.addView(UiKit.card(this, p, 11).apply {
                addView(UiKit.title(this@PrivacyDataActivity, p, getString(title), 16.5f))
                addView(UiKit.subtitle(this@PrivacyDataActivity, p, getString(body)).apply { setLineSpacing(0f, 1.12f) })
            })
        }

        section(R.string.privacy_location_title, R.string.privacy_location_body)
        section(R.string.privacy_security_title, R.string.privacy_security_body)
        section(R.string.privacy_delete_title, R.string.privacy_delete_body)

        root.addView(UiKit.card(this, p, 10).apply {
            addView(UiKit.button(this@PrivacyDataActivity, p, getString(R.string.privacy_open_policy)).apply {
                setOnClickListener { openUrl(AppUpdateManager.DEFAULT_SERVER + "/privacy") }
            })
            addView(UiKit.button(this@PrivacyDataActivity, p, getString(R.string.privacy_open_deletion), false).apply {
                setOnClickListener { openUrl(AppUpdateManager.DEFAULT_SERVER + "/delete-data") }
            })
        })

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(p.bg)
            addView(root)
        })
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.privacy_browser_error))
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
