// EH3D-ANDROID-CONTROL - what ends a kept session at start-up: only a refusal of the server.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import es.electrohobby3d.eh3d.network.ApiException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeptSessionTest {
    @Test fun `a 401 of the server ends the kept session`() {
        assertTrue(ApiException(401, "unauthorized", "no").endsKeptSession)
    }

    @Test fun `a server out of reach, an error of the server and a page of another service keep it`() {
        assertFalse(ApiException(0, "unreachable", "no answer").endsKeptSession)
        assertFalse(ApiException(500, "server_error", "boom").endsKeptSession)
        assertFalse(ApiException(503, "unavailable", "busy").endsKeptSession)
        assertFalse(ApiException(200, "bad_json", "the answer is not JSON").endsKeptSession)
        assertFalse(ApiException(403, "forbidden", "no").endsKeptSession)
    }
}
