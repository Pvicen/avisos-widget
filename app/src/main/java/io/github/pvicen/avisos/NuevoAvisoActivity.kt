package io.github.pvicen.avisos

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

/** Ventanita para escribir un aviso nuevo sin salir de la pantalla de inicio. */
class NuevoAvisoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Sesion.hay(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_nuevo)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        val texto = findViewById<EditText>(R.id.texto_nuevo)
        val mensaje = findViewById<TextView>(R.id.mensaje_nuevo)
        val agregar = findViewById<Button>(R.id.boton_agregar)

        findViewById<Button>(R.id.boton_cancelar).setOnClickListener { finish() }

        agregar.setOnClickListener {
            val contenido = texto.text.toString().trim()
            if (contenido.isEmpty()) {
                mensaje.text = getString(R.string.escribe_algo)
                return@setOnClickListener
            }
            agregar.isEnabled = false
            mensaje.text = getString(R.string.guardando)

            Thread {
                var error: String? = null
                try {
                    Api.agregar(this@NuevoAvisoActivity, contenido)
                    Actualizar.ahora(this@NuevoAvisoActivity)
                } catch (e: Exception) {
                    error = e.message ?: getString(R.string.error_agregar)
                }
                runOnUiThread {
                    if (error != null) {
                        agregar.isEnabled = true
                        mensaje.text = error
                    } else {
                        Toast.makeText(
                            this@NuevoAvisoActivity,
                            R.string.aviso_agregado,
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }
            }.start()
        }
    }
}
