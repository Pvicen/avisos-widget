package io.github.pvicen.avisos

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * Puente invisible: recibe los toques del widget y decide qué hacer.
 *
 * Existe por dos razones. Los PendingIntent del widget tienen que apuntar a un
 * componente propio (Android 14+ prohíbe los intents implícitos en plantillas
 * mutables), y al decidir aquí se usa el estado del momento del toque, no el de
 * cuando se pintó el widget.
 */
class AbrirActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val accion = intent?.getStringExtra(AvisosWidget.EXTRA_ACCION)
        val id = intent?.getStringExtra(AvisosWidget.EXTRA_ID)

        if (accion == AvisosWidget.ACCION_COMPLETAR && !id.isNullOrBlank()) {
            completar(id)
        } else {
            abrirLaApp()
        }
        finish()
    }

    private fun completar(id: String) {
        if (!Sesion.hay(this)) {
            abrirLaApp()
            return
        }
        // Desaparece de la lista al instante; el servidor se entera enseguida.
        // Si ya estaba en camino (toque repetido) no se hace nada dos veces.
        if (!Cache.ocultar(this, id)) return
        AvisosWidget.refrescar(this)
        AccionWorker.completar(this, id)
        Toast.makeText(this, R.string.hecho, Toast.LENGTH_SHORT).show()
    }

    private fun abrirLaApp() {
        val destino = if (Sesion.hay(this)) {
            Intent(Intent.ACTION_VIEW, Uri.parse(Config.APP_URL))
        } else {
            Intent(this, LoginActivity::class.java)
        }
        destino.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(destino)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.sin_navegador, Toast.LENGTH_LONG).show()
        }
    }
}
