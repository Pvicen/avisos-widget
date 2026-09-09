package io.github.pvicen.avisos

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/**
 * Única pantalla de la app: inicia la sesión que usa el widget y permite
 * refrescarlo o cerrar sesión. La app en sí se sigue usando desde el navegador.
 */
class LoginActivity : Activity() {

    private lateinit var formulario: View
    private lateinit var conSesion: View
    private lateinit var correo: EditText
    private lateinit var clave: EditText
    private lateinit var entrar: Button
    private lateinit var mensaje: TextView
    private lateinit var quienSoy: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        formulario = findViewById(R.id.formulario)
        conSesion = findViewById(R.id.con_sesion)
        correo = findViewById(R.id.correo)
        clave = findViewById(R.id.clave)
        entrar = findViewById(R.id.entrar)
        mensaje = findViewById(R.id.mensaje)
        quienSoy = findViewById(R.id.quien_soy)

        entrar.setOnClickListener { iniciarSesion() }

        findViewById<Button>(R.id.actualizar).setOnClickListener {
            ActualizarWorker.encolar(this)
            mensaje.text = getString(R.string.actualizando)
        }

        findViewById<Button>(R.id.salir).setOnClickListener {
            Sesion.limpiar(this)
            AvisosWidget.refrescar(this)
            mensaje.text = getString(R.string.sesion_cerrada)
            pintarEstado()
        }
    }

    override fun onResume() {
        super.onResume()
        pintarEstado()
    }

    private fun pintarEstado() {
        val hay = Sesion.hay(this)
        formulario.visibility = if (hay) View.GONE else View.VISIBLE
        conSesion.visibility = if (hay) View.VISIBLE else View.GONE
        val cuenta = Sesion.correo(this)
        quienSoy.text = if (cuenta.isNullOrBlank()) {
            getString(R.string.sesion_iniciada)
        } else {
            getString(R.string.sesion_iniciada_como, cuenta)
        }
    }

    private fun iniciarSesion() {
        val cuenta = correo.text.toString().trim()
        val contrasena = clave.text.toString()
        if (cuenta.isEmpty() || contrasena.isEmpty()) {
            mensaje.text = getString(R.string.faltan_datos)
            return
        }

        entrar.isEnabled = false
        mensaje.text = getString(R.string.entrando)

        Thread {
            var error: String? = null
            try {
                Api.iniciarSesion(this@LoginActivity, cuenta, contrasena)
            } catch (e: Exception) {
                error = e.message ?: getString(R.string.error_generico)
            }
            runOnUiThread {
                entrar.isEnabled = true
                if (error != null) {
                    mensaje.text = error
                } else {
                    clave.setText("")
                    mensaje.text = getString(R.string.listo)
                    ActualizarWorker.encolar(this@LoginActivity)
                    pintarEstado()
                }
            }
        }.start()
    }
}
