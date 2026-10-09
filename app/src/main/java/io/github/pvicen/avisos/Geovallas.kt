package io.github.pvicen.avisos

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * Avisos por lugar: las zonas que vigila Android. Solo se vigilan los lugares que tienen algún
 * aviso pendiente, con un círculo de su radio (150 m por defecto). Android avisa a LugarReceiver
 * al entrar y al salir; la posición del teléfono nunca sale de él.
 *
 * Las zonas se pierden al reiniciar el teléfono o al apagar la ubicación: por eso se vuelven a
 * poner al arrancar (ArranqueReceiver), cuando cambia la lista y, como mínimo, cada 6 horas.
 */
object Geovallas {
    private const val TAG = "Geovallas"
    private const val PREFS = "geovallas"
    private const val FIRMA = "firma"
    private const val PUESTAS_EN = "puestas_en"
    private const val RENOVAR = 6 * 60 * 60 * 1000L
    /** Límite de Android por app. */
    private const val MAXIMO = 100

    /** ¿Puede vigilar? Ubicación precisa y, desde Android 10, «Permitir todo el tiempo». */
    fun hayPermisos(contexto: Context): Boolean {
        val precisa = contexto.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val siempre = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            contexto.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return precisa && siempre
    }

    /** Los lugares con algún aviso pendiente: solo esos se vigilan. */
    fun activos(contexto: Context): List<Lugar> {
        val conAvisos = Cache.leerAvisos(contexto).mapNotNull { it.lugarId }.toSet()
        return Cache.lugares(contexto).filter { it.id in conAvisos }.take(MAXIMO)
    }

    /**
     * Pone las zonas que tocan (o las quita todas si no hay sesión o permisos). Sin `forzar`, no
     * hace nada si ya están puestas las mismas hace menos de 6 horas. `alTerminar` se llama
     * siempre, también si falla (para los receptores que esperan con goAsync).
     */
    @SuppressLint("MissingPermission") // se comprueba en hayPermisos()
    fun sincronizar(contexto: Context, forzar: Boolean = false, alTerminar: () -> Unit = {}) {
        val app = contexto.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val permisos = hayPermisos(app)
        val lugares = if (Sesion.hay(app) && permisos) activos(app) else emptyList()
        val firma = lugares.joinToString("|") { "${it.id},${it.lat},${it.lon},${it.radio}" }
        val reciente = System.currentTimeMillis() - prefs.getLong(PUESTAS_EN, 0L) < RENOVAR
        if (!forzar && reciente && prefs.getString(FIRMA, null) == firma) {
            alTerminar()
            return
        }

        val cliente = LocationServices.getGeofencingClient(app)
        val intencion = intencion(app)
        cliente.removeGeofences(intencion).addOnCompleteListener {
            if (lugares.isEmpty()) {
                prefs.edit().putString(FIRMA, firma).putLong(PUESTAS_EN, System.currentTimeMillis()).apply()
                alTerminar()
                return@addOnCompleteListener
            }
            val peticion = GeofencingRequest.Builder()
                // Si ya estás dentro al ponerla, avisa al momento (así se prueba en casa)
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(lugares.map { zona(it) })
                .build()
            try {
                cliente.addGeofences(peticion, intencion)
                    .addOnSuccessListener {
                        prefs.edit().putString(FIRMA, firma).putLong(PUESTAS_EN, System.currentTimeMillis()).apply()
                    }
                    .addOnFailureListener { e ->
                        // Ubicación apagada o sin permiso: se reintenta en la próxima actualización
                        Log.w(TAG, "No se pudieron poner las zonas: ${e.message}")
                        prefs.edit().remove(FIRMA).apply()
                    }
                    .addOnCompleteListener { alTerminar() }
            } catch (e: SecurityException) {
                prefs.edit().remove(FIRMA).apply()
                alTerminar()
            }
        }
    }

    private fun zona(lugar: Lugar): Geofence =
        Geofence.Builder()
            .setRequestId(lugar.id)
            .setCircularRegion(lugar.lat, lugar.lon, lugar.radio.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

    /** Explícito y mutable (Android le añade el evento): permitido también en Android 14+. */
    private fun intencion(contexto: Context): PendingIntent {
        var banderas = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) banderas = banderas or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(contexto, 0, Intent(contexto, LugarReceiver::class.java), banderas)
    }
}
