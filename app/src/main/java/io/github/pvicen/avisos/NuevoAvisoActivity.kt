package io.github.pvicen.avisos

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

/** Ventanita para escribir un aviso nuevo sin salir de la pantalla de inicio. */
class NuevoAvisoActivity : Activity() {

    private var enVuelo = false

    /**
     * El id se genera aquí y no en el servidor: si la petición se reintenta
     * (por ejemplo tras un corte a mitad de camino), el segundo intento choca
     * con el mismo id en vez de crear un aviso repetido.
     */
    private val idNuevo: String by lazy { UUID.randomUUID().toString() }

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
        val cancelar = findViewById<Button>(R.id.boton_cancelar)

        cancelar.setOnClickListener { finish() }

        agregar.setOnClickListener {
            val contenido = texto.text.toString().trim()
            if (contenido.isEmpty()) {
                mensaje.text = getString(R.string.escribe_algo)
                return@setOnClickListener
            }

            // Mientras se guarda, la ventana no se cierra por accidente: ni con
            // atrás, ni tocando fuera, ni con Cancelar.
            enVuelo = true
            setFinishOnTouchOutside(false)
            agregar.isEnabled = false
            cancelar.isEnabled = false
            mensaje.text = getString(R.string.guardando)

            Thread {
                var error: String? = null
                try {
                    Api.agregar(this@NuevoAvisoActivity, idNuevo, contenido)
                    Actualizar.ahora(this@NuevoAvisoActivity)
                } catch (e: Exception) {
                    error = e.message ?: getString(R.string.error_agregar)
                }
                val fallo = error
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    enVuelo = false
                    setFinishOnTouchOutside(true)
                    cancelar.isEnabled = true
                    if (fallo != null) {
                        agregar.isEnabled = true
                        mensaje.text = fallo
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

    @Deprecated("Se mantiene para bloquear el gesto de atrás mientras se guarda")
    override fun onBackPressed() {
        if (!enVuelo) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
