// EH3D-ANDROID-CONTROL - the notices of the phone: a machine that waits for the operator or stops. Shown only while the app watches the server, and only if the person allowed notices.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import es.electrohobby3d.eh3d.model.Notice
import es.electrohobby3d.eh3d.model.NoticeKind

object Notifier {
    private const val CHANNEL = "machines"

    /** The notice for a change of the fleet (the same text whether the app watches or the periodic job does). */
    fun showNotice(context: Context, n: Notice) = show(
        context, n.machineId.hashCode(), n.machineName,
        if (n.kind == NoticeKind.Waiting) tr("Espera al operario: confirme el paso en la máquina.") else tr("El trabajo ha terminado o se ha detenido."),
    )

    fun show(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, tr("Máquinas"), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(context, 0, Intent(context, Eh3dActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(id, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text).setContentIntent(open).setAutoCancel(true).build())
    }
}
