package io.github.pvicen.avisos

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * Puente invisible: es el destino de los toques en el widget.
 *
 * Existe por dos razones. Los PendingIntent del widget tienen que apuntar a un
 * componente propio (Android 14+ prohíbe los intents implícitos en plantillas
 * mutables), y al decidir aquí el destino se usa el estado de sesión del momento
 * del toque, no el que había cuando se pintó el widget.
 */
class AbrirActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        finish()
    }
}
