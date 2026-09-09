package io.github.pvicen.avisos

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** Cómo salió una actualización: sin error, con error pasajero o con sesión perdida. */
data class Resultado(val error: String?, val cantidad: Int, val pasajero: Boolean)

/**
 * Punto único de actualización: lo usan tanto el trabajo en segundo plano como
 * el botón de la app, para que los dos hagan exactamente lo mismo.
 */
object Actualizar {

    fun ahora(contexto: Context): Resultado {
        val app = contexto.applicationContext

        if (!Sesion.hay(app)) {
            Cache.guardarError(app, "toca para entrar")
            AvisosWidget.refrescar(app)
            return Resultado("No hay sesión iniciada.", 0, false)
        }

        var error: String? = null
        var pasajero = false
        var cantidad = 0
        try {
            val avisos = Api.avisosPendientes(app)
            Cache.guardarAvisos(app, avisos)
            cantidad = avisos.size
        } catch (e: ErrorSesion) {
            Sesion.limpiar(app)
            Cache.guardarError(app, "toca para entrar")
            error = "La sesión caducó. Vuelve a iniciar sesión."
        } catch (e: Exception) {
            // Fallo pasajero (sin señal, servidor ocupado): se reintenta solo.
            Cache.guardarError(app, "sin conexión")
            error = e.message ?: "No se pudo actualizar."
            pasajero = true
        }

        AvisosWidget.refrescar(app)
        return Resultado(error, cantidad, pasajero)
    }

    /** ¿Hay al menos un widget puesto en la pantalla de inicio? */
    fun hayWidgets(contexto: Context): Boolean {
        val manager = AppWidgetManager.getInstance(contexto)
        return manager
            .getAppWidgetIds(ComponentName(contexto, AvisosWidget::class.java))
            .isNotEmpty()
    }
}
