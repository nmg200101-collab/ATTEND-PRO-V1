package com.attendpro.store

import android.content.Intent
import com.attendpro.core.AppLockGateActivity

class AppLockActivity : AppLockGateActivity() {
    override fun mainActivityIntent(): Intent = Intent(this, MainActivity::class.java)
    override fun appLabelForLock(): String = "ATTEND PRO - المحل"
}
