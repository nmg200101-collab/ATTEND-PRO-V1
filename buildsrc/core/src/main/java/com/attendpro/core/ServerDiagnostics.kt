package com.attendpro.core

/** Process-local truth for actual HTTP activity. Credentials alone never mean "connected". */
object ServerDiagnostics {
    data class Snapshot(
        val lastHttpStatus: Int,
        val lastSuccessAt: Long,
        val lastErrorAt: Long,
        val lastError: String,
        val lastPath: String,
        val lastSuccessPath: String,
        val lastErrorPath: String
    ) {
        fun isFresh(now: Long = System.currentTimeMillis(), maxAgeMillis: Long = 120_000L): Boolean =
            lastHttpStatus in 200..299 && lastSuccessAt > 0L &&
                lastSuccessAt >= lastErrorAt && now - lastSuccessAt <= maxAgeMillis
    }

    @Volatile private var httpStatus: Int = 0
    @Volatile private var successAt: Long = 0L
    @Volatile private var errorAt: Long = 0L
    @Volatile private var errorText: String = ""
    @Volatile private var pathText: String = ""
    @Volatile private var successPathText: String = ""
    @Volatile private var errorPathText: String = ""

    fun success(code: Int, path: String) {
        httpStatus = code
        successAt = System.currentTimeMillis()
        pathText = path
        successPathText = path
        // Preserve the previous error text/time/path so diagnostics can show recovery history.
    }

    fun failure(code: Int, path: String, error: String) {
        httpStatus = code
        errorAt = System.currentTimeMillis()
        errorText = error.take(300)
        pathText = path
        errorPathText = path
    }

    fun snapshot(): Snapshot = Snapshot(
        httpStatus, successAt, errorAt, errorText, pathText, successPathText, errorPathText
    )
}
