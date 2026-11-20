// EH3D-ANDROID-CONTROL - the rules of the connection that the app follows from the client of the suite: the settings and their ranges, the pauses and what may be sent twice, when a login is renewed,
// the sessions and the machines of the plugins that the server lists, and the servers found on the local network.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import es.electrohobby3d.eh3d.model.Candidate
import es.electrohobby3d.eh3d.model.ConnectionSettings
import es.electrohobby3d.eh3d.model.MAX_CANDIDATES
import es.electrohobby3d.eh3d.model.RefreshRule
import es.electrohobby3d.eh3d.model.RetryRules
import es.electrohobby3d.eh3d.model.addCandidate
import es.electrohobby3d.eh3d.model.parseSessions
import es.electrohobby3d.eh3d.model.parseWorkMachines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTest {
    @Test fun `the defaults are the ones of the client of the suite and the search on the network is off`() {
        val s = ConnectionSettings()
        assertEquals(15, s.timeoutS)
        assertEquals(2, s.retries)
        assertFalse(s.mdns)
    }

    @Test fun `a value out of its range is brought to the nearest end`() {
        assertEquals(ConnectionSettings(1, 0, true), ConnectionSettings.of(-4, -1, true))
        assertEquals(ConnectionSettings(120, 5, false), ConnectionSettings.of(999, 99, false))
        assertEquals(ConnectionSettings(30, 3, true), ConnectionSettings.of(30, 3, true))
        assertEquals(1, ConnectionSettings().withTimeout(0).timeoutS)
        assertEquals(5, ConnectionSettings().withRetries(6).retries)
        assertTrue(ConnectionSettings().withMdns(true).mdns)
    }

    @Test fun `the pauses between tries grow from 200 ms and stop at two seconds`() {
        assertEquals(listOf(200L, 400L, 800L, 1_600L, 2_000L, 2_000L), (0..5).map { RetryRules.delayMs(it) })
        assertEquals(200L, RetryRules.delayMs(-3))
        assertEquals(2_000L, RetryRules.delayMs(1_000))
    }

    @Test fun `a connection that could not be made is tried again for any method`() {
        for (method in listOf("GET", "POST", "PUT", "DELETE", "PATCH")) assertTrue(method, RetryRules.mayRepeat(method, 0, neverSent = true))
    }

    @Test fun `a request that was sent and got no answer is tried again only if it does no harm twice`() {
        for (method in listOf("GET", "get", "HEAD", "PUT", "DELETE", "OPTIONS")) assertTrue(method, RetryRules.mayRepeat(method, 0, neverSent = false))
        for (method in listOf("POST", "PATCH")) assertFalse(method, RetryRules.mayRepeat(method, 0, neverSent = false))
    }

    @Test fun `a 502, 503 or 504 is tried again for the same methods and nothing else is`() {
        for (status in listOf(502, 503, 504)) {
            assertTrue(RetryRules.mayRepeat("GET", status, false))
            assertTrue(RetryRules.mayRepeat("DELETE", status, false))
            assertFalse(RetryRules.mayRepeat("POST", status, false))
        }
        for (status in listOf(400, 401, 403, 404, 409, 422, 500, 501)) assertFalse(status.toString(), RetryRules.mayRepeat("GET", status, false))
    }

    @Test fun `a login is renewed in the last quarter of its life only`() {
        assertFalse(RefreshRule.due(3600, 3600))
        assertFalse(RefreshRule.due(3600, 901))
        assertTrue(RefreshRule.due(3600, 900))
        assertTrue(RefreshRule.due(3600, 1))
        assertFalse(RefreshRule.due(3600, 0))
        assertFalse(RefreshRule.due(3600, -5))
        assertFalse(RefreshRule.due(0, 100))
    }

    @Test fun `the sessions are read with their application and which one is this`() {
        val list = parseSessions("""{"sessions":[{"id":"a1","name":"ana","role":"admin","app":"android","ip":"10.0.0.2","current":true},{"id":"b2","name":"ana","role":"operator"},{"name":"no id"}]}""")
        assertEquals(listOf("a1", "b2"), list.map { it.id })
        assertTrue(list[0].current)
        assertEquals("android", list[0].app)
        assertEquals("", list[1].app)
        assertEquals("", list[1].ip)
        assertFalse(list[1].current)
    }

    @Test fun `an answer with no list of sessions is an empty one`() {
        assertTrue(parseSessions("{}").isEmpty())
        assertTrue(parseSessions("""{"sessions":[]}""").isEmpty())
    }

    @Test fun `the machines of the plugins show their detail and the ones of the fleet are left out`() {
        val text = """{"machines":[
            {"id":"drill","name":"Drill","plugin":"bungard","state":"idle"},
            {"id":"l1","name":"Laser","plugin":"laser","state":"busy","detail":{"estop_latched":true,"position_mm":{"x":1.5,"y":2},"power":40,"feeders":[1,2,3]}},
            {"id":"p1","plugin":"printer3d","state":"idle","detail":{"online":true,"latched":false,"note":null}},
            {"name":"no id","plugin":"cnc"}]}"""
        val list = parseWorkMachines(text)
        assertEquals(listOf("l1", "p1"), list.map { it.id })
        val laser = list[0]
        assertEquals("Laser", laser.name)
        assertEquals("laser", laser.plugin)
        assertTrue(laser.latched)
        assertEquals(listOf("estop_latched" to "true", "feeders" to "[3]", "position_mm.x" to "1.5", "position_mm.y" to "2", "power" to "40"), laser.detail)
        val printer = list[1]
        assertEquals("p1", printer.name)
        assertFalse(printer.latched)
        assertEquals(listOf("latched" to "false", "online" to "true"), printer.detail)
    }

    @Test fun `a machine without detail has no lines and an answer without machines is empty`() {
        assertEquals(emptyList<Pair<String, String>>(), parseWorkMachines("""{"machines":[{"id":"x","plugin":"pnp"}]}""").single().detail)
        assertTrue(parseWorkMachines("{}").isEmpty())
    }

    @Test fun `an alarm in the detail marks the machine as latched`() {
        assertTrue(parseWorkMachines("""{"machines":[{"id":"c","plugin":"cnc","detail":{"alarm":true}}]}""").single().latched)
    }

    @Test fun `a very deep or very long detail is cut short`() {
        val deep = """{"machines":[{"id":"c","plugin":"cnc","detail":{"a":{"b":{"c":{"d":1}}}}}]}"""
        assertEquals(listOf("a.b.c" to "{…}"), parseWorkMachines(deep).single().detail)
        val many = (1..40).joinToString(",") { "\"k$it\":$it" }
        assertEquals(24, parseWorkMachines("""{"machines":[{"id":"c","plugin":"cnc","detail":{$many}}]}""").single().detail.size)
    }

    @Test fun `a server found on the network is a candidate once, and a bad name or port is not one`() {
        var found = emptyList<Candidate>()
        found = addCandidate(found, "cm5.local.", 18200)
        found = addCandidate(found, "cm5.local", 18200)
        found = addCandidate(found, "192.168.0.180", 18200)
        assertEquals(listOf("cm5.local:18200", "192.168.0.180:18200"), found.map { it.label })
        assertEquals(found, addCandidate(found, null, 18200))
        assertEquals(found, addCandidate(found, "  ", 18200))
        assertEquals(found, addCandidate(found, "a b", 18200))
        assertEquals(found, addCandidate(found, "evil/host", 18200))
        assertEquals(found, addCandidate(found, "x.local", 0))
        assertEquals(found, addCandidate(found, "x.local", 70_000))
    }

    @Test fun `the list of candidates is short`() {
        var found = emptyList<Candidate>()
        for (i in 1..20) found = addCandidate(found, "h$i.local", 18200)
        assertEquals(MAX_CANDIDATES, found.size)
        assertEquals("h1.local:18200", found.first().label)
    }
}
