// EH3D-ANDROID-CONTROL - notices at once without Google: while a job runs, a foreground service asks the owner's own server every few seconds and tells a machine that waits
// for the operator or a job that ended; it stops by itself when nothing is going on. Off until switched on; it starts when the app, in sight, sees a job running.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import es.electrohobby3d.eh3d.model.ConnectionSettings
import es.electrohobby3d.eh3d.model.LISTEN_EVERY_MS
import es.electrohobby3d.eh3d.model.Listening
import es.electrohobby3d.eh3d.model.fleetNotices
import es.electrohobby3d.eh3d.model.fleetStates
import es.electrohobby3d.eh3d.network.Eh3dApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object LiveListen {
    const val SWITCH = "live_listen"

    /** While the service listens, it is the one that tells; the app in sight keeps quiet. */
    @Volatile var running = false

    fun on(context: Context): Boolean = context.getSharedPreferences("eh3d-control", Context.MODE_PRIVATE).getBoolean(SWITCH, false)

    /** Only works while the app is in sight (Android refuses to start it from the background): a refusal is not an error. */
    fun start(context: Context) {
        if (running || !on(context)) return
        runCatching { ContextCompat.startForegroundService(context, Intent(context, LiveListenService::class.java)) }
    }

    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, LiveListenService::class.java)) }
    }
}

class LiveListenService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Tr.init(applicationContext)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, tr("Escucha del trabajo"), NotificationManager.IMPORTANCE_LOW))
        val notice = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("EH3D")
            .setContentText(tr("Escuchando mientras hay un trabajo en marcha.")).setOngoing(true).build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        runCatching { ServiceCompat.startForeground(this, ONGOING, notice, type) }.onFailure { stopSelf(); return START_NOT_STICKY }
        LiveListen.running = true
        if (work?.isActive != true) work = scope.launch { listen(); stopSelf() }
        return START_NOT_STICKY
    }

    private suspend fun listen() {
        val preferences = applicationContext.getSharedPreferences("eh3d-control", Context.MODE_PRIVATE)
        val origin = preferences.getString("origin", "").orEmpty()
        if (origin.isBlank()) return
        val session = SessionVault(PreferencesStore(preferences), KeystoreBox()).load(origin) ?: return
        val api = Eh3dApi(origin, session.value, ConnectionSettings.read(preferences::getInt, preferences::getBoolean))
        val started = SystemClock.elapsedRealtime()
        var state = Listening()
        var before = emptyMap<String, String>()
        while (LiveListen.on(applicationContext) && state.goOn(SystemClock.elapsedRealtime() - started)) {
            val fleet = runCatching { api.fleet() }.getOrNull()
            if (fleet == null) {
                state = state.failed()
            } else {
                for (change in fleetNotices(before, fleet)) Notifier.showNotice(applicationContext, change)
                before = fleetStates(fleet)
                state = state.answered(fleet)
            }
            delay(LISTEN_EVERY_MS)
        }
    }

    /** Android ends a data service that has run its hours. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        LiveListen.running = false
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "listen"
        const val ONGOING = 7001
    }
}
