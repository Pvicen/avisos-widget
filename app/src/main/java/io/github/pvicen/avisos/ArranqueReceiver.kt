package io.github.pvicen.avisos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Android borra las zonas vigiladas al reiniciar (y puede hacerlo al actualizar la app): aquí se
 * vuelven a poner, con los datos guardados, y se pide una actualización.
 */
class ArranqueReceiver : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendiente = goAsync()
        Geovallas.sincronizar(contexto, forzar = true) { pendiente.finish() }
        if (Sesion.hay(contexto)) ActualizarWorker.encolar(contexto)
    }
}
