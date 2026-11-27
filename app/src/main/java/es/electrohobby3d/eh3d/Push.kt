// EH3D-ANDROID-CONTROL - notices that arrive with the app closed, through Firebase Cloud Messaging: off until the person switches them on, and only in a build that carries the
// owner's own Firebase project (app/google-services.json, never in the repository). A message says which of three things happened and nothing else; the text is written here.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later, with the additional permission of LICENSE-EXCEPTION.md for the Google libraries.
package es.electrohobby3d.eh3d

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import es.electrohobby3d.eh3d.model.ConnectionSettings
import es.electrohobby3d.eh3d.model.PushEvent
import es.electrohobby3d.eh3d.model.pushEvent
import es.electrohobby3d.eh3d.network.Eh3dApi
import kotlinx.coroutines.runBlocking

object Push {
    private const val PREFERENCES = "eh3d-control"
    const val SWITCH = "push_enabled"

    /** Whether this build carries a Firebase project. */
    fun available(): Boolean = BuildConfig.FIREBASE_APP_ID.isNotEmpty()

    fun on(context: Context): Boolean = available() && context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(SWITCH, false)

    fun init(context: Context) {
        if (!available()) return
        runCatching {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build(),
            )
        }
    }

    /** Asks Firebase for the token of this phone and hands it over; nothing happens when Firebase cannot be reached. */
    fun withToken(use: (String) -> Unit) {
        runCatching {
            FirebaseMessaging.getInstance().isAutoInitEnabled = true
            FirebaseMessaging.getInstance().token.addOnSuccessListener { use(it) }
        }
    }

    /** Stops Firebase from keeping a token for this phone. */
    fun stop() {
        runCatching {
            FirebaseMessaging.getInstance().isAutoInitEnabled = false
            FirebaseMessaging.getInstance().deleteToken()
        }
    }
}

class Eh3dApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Push.init(this)
    }
}

class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (!Push.on(this)) return
        val event = pushEvent(message.data["eh3d"]) ?: return
        Tr.init(applicationContext)
        val text = when (event) {
            PushEvent.Waiting -> tr("Un trabajo espera al operario.")
            PushEvent.Failed -> tr("Un trabajo ha fallado.")
            PushEvent.Finished -> tr("Un trabajo ha terminado.")
        }
        Notifier.show(applicationContext, event.ordinal + 1, "EH3D", text)
    }

    /** Firebase changed the token: the server learns the new one with the session kept in the vault; without a session it learns it at the next sign-in. */
    override fun onNewToken(token: String) {
        if (!Push.on(this)) return
        val preferences = applicationContext.getSharedPreferences("eh3d-control", Context.MODE_PRIVATE)
        val origin = preferences.getString("origin", "").orEmpty()
        if (origin.isBlank()) return
        val session = SessionVault(PreferencesStore(preferences), KeystoreBox()).load(origin) ?: return
        val settings = ConnectionSettings.read(preferences::getInt, preferences::getBoolean)
        runBlocking { runCatching { Eh3dApi(origin, session.value, settings).registerPhone(token) } }
    }
}
