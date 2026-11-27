// EH3D-ANDROID-CONTROL - Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushEventTest {
    @Test fun the_three_words_of_the_server_are_read_and_nothing_else() {
        assertEquals(PushEvent.Waiting, pushEvent("waiting"))
        assertEquals(PushEvent.Failed, pushEvent("failed"))
        assertEquals(PushEvent.Finished, pushEvent("finished"))
        listOf(null, "", "Waiting", "done", "waiting ").forEach { assertNull(pushEvent(it)) }
    }
}
