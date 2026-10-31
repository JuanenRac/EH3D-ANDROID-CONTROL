package es.electrohobby3d.eh3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerEndpointTest {
    @Test fun acceptsTlsAndPrivateLanOriginsOnly() {
        assertEquals("https://eh3d.example:8443", ServerEndpoint.parse("https://eh3d.example:8443")?.origin)
        assertEquals("http://192.168.0.80:8080", ServerEndpoint.parse("http://192.168.0.80:8080")?.origin)
        assertNull(ServerEndpoint.parse("http://example.com:8080"))
        assertNull(ServerEndpoint.parse("https://token@example.com"))
    }

    @Test fun buildsOnlyPrivateLanHostAndPortEntries() {
        assertEquals("http://192.168.0.80:8080", ServerEndpoint.fromHostAndPort("192.168.0.80", "8080")?.origin)
        assertEquals("https://eh3d.example:8443", ServerEndpoint.fromHostAndPort("https://eh3d.example", "8443")?.origin)
        assertNull(ServerEndpoint.fromHostAndPort("example.com", "8080"))
        assertNull(ServerEndpoint.fromHostAndPort("192.168.0.80", "70000"))
    }

    // A host name that merely starts like a private address must never receive a password in clear text.
    @Test fun aHostNameThatLooksPrivateIsNotPrivate() {
        for (host in listOf("10.attacker.example", "192.168.evil.com", "172.16.0.1.example.com", "10.0.0.1.nip.io", "127.0.0.1.evil.net")) {
            assertNull(host, ServerEndpoint.parse("http://$host:8080"))
        }
    }

    @Test fun recognisesEveryPrivateRangeAndNothingElse() {
        for (host in listOf("10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.255.255", "192.168.0.180", "127.0.0.1", "127.1.2.3", "localhost")) {
            assertTrue(host, ServerEndpoint.isPrivateLanHost(host))
        }
        for (host in listOf("172.15.0.1", "172.32.0.1", "192.169.0.1", "11.0.0.1", "8.8.8.8", "256.1.1.1", "1.2.3", "1.2.3.4.5", "", "eh3d.local", "1.2.3.4x")) {
            assertFalse(host, ServerEndpoint.isPrivateLanHost(host))
        }
    }

    // Some resolvers read a leading zero as octal: "010.0.0.1" is then the public address 8.0.0.1.
    @Test fun refusesAddressesWithLeadingZeros() {
        for (host in listOf("010.0.0.1", "10.00.0.1", "192.168.000.5", "0177.0.0.1")) {
            assertFalse(host, ServerEndpoint.isPrivateLanHost(host))
        }
    }

    @Test fun rejectsQueriesFragmentsAndOtherSchemes() {
        assertNull(ServerEndpoint.parse("http://192.168.0.5:8080?x=1"))
        assertNull(ServerEndpoint.parse("http://192.168.0.5:8080#frag"))
        assertNull(ServerEndpoint.parse("ftp://192.168.0.5"))
        assertNull(ServerEndpoint.parse("not a url"))
        assertNull(ServerEndpoint.parse(""))
    }

    // An address of the house is stable; a public one lasts until the operator changes it, so the sign-in suggests a name.
    @Test fun aPublicIpAddressIsToldFromANameAndFromTheHouse() {
        assertTrue(ServerEndpoint.usesPublicIpAddress("203.0.113.9"))
        assertTrue(ServerEndpoint.usesPublicIpAddress("https://203.0.113.9:8443/"))
        assertFalse(ServerEndpoint.usesPublicIpAddress("192.168.0.180"))
        assertFalse(ServerEndpoint.usesPublicIpAddress("http://10.0.0.5"))
        assertFalse(ServerEndpoint.usesPublicIpAddress("casa.duckdns.org"))
        assertFalse(ServerEndpoint.usesPublicIpAddress("https://casa.duckdns.org"))
        assertFalse(ServerEndpoint.usesPublicIpAddress("999.1.1.1"))
        assertFalse(ServerEndpoint.usesPublicIpAddress("  "))
    }

    @Test fun normalisesTheOriginItKeeps() {
        assertEquals("http://192.168.0.5:18080", ServerEndpoint.parse("  http://192.168.0.5:18080/  ")?.origin)
        assertEquals("https://eh3d.example", ServerEndpoint.parse("https://eh3d.example")?.origin)
    }
}
