// EH3D-ANDROID-CONTROL - the nodes of the pick-and-place machines as the phone reads them: the list of GET /pnp/nodes, read only. A simulated answer of the server (nothing here has talked to a real feeder or head),
// a seed that does not change for the random answers, and a socket of this computer for the request. EH3D-STUDIO has the forms; the phone never reads or sends a token.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import com.sun.net.httpserver.HttpServer
import es.electrohobby3d.eh3d.model.parsePnpNodes
import es.electrohobby3d.eh3d.network.ApiException
import es.electrohobby3d.eh3d.network.Eh3dApi
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.Random
import java.util.concurrent.CopyOnWriteArrayList

class PnpNodesTest {
    private companion object {
        /** The answer of the server for a feeder that is ready and a head that is silent. */
        const val ANSWER = """{"nodes":[
            {"id":"feeder-1","kind":"feeder","name":"Feeders left","address":"192.168.0.60:80","token":"********","machine":"pnp-1","enabled":true,"health":"ok","ready":true,"silent_for_s":null,
             "polls":{"ok":7,"failed":1},"last_error":"","status":{"safe":false,"selfcheck":"ok","slots":4,"online":3,"low":1,"empty":0,"faulted":0,"bus":"rs485","node":"feeder-a"},"evidence":"simulator","simulator_only":true},
            {"id":"head-1","kind":"head","name":"Head","address":"192.168.0.61:80","token":"","machine":"","enabled":true,"health":"silent","ready":false,
             "polls":{"ok":0,"failed":9},"last_error":"unreachable","status":null,"evidence":"simulator"}],
            "summary":{"registered":2,"ready":1,"not_ready":1},"limits":{"max":16,"poll_s":5,"silence_s":30,"timeout_s":3,"kinds":["feeder","head"]}}"""
    }

    private class Seen(val method: String, val path: String, val authorization: String?)

    /** Runs [block] against a server that answers every request with [status] and [text], and writes down what it was asked. */
    private fun withServer(status: Int, text: String, block: (String, List<Seen>) -> Unit) {
        val seen = CopyOnWriteArrayList<Seen>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            seen.add(Seen(exchange.requestMethod, exchange.requestURI.rawPath, exchange.requestHeaders.getFirst("Authorization")))
            exchange.requestBody.readBytes()
            val bytes = text.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) } else exchange.close()
        }
        server.start()
        try { block("http://127.0.0.1:${server.address.port}", seen) } finally { server.stop(0) }
    }

    @Test fun `a feeder and a head are read with their health, readiness and the numbers they said last`() {
        val nodes = parsePnpNodes(ANSWER)
        assertEquals(listOf("feeder-1", "head-1"), nodes.map { it.id })
        val feeder = nodes[0]
        assertEquals("feeder", feeder.kind)
        assertEquals("Feeders left", feeder.name)
        assertEquals("192.168.0.60:80", feeder.address)
        assertEquals("pnp-1", feeder.machine)
        assertTrue(feeder.enabled)
        assertEquals("ok", feeder.health)
        assertTrue(feeder.ready)
        assertTrue(feeder.simulatorOnly)
        assertEquals(
            listOf("safe" to "false", "selfcheck" to "ok", "online" to "3", "slots" to "4", "low" to "1", "empty" to "0", "faulted" to "0"),
            feeder.facts,
        )
        val head = nodes[1]
        assertEquals("head", head.kind)
        assertEquals("silent", head.health)
        assertFalse(head.ready)
        assertEquals("unreachable", head.lastError)
        assertEquals("", head.machine)
        assertEquals(emptyList<Pair<String, String>>(), head.facts)
    }

    @Test fun `the token of a node is never read, so it cannot be shown`() {
        val text = parsePnpNodes(ANSWER).toString()
        assertFalse(text.contains("********"))
        assertFalse(text.contains("token"))
    }

    @Test fun `an answer that does not say is not ready, and a node without an id or a list that is not one is left out`() {
        val node = parsePnpNodes("""{"nodes":[{"id":"n1"},{"name":"no id"},{"id":""}]}""").single()
        assertEquals("n1", node.id)
        assertEquals("n1", node.name)
        assertEquals("unknown", node.health)
        assertFalse(node.ready)
        assertTrue(node.enabled)
        assertEquals(emptyList<Any>(), parsePnpNodes("{}"))
        assertEquals(emptyList<Any>(), parsePnpNodes("""{"nodes":"x"}"""))
        assertEquals(emptyList<Any>(), parsePnpNodes("""{"nodes":[]}"""))
    }

    @Test fun `only the numbers the phone lists are taken from what a node said, in a fixed order`() {
        val node = parsePnpNodes("""{"nodes":[{"id":"h","kind":"head","status":{"pump":true,"extra":"x","faulted":2,"safe":null,"nozzle_count":2}}]}""").single()
        assertEquals(listOf("faulted" to "2", "nozzle_count" to "2", "pump" to "true"), node.facts)
    }

    @Test fun `answers made from a fixed seed with any kind of value never break the reading`() {
        val random = Random(20261121L)
        val pieces: List<Any> = listOf("x", "", 5, 1.5, true, false, JSONObject.NULL, JSONObject("""{"safe":true,"pump":"on"}"""))
        val keys = listOf("id", "kind", "name", "address", "machine", "enabled", "health", "ready", "last_error", "status", "evidence", "simulator_only")
        repeat(300) {
            val array = org.json.JSONArray()
            repeat(random.nextInt(4)) {
                val item = JSONObject()
                for (key in keys) if (random.nextBoolean()) item.put(key, pieces[random.nextInt(pieces.size)])
                array.put(item)
            }
            val nodes = parsePnpNodes(JSONObject().put("nodes", array).toString())
            assertTrue(nodes.all { it.id.isNotEmpty() })
            assertTrue(nodes.size <= array.length())
        }
    }

    @Test fun `the call asks GET pnp nodes with the token and reads the list`() = withServer(200, ANSWER) { origin, seen ->
        val nodes = runBlocking { Eh3dApi(origin, "t_abc").pnpNodes() }
        assertEquals(2, nodes.size)
        assertEquals("GET", seen[0].method)
        assertEquals("/api/v1/pnp/nodes", seen[0].path)
        assertEquals("Bearer t_abc", seen[0].authorization)
    }

    @Test fun `a server that has the nodes switched off answers with its code`() = withServer(501, """{"error":"pnp_nodes_off","detail":"the nodes are off"}""") { origin, _ ->
        val error = assertThrows(ApiException::class.java) { runBlocking { Eh3dApi(origin, "t").pnpNodes() } }
        assertEquals(501, error.status)
        assertEquals("pnp_nodes_off", error.code)
        assertFalse(error.unauthorized)
    }
}
