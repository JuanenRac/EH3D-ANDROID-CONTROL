// EH3D-ANDROID-CONTROL - how long the phone listens to the server while a job runs. Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.model

/** Between two questions to the server while listening. */
const val LISTEN_EVERY_MS = 5_000L

/** Listening never lasts longer than this: a job that is never ended does not keep the phone awake for ever (Android allows a data service about six hours a day). */
const val LISTEN_MOST_MS = 5 * 60 * 60 * 1_000L

/** Whether some machine has a job that is going on or waits for the operator. */
fun anyJobGoingOn(fleet: Fleet): Boolean = fleet.machines.any { m -> m.job?.let { JobStates.active(it.state) } == true }

/** What the listener remembers between two questions. */
data class Listening(val quiet: Int = 0, val failures: Int = 0) {
    /** After an answer: three answers in a row with nothing going on end the listening. */
    fun answered(fleet: Fleet): Listening = Listening(quiet = if (anyJobGoingOn(fleet)) 0 else quiet + 1)

    /** After a question without an answer: about two minutes of silence end it. */
    fun failed(): Listening = copy(failures = failures + 1)

    fun goOn(elapsedMs: Long): Boolean = quiet < 3 && failures < 24 && elapsedMs < LISTEN_MOST_MS
}
