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
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Central HTTPS transport with resilient DNS and IPv4-first recovery.
 *
 * Order:
 * 1) Normal Android network stack.
 * 2) On DNS/connect failures, retry with IPv4-preferred DNS.
 * 3) Resolve through bootstrapped DNS-over-HTTPS.
 * 4) Use verified Cloudflare IPv4 edge addresses for the ATTEND-PRO Worker.
 *
 * TLS hostname verification/SNI stay enabled because requests always keep the
 * original HTTPS hostname. Only address resolution changes.
 */
object ResilientHttp {
    data class Result(val code: Int, val body: String)

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private const val ATTEND_WORKER_HOST = "attend-pro-central.nmg200101.workers.dev"

    // Verified A records. IPv4 is deliberately used as the final fallback because
    // some mobile networks advertise/resolve IPv6 without providing a working route.
    private val attendWorkerIpv4Fallback = listOf(
        "104.21.44.99",
        "172.67.198.135"
    )

    private data class Resolver(
        val host: String,
        val bootstrapIps: List<String>,
        val urlFor: (String, String) -> String
    )

    private val resolvers = listOf(
        Resolver(
            host = "cloudflare-dns.com",
            bootstrapIps = listOf("1.1.1.1", "1.0.0.1")
        ) { name, type ->
            "https://cloudflare-dns.com/dns-query?name=${URLEncoder.encode(name, "UTF-8")}&type=$type"
        },
        Resolver(
            host = "dns.google",
            bootstrapIps = listOf("8.8.8.8", "8.8.4.4")
        ) { name, type ->
            "https://dns.google/resolve?name=${URLEncoder.encode(name, "UTF-8")}&type=$type"
        }
    )

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
            if (!isRetryableNetworkFailure(t)) throw t
            executeWithSecureDns(url, method, headers, body, connectTimeoutMs, readTimeoutMs)
        }
    }

    fun isDnsFailure(t: Throwable?): Boolean {
        var x = t
        repeat(12) {
            if (x == null) return false
            if (x is UnknownHostException) return true
            val m = x.message.orEmpty()
            if (m.contains("Unable to resolve host", ignoreCase = true) ||
                m.contains("No address associated with hostname", ignoreCase = true) ||
                m.contains("UnknownHost", ignoreCase = true) ||
                m.contains("secure DNS fallback", ignoreCase = true)) return true
            x = x.cause
        }
        return false
    }

    fun isRetryableNetworkFailure(t: Throwable?): Boolean {
        var x = t
        repeat(12) {
            if (x == null) return false
            if (x is UnknownHostException || x is ConnectException || x is SocketTimeoutException) return true
            val m = x.message.orEmpty()
            if (m.contains("Failed to connect to", ignoreCase = true) ||
                m.contains("Network is unreachable", ignoreCase = true) ||
                m.contains("ENETUNREACH", ignoreCase = true) ||
                m.contains("EHOSTUNREACH", ignoreCase = true) ||
                m.contains("connect timed out", ignoreCase = true) ||
                m.contains("Unable to resolve host", ignoreCase = true) ||
                m.contains("No address associated with hostname", ignoreCase = true)) return true
            x = x.cause
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
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> b.header(k, v) }
        val requestBody = body?.toRequestBody(jsonMediaType)
        when {
            method.equals("GET", true) && body == null -> b.get()
            method.equals("HEAD", true) && body == null -> b.head()
            else -> b.method(method.uppercase(Locale.US), requestBody)
        }
        client.newCall(b.build()).execute().use { response ->
            return Result(response.code, response.body?.string().orEmpty())
        }
    }

    private object SecureFallbackDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val system = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrNull().orEmpty()
            preferIpv4(system).takeIf { it.isNotEmpty() }?.let { return it }

            val encrypted = resolveEncrypted(hostname)
            preferIpv4(encrypted).takeIf { it.isNotEmpty() }?.let { return it }

            if (hostname.equals(ATTEND_WORKER_HOST, ignoreCase = true)) {
                val pinned = attendWorkerIpv4Fallback.mapNotNull { literalAddress(it) }
                if (pinned.isNotEmpty()) return pinned
            }

            throw UnknownHostException(
                "$hostname: system DNS, encrypted DNS and verified IPv4 fallback all failed"
            )
        }
    }

    private fun resolveEncrypted(hostname: String, depth: Int = 0): List<InetAddress> {
        if (depth > 3) return emptyList()

        val ipv4 = LinkedHashSet<InetAddress>()
        val cnames = LinkedHashSet<String>()

        // First ask only for A records. On the affected mobile networks IPv6 can
        // resolve successfully while actual IPv6 routing is unavailable.
        resolvers.forEach { resolver ->
            runCatching {
                val response = queryResolver(resolver, hostname, "A")
                val answers = response.optJSONArray("Answer") ?: return@runCatching
                for (i in 0 until answers.length()) {
                    val a = answers.optJSONObject(i) ?: continue
                    val data = a.optString("data", "").trim().trimEnd('.')
                    when (a.optInt("type", 0)) {
                        1 -> if (isIpv4Literal(data)) literalAddress(data)?.let { ipv4 += it }
                        5 -> if (data.isNotBlank()) cnames += data
                    }
                }
            }
        }
        if (ipv4.isNotEmpty()) return ipv4.toList()

        cnames.forEach { alias ->
            val nested = resolveEncrypted(alias, depth + 1)
            val nestedIpv4 = preferIpv4(nested)
            if (nestedIpv4.isNotEmpty()) return nestedIpv4
        }

        // IPv6 is only a last option for hosts that genuinely have no IPv4.
        val ipv6 = LinkedHashSet<InetAddress>()
        resolvers.forEach { resolver ->
            runCatching {
                val response = queryResolver(resolver, hostname, "AAAA")
                val answers = response.optJSONArray("Answer") ?: return@runCatching
                for (i in 0 until answers.length()) {
                    val a = answers.optJSONObject(i) ?: continue
                    val data = a.optString("data", "").trim().trimEnd('.')
                    if (a.optInt("type", 0) == 28 && data.contains(':')) {
                        literalAddress(data)?.let { ipv6 += it }
                    }
                }
            }
        }
        return ipv6.toList()
    }

    private fun queryResolver(resolver: Resolver, hostname: String, type: String): JSONObject {
        val bootstrap = object : Dns {
            override fun lookup(name: String): List<InetAddress> {
                if (name.equals(resolver.host, ignoreCase = true)) {
                    val addresses = resolver.bootstrapIps.mapNotNull { literalAddress(it) }
                    if (addresses.isNotEmpty()) return addresses
                }
                return preferIpv4(Dns.SYSTEM.lookup(name)).ifEmpty { Dns.SYSTEM.lookup(name) }
            }
        }

        val client = OkHttpClient.Builder()
            .dns(bootstrap)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val request = Request.Builder()
            .url(resolver.urlFor(hostname, type))
            .header("Accept", "application/dns-json")
            .header("User-Agent", "ATTEND-PRO-SecureDNS/3")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("DNS resolver HTTP ${response.code}")
            return JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
        }
    }

    private fun preferIpv4(addresses: List<InetAddress>): List<InetAddress> {
        val ipv4 = addresses.filterIsInstance<Inet4Address>()
        return if (ipv4.isNotEmpty()) ipv4 else addresses
    }

    private fun isIpv4Literal(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all {
            val n = it.toIntOrNull()
            n != null && n in 0..255
        }
    }

    private fun literalAddress(value: String): InetAddress? =
        runCatching { InetAddress.getByName(value) }.getOrNull()
}
