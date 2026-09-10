package com.attendpro.store

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.FaceDetector
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object FaceSignatureEngine {
    data class Analysis(val template: String = "", val quality: Int = 0, val error: String = "", val faceCrop: Bitmap? = null)

    fun analyze(source: Bitmap): Analysis {
        if (source.width < 120 || source.height < 120) return Analysis(error = "الصورة صغيرة جدًا")
        val maxSide = max(source.width, source.height)
        val scale = if (maxSide > 900) 900f / maxSide else 1f
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true) else source
        val evenWidth = if (scaled.width % 2 == 0) scaled.width else scaled.width - 1
        val detectorBitmap = Bitmap.createBitmap(evenWidth, scaled.height, Bitmap.Config.RGB_565)
        Canvas(detectorBitmap).drawBitmap(scaled, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        val faces = arrayOfNulls<FaceDetector.Face>(3)
        val count = runCatching { FaceDetector(detectorBitmap.width, detectorBitmap.height, faces.size).findFaces(detectorBitmap, faces) }.getOrDefault(0)
        if (count <= 0 || faces[0] == null) return Analysis(error = "لم يتم العثور على وجه واضح. واجه الكاميرا مباشرة وحسّن الإضاءة.")
        if (count > 1) return Analysis(error = "يوجد أكثر من وجه في الصورة. اجعل موظفًا واحدًا فقط أمام الكاميرا.")
        val face = faces[0]!!
        val midpoint = android.graphics.PointF(); face.getMidPoint(midpoint)
        val eyes = face.eyesDistance()
        if (eyes < detectorBitmap.width * 0.06f) return Analysis(error = "الوجه بعيد عن الكاميرا. اقترب قليلًا.")
        val left = (midpoint.x - eyes * 2.0f).toInt().coerceAtLeast(0)
        val top = (midpoint.y - eyes * 2.25f).toInt().coerceAtLeast(0)
        val right = (midpoint.x + eyes * 2.0f).toInt().coerceAtMost(detectorBitmap.width)
        val bottom = (midpoint.y + eyes * 2.35f).toInt().coerceAtMost(detectorBitmap.height)
        if (right - left < 80 || bottom - top < 80) return Analysis(error = "تعذر قص الوجه بجودة مناسبة.")
        val crop = Bitmap.createBitmap(detectorBitmap, left, top, right - left, bottom - top)
        val normalized = Bitmap.createScaledBitmap(crop, 96, 112, true)
        val quality = qualityScore(normalized, face.confidence())
        if (quality < 45) return Analysis(quality = quality, error = "جودة الوجه منخفضة ($quality/100). حسّن الإضاءة وثبّت الهاتف ثم أعد المحاولة.", faceCrop = normalized)
        return Analysis(encode(hog(normalized)), quality, faceCrop = normalized)
    }

    fun templateCount(value: String): Int = unpack(value).size

    fun appendTemplate(existing: String, fresh: String, maxTemplates: Int = 5): String {
        val templates = (unpack(existing) + unpack(fresh)).filter { it.startsWith("fh1:") }.takeLast(maxTemplates)
        return if (templates.size <= 1) templates.firstOrNull().orEmpty() else "fm1:" + templates.joinToString("~")
    }

    fun similarity(a: String, b: String): Float {
        val scores = unpack(a).flatMap { left -> unpack(b).map { right -> singleSimilarity(left, right) } }
        return scores.maxOrNull() ?: 0f
    }

    private fun singleSimilarity(a: String, b: String): Float {
        val x = decode(a) ?: return 0f; val y = decode(b) ?: return 0f
        if (x.size != y.size || x.isEmpty()) return 0f
        var dot = 0.0; var nx = 0.0; var ny = 0.0
        for (i in x.indices) { dot += x[i] * y[i]; nx += x[i] * x[i]; ny += y[i] * y[i] }
        if (nx <= 1e-9 || ny <= 1e-9) return 0f
        return (dot / sqrt(nx * ny)).toFloat().coerceIn(-1f, 1f)
    }

    private fun unpack(value: String): List<String> = when {
        value.startsWith("fm1:") -> value.removePrefix("fm1:").split('~').filter { it.startsWith("fh1:") }
        value.startsWith("fh1:") -> listOf(value)
        else -> emptyList()
    }

    private fun qualityScore(bitmap: Bitmap, detectorConfidence: Float): Int {
        val gray = grayscale(bitmap)
        val mean = gray.average()
        val variance = gray.sumOf { (it - mean) * (it - mean) } / gray.size
        var edge = 0.0; var n = 0
        val w = bitmap.width
        for (y in 1 until bitmap.height - 1 step 2) for (x in 1 until w - 1 step 2) {
            val i = y * w + x
            edge += kotlin.math.abs(gray[i + 1] - gray[i - 1]) + kotlin.math.abs(gray[i + w] - gray[i - w]); n++
        }
        val edgeAvg = if (n > 0) edge / n else 0.0
        val brightnessScore = (1.0 - kotlin.math.abs(mean - 135.0) / 135.0).coerceIn(0.0, 1.0)
        val contrastScore = (sqrt(variance) / 55.0).coerceIn(0.0, 1.0)
        val sharpScore = (edgeAvg / 42.0).coerceIn(0.0, 1.0)
        return ((0.35 * detectorConfidence.coerceIn(0f, 1f) + 0.20 * brightnessScore + 0.20 * contrastScore + 0.25 * sharpScore) * 100).toInt().coerceIn(0, 100)
    }

    private fun hog(bitmap: Bitmap): FloatArray {
        val w = 64; val h = 80
        val b = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val g = grayscale(b)
        val cellsX = 8; val cellsY = 10; val bins = 8
        val out = FloatArray(cellsX * cellsY * bins)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val dx = g[i + 1] - g[i - 1]; val dy = g[i + w] - g[i - w]
            val mag = sqrt(dx * dx + dy * dy).toFloat()
            var angle = atan2(dy, dx)
            if (angle < 0) angle += Math.PI
            if (angle >= Math.PI) angle -= Math.PI
            val bin = min(bins - 1, floor(angle / Math.PI * bins).toInt())
            val cx = min(cellsX - 1, x / (w / cellsX)); val cy = min(cellsY - 1, y / (h / cellsY))
            out[(cy * cellsX + cx) * bins + bin] += mag
        }
        var norm = 0.0
        for (v in out) norm += v * v
        val inv = if (norm > 1e-9) (1.0 / sqrt(norm)).toFloat() else 1f
        for (i in out.indices) out[i] *= inv
        return out
    }

    private fun grayscale(bitmap: Bitmap): DoubleArray {
        val pixels = IntArray(bitmap.width * bitmap.height); bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return DoubleArray(pixels.size) { i ->
            val c = pixels[i]; val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            0.299 * r + 0.587 * g + 0.114 * b
        }
    }

    private fun encode(values: FloatArray): String {
        val bb = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN); values.forEach { bb.putFloat(it) }
        return "fh1:" + Base64.encodeToString(bb.array(), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    private fun decode(text: String): FloatArray? = runCatching {
        if (!text.startsWith("fh1:")) return@runCatching null
        val bytes = Base64.decode(text.removePrefix("fh1:"), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
        if (bytes.size % 4 != 0) return@runCatching null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        FloatArray(bytes.size / 4) { bb.float }
    }.getOrNull()
}
