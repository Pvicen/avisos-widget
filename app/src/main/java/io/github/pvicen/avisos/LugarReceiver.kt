package io.github.pvicen.avisos

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/**
 * Android avisa aquí al entrar o salir de la zona de un lugar. Al llegar se enseña una
 * notificación con lo pendiente de ese lugar, una vez por visita: no se repite hasta salir y
 * volver (ni antes de 2 horas). Si la salida se perdió (teléfono apagado), la visita caduca a
 * las 12 horas.
 */
class LugarReceiver : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intent: Intent) {
        val evento = GeofencingEvent.fromIntent(intent) ?: return
        if (evento.hasError()) return
        val ids = evento.triggeringGeofences?.map { it.requestId } ?: return
        when (evento.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> ids.forEach { llegada(contexto, it) }
            Geofence.GEOFENCE_TRANSITION_EXIT -> ids.forEach { salida(contexto, it) }
        }
    }

    companion object {
        private const val PREFS = "visitas"
        private const val CANAL = "lugares"
        private const val ENTRE_AVISOS = 2 * 60 * 60 * 1000L
        private const val VISITA_MAXIMA = 12 * 60 * 60 * 1000L
        private const val MAXIMO_LINEAS = 5

        fun llegada(contexto: Context, lugarId: String) {
            val prefs = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val ahora = System.currentTimeMillis()
            val dentroDesde = prefs.getLong("dentro:$lugarId", 0L)
            // Misma visita (al volver a poner las zonas, Android repite la entrada)
            if (dentroDesde != 0L && ahora - dentroDesde < VISITA_MAXIMA) return
            prefs.edit().putLong("dentro:$lugarId", ahora).apply()
            if (ahora - prefs.getLong("avisado:$lugarId", 0L) < ENTRE_AVISOS) return

            val lugar = Cache.lugares(contexto).find { it.id == lugarId } ?: return
            val avisos = Cache.leerAvisos(contexto).filter { it.lugarId == lugarId }
            if (avisos.isEmpty()) return
            if (notificar(contexto, lugar, avisos)) {
                prefs.edit().putLong("avisado:$lugarId", ahora).apply()
            }
        }

        fun salida(contexto: Context, lugarId: String) {
            contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("dentro:$lugarId").apply()
        }

        /** «📍 En Súper» con los avisos de ese lugar. false si no se pudo enseñar. */
        private fun notificar(contexto: Context, lugar: Lugar, avisos: List<Aviso>): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                contexto.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            val manager = contexto.getSystemService(NotificationManager::class.java) ?: return false
            manager.createNotificationChannel(
                NotificationChannel(CANAL, "Avisos por lugar", NotificationManager.IMPORTANCE_HIGH)
            )
            val abrir = PendingIntent.getActivity(
                contexto,
                lugar.id.hashCode(),
                Intent(contexto, AbrirActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val lineas = Notification.InboxStyle()
            avisos.take(MAXIMO_LINEAS).forEach { lineas.addLine(it.texto) }
            if (avisos.size > MAXIMO_LINEAS) lineas.setSummaryText("y ${avisos.size - MAXIMO_LINEAS} más")
            val notificacion = Notification.Builder(contexto, CANAL)
                .setSmallIcon(R.drawable.ic_lugar)
                .setContentTitle("📍 En ${lugar.nombre}")
                .setContentText(avisos.joinToString(" · ") { it.texto })
                .setStyle(lineas)
                .setContentIntent(abrir)
                .setAutoCancel(true)
                .build()
            manager.notify(lugar.id.hashCode(), notificacion)
            return true
        }
    }
}
