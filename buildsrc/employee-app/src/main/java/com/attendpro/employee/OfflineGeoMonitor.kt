package com.attendpro.employee

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import com.attendpro.core.EmployeeIdentityStore

/**
 * Background GPS recognition for ATTEND-PRO.
 *
 * GPS is a phone-presence channel in 1.9.66. It records when the
 * employee phone is seen inside/near/outside the configured store radius and exposes
 * that observation to the Store/server. Identity verification remains a separate proof step.
 */
class OfflineGeoMonitor(
    private val context: Context,
    private val identity: EmployeeIdentityStore,
    private val onEntered: (distanceMeters: Int, accuracyMeters: Int) -> Unit,
    private val onObservation: (state: String, distanceMeters: Int, accuracyMeters: Int, observedAt: Long) -> Unit,
    private val onStatus: (String) -> Unit
) {
    companion object {
        const val STATE_UNKNOWN = "UNKNOWN"
        const val STATE_INSIDE = "INSIDE"
        const val STATE_NEAR = "NEAR"
        const val STATE_OUTSIDE = "OUTSIDE"
        private const val MAX_ACCEPTED_ACCURACY_METERS = 150f
        private const val UPDATE_INTERVAL_MILLIS = 10_000L
        private const val MIN_DISTANCE_METERS = 5f
    }

    private val manager = context.getSystemService(LocationManager::class.java)
    private var running = false
    private var state: String = STATE_UNKNOWN
    private var lastArrivalAlertAt = 0L

    private fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (running || !identity.geoArrivalAlertsEnabled || !identity.isTrustedStoreGpsConfigured || !hasPermission()) return
        val lm = manager ?: return
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            onStatus("GPS مهيأ لكن خدمات الموقع متوقفة")
            return
        }
        running = true
        state = identity.lastGpsState.ifBlank { STATE_UNKNOWN }
        providers.forEach { provider ->
            runCatching { lm.requestLocationUpdates(provider, UPDATE_INTERVAL_MILLIS, MIN_DISTANCE_METERS, listener, Looper.getMainLooper()) }
        }
        onStatus("GPS يعمل — يتم التعرف على وجود الهاتف قرب المحل")
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        running = false
        runCatching { manager?.removeUpdates(listener) }
    }

    fun isRunning(): Boolean = running

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!running || !identity.isTrustedStoreGpsConfigured) return
            val now = System.currentTimeMillis()
            if (now - location.time > 5 * 60_000L) return
            if (isMock(location)) {
                onStatus("تم تجاهل موقع تجريبي/مزيف")
                return
            }
            val accuracy = if (location.hasAccuracy()) location.accuracy else MAX_ACCEPTED_ACCURACY_METERS
            if (!accuracy.isFinite() || accuracy > MAX_ACCEPTED_ACCURACY_METERS) {
                onStatus("GPS يعمل لكن دقة القراءة ضعيفة (${accuracy.toInt()}م)")
                return
            }

            val target = Location("ATTEND_PRO_STORE").apply {
                latitude = identity.trustedStoreLatitude
                longitude = identity.trustedStoreLongitude
            }
            val distance = location.distanceTo(target)
            val radius = identity.trustedStoreGpsRadius.toFloat().coerceAtLeast(10f)
            // A second NEAR band prevents a 10m shop from oscillating IN/OUT just because
            // indoor GPS accuracy drifts. It is explicitly shown as NEAR, never as proof.
            val nearAllowance = accuracy.coerceIn(12f, 40f)
            val nextState = when {
                distance <= radius -> STATE_INSIDE
                distance <= radius + nearAllowance -> STATE_NEAR
                else -> STATE_OUTSIDE
            }
            val distanceInt = distance.toInt().coerceAtLeast(0)
            val accuracyInt = accuracy.toInt().coerceAtLeast(0)

            identity.lastGpsObservedAt = now
            identity.lastGpsDistanceMeters = distanceInt
            identity.lastGpsAccuracyMeters = accuracyInt
            identity.lastGpsState = nextState
            if (nextState == STATE_INSIDE || nextState == STATE_NEAR) identity.lastGpsInsideAt = now

            val previous = state
            if ((nextState == STATE_INSIDE || nextState == STATE_NEAR) && previous == STATE_OUTSIDE) {
                identity.lastGpsEnteredAt = now
            } else if (previous == STATE_UNKNOWN && (nextState == STATE_INSIDE || nextState == STATE_NEAR)) {
                identity.lastGpsEnteredAt = now
            }
            if (nextState == STATE_OUTSIDE && previous != STATE_OUTSIDE && previous != STATE_UNKNOWN) {
                identity.lastGpsExitedAt = now
            }

            if (nextState != previous) identity.lastGpsServerUploadAt = 0L
            onObservation(nextState, distanceInt, accuracyInt, now)

            if ((nextState == STATE_INSIDE || nextState == STATE_NEAR) && previous !in setOf(STATE_INSIDE, STATE_NEAR)) {
                if (now - lastArrivalAlertAt > 10 * 60_000L) {
                    lastArrivalAlertAt = now
                    onEntered(distanceInt, accuracyInt)
                }
            }
            state = nextState
        }

        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    private fun isMock(location: Location): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else @Suppress("DEPRECATION") location.isFromMockProvider
}
