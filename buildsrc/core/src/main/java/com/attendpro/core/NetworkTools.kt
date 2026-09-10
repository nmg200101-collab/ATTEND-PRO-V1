package com.attendpro.core

import java.net.InetSocketAddress
import java.net.Socket

object NetworkTools {
    /** Generic TCP reachability probe used only for external fingerprint hardware/network diagnostics. */
    fun probeTcp(host: String, port: Int, timeoutMillis: Int = 2500): Result<Unit> = runCatching {
        Socket().use { socket -> socket.connect(InetSocketAddress(host, port), timeoutMillis) }
    }
}
