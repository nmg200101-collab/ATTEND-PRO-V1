package com.attendpro.foundation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.attendpro.core.AppLockManager
import com.attendpro.core.AppPinResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppLockManagerTest {
    private lateinit var manager: AppLockManager

    @Before fun setUp() {
        manager = AppLockManager(ApplicationProvider.getApplicationContext<Context>())
        manager.disable()
    }

    @After fun tearDown() { manager.disable() }

    @Test fun pinUnlockAndImmediateBackgroundLock() {
        manager.setPin("246810", biometricEnabled = false, timeoutSeconds = 0)
        manager.markUnlocked(1_000L)
        assertFalse(manager.requiresUnlock(1_050L))

        manager.markBackground(1_100L)
        assertTrue(manager.requiresUnlock(1_100L))
        assertEquals(AppPinResult.INCORRECT, manager.verifyPin("000000", 1_200L))
        assertEquals(AppPinResult.SUCCESS, manager.verifyPin("246810", 1_300L))
        assertFalse(manager.requiresUnlock(1_301L))
    }

    @Test fun fiveFailuresCauseTemporaryLockout() {
        manager.setPin("135790")
        repeat(4) { assertEquals(AppPinResult.INCORRECT, manager.verifyPin("999999", 10_000L + it)) }
        assertEquals(AppPinResult.TEMPORARILY_LOCKED, manager.verifyPin("999999", 10_100L))
        assertEquals(AppPinResult.TEMPORARILY_LOCKED, manager.verifyPin("135790", 10_200L))
        assertEquals(AppPinResult.SUCCESS, manager.verifyPin("135790", 70_101L))
    }

    @Test fun delayedLockAllowsShortAppSwitch() {
        manager.setPin("112233", timeoutSeconds = 30)
        manager.markUnlocked(1_000L)
        manager.markBackground(2_000L)
        assertFalse(manager.requiresUnlock(31_999L))
        assertTrue(manager.requiresUnlock(32_000L))
    }
}
