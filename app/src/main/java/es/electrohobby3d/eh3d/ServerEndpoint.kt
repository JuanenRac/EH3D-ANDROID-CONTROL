// EH3D-ANDROID-CONTROL — safe endpoint parsing.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import java.net.URI

data class ServerEndpoint(val origin: String) {
    companion object {
        /** Builds the normal private-LAN HTTP origin entered by an operator. */
        fun fromHostAndPort(hostInput: String, portInput: String): ServerEndpoint? {
            val source = hostInput.trim()
            val scheme = if (source.startsWith("https://", ignoreCase = true)) "https" else "http"
            val host = source.removePrefix("http://").removePrefix("https://").substringBefore('/').substringBefore(':')
            val port = portInput.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            return parse("${scheme}://${host}:${port}")
        }

        /**
         * True when what the person typed names a place outside the home network but does not ask for a secure connection: a public address or a name without "https://".
         * The app refuses to send the password that way, and the sign-in explains it instead of just staying disabled.
         */
        fun needsSecureConnection(hostInput: String): Boolean {
            val source = hostInput.trim()
            if (source.isEmpty() || source.startsWith("https://", ignoreCase = true)) return false
            val host = source.removePrefix("http://").substringBefore('/').substringBefore(':')
            return host.isNotEmpty() && !isPrivateLanHost(host)
        }

        /**
         * True when what the person typed is a public IPv4 address (not a name, not an address of the house): such an address stops working the day the operator changes it, and the sign-in says so
         * and suggests a name that follows the house (dynamic DNS).
         */
        fun usesPublicIpAddress(hostInput: String): Boolean {
            val source = hostInput.trim()
            if (source.isEmpty()) return false
            val host = source.removePrefix("http://").removePrefix("https://").substringBefore('/').substringBefore(':')
            return IPV4.matches(host) && !isPrivateLanHost(host)
        }

        // No leading zeros: some resolvers read "010.0.0.1" as octal, i.e. the public address 8.0.0.1.
        private val IPV4 = Regex("""^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$""")

        /**
         * Plain HTTP is allowed only to a private-LAN or loopback address. The host must be an IPv4 literal
         * (or "localhost"): a name such as "10.attacker.example" merely starts like a private address and
         * would send the password in clear text to the public internet.
         */
        internal fun isPrivateLanHost(host: String): Boolean {
            if (host == "localhost") return true
            if (!IPV4.matches(host)) return false
            val octets = host.split('.').map { it.toInt() }
            return octets[0] == 127 || octets[0] == 10 || (octets[0] == 192 && octets[1] == 168) || (octets[0] == 172 && octets[1] in 16..31)
        }

        fun parse(value: String): ServerEndpoint? = try {
            val uri = URI(value.trim())
            val host = uri.host ?: return null
            // a port that does not exist is not an address (java.net.URI reads any number)
            if (uri.port != -1 && uri.port !in 1..65535) return null
            val accepted = uri.scheme == "https" || (uri.scheme == "http" && isPrivateLanHost(host))
            if (!accepted || uri.userInfo != null || uri.query != null || uri.fragment != null) null
            else ServerEndpoint("${uri.scheme}://${host}" + if (uri.port == -1) "" else ":${uri.port}")
        } catch (_: Exception) { null }
    }
}
