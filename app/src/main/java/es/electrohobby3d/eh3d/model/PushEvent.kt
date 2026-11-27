// EH3D-ANDROID-CONTROL - what a message of the server through Firebase can say. Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.model

enum class PushEvent { Waiting, Failed, Finished }

/** The event of the `eh3d` field of a message; anything else says nothing. */
fun pushEvent(word: String?): PushEvent? = when (word) {
    "waiting" -> PushEvent.Waiting
    "failed" -> PushEvent.Failed
    "finished" -> PushEvent.Finished
    else -> null
}
