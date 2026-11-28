// EH3D-ANDROID-CONTROL - Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenTest {
    @Test fun an_empty_fleet_has_no_job_and_three_quiet_answers_end_the_listening() {
        assertFalse(anyJobGoingOn(Fleet.EMPTY))
        var state = Listening()
        repeat(2) { state = state.answered(Fleet.EMPTY); assertTrue(state.goOn(0)) }
        state = state.answered(Fleet.EMPTY)
        assertFalse(state.goOn(0))
    }

    @Test fun silence_and_a_very_long_wait_end_it_and_an_answer_forgets_the_failures() {
        var state = Listening()
        repeat(23) { state = state.failed(); assertTrue(state.goOn(0)) }
        assertEquals(0, state.answered(Fleet.EMPTY).failures)
        assertFalse(state.failed().goOn(0))
        assertFalse(Listening().goOn(LISTEN_MOST_MS))
        assertTrue(Listening().goOn(LISTEN_MOST_MS - 1))
    }
}
