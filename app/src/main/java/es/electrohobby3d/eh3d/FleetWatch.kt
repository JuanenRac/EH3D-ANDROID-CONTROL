// EH3D-ANDROID-CONTROL - the watch of the fleet while the app is not in sight: a periodic job (WorkManager, every 15 minutes at best, the least Android allows) asks the server and tells,
// in a notice, a machine that waits for the operator or a job that ended. It uses the session kept in the vault of the app; without a session, or when the server does not answer, it does nothing.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import es.electrohobby3d.eh3d.model.fleetNotices
import es.electrohobby3d.eh3d.model.fleetStates
import es.electrohobby3d.eh3d.network.Eh3dApi
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object FleetWatch {
    private const val NAME = "fleet-watch"

    /** Starts the periodic job (once: it keeps the one that already exists). */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<FleetWatchWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request) }
    }

    fun cancel(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(NAME) }
        runCatching { context.getSharedPreferences("eh3d-control", Context.MODE_PRIVATE).edit().remove("watch_states").apply() }
    }
}

class FleetWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val preferences = applicationContext.getSharedPreferences("eh3d-control", Context.MODE_PRIVATE)
        val origin = preferences.getString("origin", "").orEmpty()
        if (origin.isBlank()) return Result.success()
        val session = SessionVault(PreferencesStore(preferences), KeystoreBox()).load(origin) ?: return Result.success()
        val fleet = try { Eh3dApi(origin, session.value).fleet() } catch (_: Exception) { return Result.success() }
        Tr.init(applicationContext)
        val before = runCatching {
            val saved = JSONObject(preferences.getString("watch_states", "{}").orEmpty())
            saved.keys().asSequence().associateWith { saved.getString(it) }
        }.getOrDefault(emptyMap())
        for (notice in fleetNotices(before, fleet)) Notifier.showNotice(applicationContext, notice)
        val now = JSONObject()
        fleetStates(fleet).forEach { (id, state) -> now.put(id, state) }
        preferences.edit().putString("watch_states", now.toString()).apply()
        return Result.success()
    }
}
