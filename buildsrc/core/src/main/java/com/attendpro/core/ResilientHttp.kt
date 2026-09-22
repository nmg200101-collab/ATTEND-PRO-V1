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
 * Central HTTPS transport with resilient DNS.
 *
 * Order:
 * 1) Android/system DNS.
 * 2) DNS-over-HTTPS using resolver hostnames with hard-coded bootstrap IPs,
 *    so the fallback does not depend on the broken system DNS.
 * 3) Current Cloudflare edge addresses for the ATTEND-PRO Worker as a last resort.
 *
 * TLS hostname verification and SNI are never disabled: the original HTTPS hostname
 * remains in the request URL and OkHttp only receives alternate IP addresses from Dns.
 */
object ResilientHttp {
    data class Result(val code: Int, val body: String)

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private const val ATTEND_WORKER_HOST = "attend-pro-central.nmg200101.workers.dev"

    // Verified from two independent public resolvers during V134 build.
    // These are only the final fallback; normal/system and encrypted DNS are preferred.
    private val attendWorkerEdgeFallback = listOf(
        "104.21.44.99",
        "172.67.198.135",
        "2606:4700:3033::6815:2c63",
        "2606:4700:3035::ac43:c687"
    )

    private data class Resolver(
        val host: String,
        val bootstrapIps: List<String>,
        val urlFor: (String, String) -> String
    )

    private val resolvers = listOf(
        Resolver(
            host = "cloudflare-dns.com",
            bootstrapIps = listOf(
                "1.1.1.1", "1.0.0.1",
                "2606:4700:4700::1111", "2606:4700:4700::1001"
            )
        ) { name, type ->
            "https://cloudflare-dns.com/dns-query?name=${URLEncoder.encode(name, "UTF-8")}&type=$type"
        },
        Resolver(
            host = "dns.google",
            bootstrapIps = listOf(
                "8.8.8.8", "8.8.4.4",
                "2001:4860:4860::8888", "2001:4860:4860::8844"
            )
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

    fun isRetryableNetworkFailure(t: Throwable?): Boolean {
        var x = t
        repeat(12) {
            if (x == null) return false
            if (x is UnknownHostException || x is ConnectException || x is SocketTimeoutException) return true
            val m = x?.message.orEmpty()
            if (m.contains("Unable to resolve host", ignoreCase = true) ||
                m.contains("No address associated with hostname", ignoreCase = true) ||
                m.contains("UnknownHost", ignoreCase = true) ||
                m.contains("secure DNS fallback", ignoreCase = true) ||
                m.contains("Failed to connect to /", ignoreCase = true) ||
                m.contains("Network is unreachable", ignoreCase = true) ||
                m.contains("ENETUNREACH", ignoreCase = true) ||
                m.contains("connect failed", ignoreCase = true)) return true
            x = x?.cause
        }
        return false
    }

    fun isDnsFailure(t: Throwable?): Boolean {
        var x = t
        repeat(12) {
            if (x == null) return false
            if (x is UnknownHostException) return true
            val m = x?.message.orEmpty()
            if (m.contains("Unable to resolve host", ignoreCase = true) ||
                m.contains("No address associated with hostname", ignoreCase = true) ||
                m.contains("UnknownHost", ignoreCase = true) ||
                m.contains("secure DNS fallback", ignoreCase = true)) return true
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
            else -> b.method(method.uppercase(Locale.US), requestBody)
        }
        client.newCall(b.build()).execute().use { response ->
            return Result(response.code, response.body?.string().orEmpty())
        }
    }

    private object SecureFallbackDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val system = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrNull().orEmpty()
            val systemV4 = ipv4First(system)
            if (systemV4.isNotEmpty()) return systemV4

            val encrypted = resolveEncrypted(hostname)
            val encryptedV4 = ipv4First(encrypted)
            if (encryptedV4.isNotEmpty()) return encryptedV4

            if (hostname.equals(ATTEND_WORKER_HOST, ignoreCase = true)) {
                val pinned = attendWorkerEdgeFallback.mapNotNull { literalAddress(it) }
                val pinnedV4 = ipv4First(pinned)
                if (pinnedV4.isNotEmpty()) return pinnedV4
            }

            // Only use IPv6 when no IPv4 route can be obtained at all. On many mobile
            // networks Android can resolve AAAA but has no working IPv6 route.
            if (system.isNotEmpty()) return system
            if (encrypted.isNotEmpty()) return encrypted

            throw UnknownHostException(
                "$hostname: system DNS and encrypted DNS failed; no verified fallback address is available"
            )
        }
    }

    private fun resolveEncrypted(hostname: String, depth: Int = 0): List<InetAddress> {
        if (depth > 3) return emptyList()

        val ipv4 = LinkedHashSet<InetAddress>()
        val ipv6 = LinkedHashSet<InetAddress>()
        val cnames = LinkedHashSet<String>()

        listOf("A", "AAAA").forEach { type ->
            resolvers.forEach { resolver ->
                runCatching {
                    val response = queryResolver(resolver, hostname, type)
                    val answers = response.optJSONArray("Answer") ?: return@runCatching
                    for (i in 0 until answers.length()) {
                        val a = answers.optJSONObject(i) ?: continue
                        val data = a.optString("data", "").trim().trimEnd('.')
                        when (a.optInt("type", 0)) {
                            1 -> if (isIpv4Literal(data)) literalAddress(data)?.let { ipv4 += it }
                            28 -> if (data.contains(':')) literalAddress(data)?.let { ipv6 += it }
                            5 -> if (data.isNotBlank()) cnames += data
                        }
                    }
                }
            }
            if (type == "A" && ipv4.isNotEmpty()) return ipv4.toList()
        }

        if (ipv4.isNotEmpty()) return ipv4.toList()
        cnames.forEach { alias ->
            val nested = resolveEncrypted(alias, depth + 1)
            if (nested.isNotEmpty()) return nested
        }
        return ipv6.toList()
    }

    private fun queryResolver(resolver: Resolver, hostname: String, type: String): JSONObject {
        val bootstrap = object : Dns {
            override fun lookup(name: String): List<InetAddress> {
                if (name.equals(resolver.host, ignoreCase = true)) {
                    val addresses = resolver.bootstrapIps.mapNotNull { literalAddress(it) }
                    val v4 = ipv4First(addresses)
                    if (v4.isNotEmpty()) return v4
                    if (addresses.isNotEmpty()) return addresses
                }
                return Dns.SYSTEM.lookup(name)
            }
        }

        val client = OkHttpClient.Builder()
            .dns(bootstrap)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val request = Request.Builder()
            .url(resolver.urlFor(hostname, type))
            .header("Accept", "application/dns-json")
            .header("User-Agent", "ATTEND-PRO-SecureDNS/2")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("DNS resolver HTTP ${response.code}")
            return JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
        }
    }

    private fun isIpv4Literal(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all {
            val n = it.toIntOrNull()
            n != null && n in 0..255
        }
    }

    private fun ipv4First(addresses: List<InetAddress>): List<InetAddress> {
        if (addresses.isEmpty()) return emptyList()
        val v4 = addresses.filterIsInstance<Inet4Address>()
        return if (v4.isNotEmpty()) v4 else emptyList()
    }

    private fun literalAddress(value: String): InetAddress? =
        runCatching { InetAddress.getByName(value) }.getOrNull()
}
