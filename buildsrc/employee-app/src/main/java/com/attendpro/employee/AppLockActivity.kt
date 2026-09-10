package com.attendpro.employee

import android.content.Intent
import com.attendpro.core.AppLockGateActivity

class AppLockActivity : AppLockGateActivity() {
    override fun mainActivityIntent(): Intent = Intent(this, MainActivity::class.java).apply {
        if (this@AppLockActivity.intent?.action == Intent.ACTION_SEND && this@AppLockActivity.intent?.type == "text/plain") {
            action = Intent.ACTION_SEND
            type = "text/plain"
            val shared = this@AppLockActivity.intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(32_000)
            if (shared.isNotBlank()) putExtra(Intent.EXTRA_TEXT, shared)
        }
    }

    override fun appLabelForLock(): String = "ATTEND PRO - الموظف"
}
