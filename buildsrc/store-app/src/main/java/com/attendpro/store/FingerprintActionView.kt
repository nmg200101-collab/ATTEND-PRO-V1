package com.attendpro.store

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/**
 * Lightweight vector fingerprint used by the Store dashboard.
 * It intentionally contains no bitmap asset so it scales cleanly on all densities.
 */
class FingerprintActionView(
    context: Context,
    private val accentColor: Int,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val size = min(w, h) * 0.76f
        val cx = w / 2f
        val cy = h * 0.48f
        paint.color = accentColor
        paint.strokeWidth = min(w, h) * 0.035f

        fun arc(scale: Float, start: Float, sweep: Float, yShift: Float = 0f) {
            val rw = size * scale
            val rh = size * scale * 1.08f
            val r = RectF(cx - rw / 2f, cy - rh / 2f + yShift, cx + rw / 2f, cy + rh / 2f + yShift)
            canvas.drawArc(r, start, sweep, false, paint)
        }

        arc(1.00f, 205f, 130f)
        arc(0.82f, 190f, 160f, size * 0.02f)
        arc(0.64f, 175f, 190f, size * 0.04f)
        arc(0.47f, 160f, 220f, size * 0.06f)
        arc(0.30f, 145f, 250f, size * 0.08f)

        // Two short lower ridges make the icon read as a fingerprint rather than a target.
        val y1 = cy + size * 0.34f
        val y2 = cy + size * 0.45f
        canvas.drawArc(RectF(cx - size * 0.22f, y1 - size * 0.08f, cx + size * 0.22f, y1 + size * 0.12f), 205f, 130f, false, paint)
        canvas.drawArc(RectF(cx - size * 0.12f, y2 - size * 0.05f, cx + size * 0.12f, y2 + size * 0.08f), 205f, 130f, false, paint)
    }
}
