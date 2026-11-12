package es.electrohobby3d.eh3d

import com.sun.net.httpserver.HttpServer
import es.electrohobby3d.eh3d.network.Eh3dApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/** Signing out asks the server to revoke the token that is being closed, and a server that does not answer never breaks the sign-out. The server here is a socket of this computer. */
class SignOutTest {
    private class Seen(val method: String, val path: String, val authorization: String?)

    @Test fun signingOutAsksTheServerToRevokeTheTokenThatIsClosed() {
        val seen = CopyOnWriteArrayList<Seen>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            seen.add(Seen(exchange.requestMethod, exchange.requestURI.path, exchange.requestHeaders.getFirst("Authorization")))
            val body = "{\"ok\":true}".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            runBlocking { Eh3dApi("http://127.0.0.1:${server.address.port}", "t_closing").logout() }
        } finally {
            server.stop(0)
        }
        assertEquals(1, seen.size)
        assertEquals("POST", seen[0].method)
        assertEquals("/api/v1/logout", seen[0].path)
        assertEquals("Bearer t_closing", seen[0].authorization)
    }

    @Test fun aServerThatDoesNotAnswerDoesNotBreakTheSignOut() {
        val port = ServerSocket(0).use { it.localPort }   // a port nobody listens on
        runBlocking { Eh3dApi("http://127.0.0.1:$port", "t_closing").logout() }
        assertTrue(true)   // reaching here is the check: the call is wrapped and nothing was thrown
    }

    @Test fun aServerThatRefusesTheTokenDoesNotBreakTheSignOutEither() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = "{\"error\":\"unauthorized\"}".toByteArray()
            exchange.sendResponseHeaders(401, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            runBlocking { Eh3dApi("http://127.0.0.1:${server.address.port}", "t_old").logout() }
        } finally {
            server.stop(0)
        }
    }
}
