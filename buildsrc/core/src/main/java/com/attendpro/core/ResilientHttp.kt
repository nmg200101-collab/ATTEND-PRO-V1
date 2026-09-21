package com.attendpro.core

import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.UnknownHostException
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * HTTPS transport that preserves the normal Android network path, but retries with
 * secure DNS-over-HTTPS resolution when the device/ISP cannot resolve the server name.
 *
 * TLS hostname verification remains enabled because OkHttp still connects using the
 * original HTTPS hostname; only the DNS lookup is replaced on the fallback attempt.
 */
object ResilientHttp {
    data class Result(val code: Int, val body: String)

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun execute(
        url: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 10_000
    ): Result {
        return try {
            executeSystem(url, method, headers, body, connectTimeoutMs, readTimeoutMs)
        } catch (t: Throwable) {
            if (!isDnsFailure(t)) throw t
            executeWithSecureDns(url, method, headers, body, connectTimeoutMs, readTimeoutMs)
        }
    }

    fun isDnsFailure(t: Throwable?): Boolean {
        var x = t
        repeat(10) {
            if (x == null) return false
            if (x is UnknownHostException) return true
            val m = x?.message.orEmpty()
            if (m.contains("Unable to resolve host", ignoreCase = true) ||
                m.contains("No address associated with hostname", ignoreCase = true) ||
                m.contains("UnknownHost", ignoreCase = true)) return true
            x = x?.cause
        }
        return false
    }

    private fun executeSystem(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): Result {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = connectTimeoutMs
            c.readTimeout = readTimeoutMs
            c.instanceFollowRedirects = true
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                if (headers.keys.none { it.equals("Content-Type", true) }) {
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                OutputStreamWriter(c.outputStream, Charsets.UTF_8).use { it.write(body) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader -> reader.readText() }
            }.orEmpty()
            return Result(code, text)
        } finally {
            c.disconnect()
        }
    }

    private fun executeWithSecureDns(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): Result {
        val client = OkHttpClient.Builder()
            .dns(SecureFallbackDns)
            .connectTimeout(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> b.header(k, v) }
        val requestBody = body?.toRequestBody(jsonMediaType)
        when {
            method.equals("GET", true) && body == null -> b.get()
            method.equals("HEAD", true) && body == null -> b.head()
            else -> b.method(method.uppercase(), requestBody)
        }
        client.newCall(b.build()).execute().use { response ->
            return Result(response.code, response.body?.string().orEmpty())
        }
    }

    private object SecureFallbackDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            try {
                return Dns.SYSTEM.lookup(hostname)
            } catch (_: UnknownHostException) {
                // Continue to encrypted resolvers.
            }

            val escaped = URLEncoder.encode(hostname, "UTF-8")
            val endpoints = listOf(
                "https://1.1.1.1/dns-query?name=$escaped&type=A",
                "https://8.8.8.8/resolve?name=$escaped&type=A"
            )
            val resolved = LinkedHashSet<String>()

            endpoints.forEach { endpoint ->
                runCatching {
                    val c = URL(endpoint).openConnection() as HttpURLConnection
                    try {
                        c.requestMethod = "GET"
                        c.connectTimeout = 5_000
                        c.readTimeout = 5_000
                        c.setRequestProperty("Accept", "application/dns-json")
                        c.setRequestProperty("User-Agent", "ATTEND-PRO-SecureDNS/1")
                        val code = c.responseCode
                        if (code !in 200..299) return@runCatching
                        val text = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val json = JSONObject(text)
                        val answers = json.optJSONArray("Answer") ?: return@runCatching
                        for (i in 0 until answers.length()) {
                            val a = answers.optJSONObject(i) ?: continue
                            if (a.optInt("type", 0) != 1) continue
                            val value = a.optString("data", "").trim()
                            if (value.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) resolved += value
                        }
                    } finally {
                        c.disconnect()
                    }
                }
                if (resolved.isNotEmpty()) return@forEach
            }

            if (resolved.isEmpty()) throw UnknownHostException("$hostname: secure DNS fallback returned no IPv4 address")
            return resolved.map { InetAddress.getByName(it) }
        }
    }
}
