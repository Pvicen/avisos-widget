package io.github.pvicen.avisos

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.UUID

/**
 * Se abre al compartir un sitio desde Google Maps (o un enlace de mapa desde otra app, como Mundi):
 * «Compartir» → «Avisos: guardar lugar». Saca el nombre y el punto, deja cambiar el nombre y, si se
 * escribe qué hacer allí, crea también ese aviso con el lugar puesto.
 */
class CompartirLugarActivity : Activity() {

    private lateinit var punto: TextView
    private lateinit var nombre: EditText
    private lateinit var aviso: EditText
    private lateinit var mensaje: TextView
    private lateinit var guardar: Button
    private lateinit var cancelar: Button

    private var coordenadas: Pair<Double, Double>? = null
    private var enVuelo = false
    /** Generado aquí: si se reintenta, el aviso no se repite (como en NuevoAvisoActivity). */
    private val idAviso: String by lazy { UUID.randomUUID().toString() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Sesion.hay(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_compartir_lugar)
        punto = findViewById(R.id.punto_compartido)
        nombre = findViewById(R.id.nombre_compartido)
        aviso = findViewById(R.id.aviso_compartido)
        mensaje = findViewById(R.id.mensaje_compartido)
        guardar = findViewById(R.id.guardar_compartido)
        cancelar = findViewById(R.id.cancelar_compartido)

        val texto = listOfNotNull(
            intent?.getStringExtra(Intent.EXTRA_TEXT),
            intent?.dataString
        ).joinToString("\n")
        val asunto = intent?.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        val lineas = EnlaceMaps.lineas(texto)
        nombre.setText((asunto.ifEmpty { lineas.firstOrNull().orEmpty() }).take(40))

        cancelar.setOnClickListener { finish() }
        guardar.setOnClickListener { guardarLugar() }
        buscarPunto(texto, lineas)
    }

    /** Del texto, del enlace (siguiéndolo) o, en último caso, del nombre y la dirección. */
    private fun buscarPunto(texto: String, lineas: List<String>) {
        Thread {
            val encontrado = EnlaceMaps.coordenadas(texto)
                ?: EnlaceMaps.enlace(texto)?.let { EnlaceMaps.resolver(it) }
                ?: porDireccion(lineas)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                coordenadas = encontrado
                if (encontrado == null) {
                    punto.text = "No encontré el punto en lo compartido. Prueba a compartirlo otra vez desde Google Maps."
                } else {
                    punto.text = String.format(Locale.ROOT, "📍 %.5f, %.5f", encontrado.first, encontrado.second)
                    guardar.isEnabled = true
                }
            }
        }.start()
    }

    @Suppress("DEPRECATION") // la versión síncrona basta: ya estamos fuera del hilo principal
    private fun porDireccion(lineas: List<String>): Pair<Double, Double>? {
        if (lineas.isEmpty() || !Geocoder.isPresent()) return null
        return try {
            Geocoder(this, Locale("es", "ES")).getFromLocationName(lineas.joinToString(", "), 1)
                ?.firstOrNull()?.let { it.latitude to it.longitude }
        } catch (e: Exception) {
            null
        }
    }

    private fun guardarLugar() {
        val (lat, lon) = coordenadas ?: return
        val texto = nombre.text.toString().trim()
        if (texto.isEmpty()) {
            mensaje.text = "Ponle un nombre al sitio."
            return
        }
        enSegundoPlano(
            trabajo = { Api.guardarLugar(this, texto, lat, lon) },
            siRepetido = { ofrecerExistente(texto, lat, lon) }
        )
    }

    /** El nombre ya existe: usar ese lugar tal cual o moverlo a este punto. */
    private fun ofrecerExistente(texto: String, lat: Double, lon: Double) {
        Thread {
            val clave = texto.trim().lowercase()
            val existente = try {
                Api.lugares(this).find { it.nombre.trim().lowercase() == clave }
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (existente == null) {
                    mensaje.text = "Ya hay un lugar llamado «$texto». Ponle otro nombre."
                    return@runOnUiThread
                }
                AlertDialog.Builder(this)
                    .setMessage("Ya tienes «${existente.nombre}». ¿Lo usas tal cual o lo mueves a este punto?")
                    .setPositiveButton("Moverlo aquí") { _, _ ->
                        enSegundoPlano(trabajo = { Api.moverLugar(this, existente.id, lat, lon); existente.id })
                    }
                    .setNeutralButton("Usar el que hay") { _, _ ->
                        enSegundoPlano(trabajo = { existente.id })
                    }
                    .setNegativeButton(R.string.cancelar, null)
                    .show()
            }
        }.start()
    }

    /**
     * `trabajo` deja el lugar listo y devuelve su id; después se crea el aviso (si se escribió) y se
     * actualiza todo (lista, widget y zonas vigiladas).
     */
    private fun enSegundoPlano(trabajo: () -> String, siRepetido: () -> Unit = {}) {
        val queHacer = aviso.text.toString().trim()
        enVuelo = true
        setFinishOnTouchOutside(false)
        guardar.isEnabled = false
        cancelar.isEnabled = false
        mensaje.text = getString(R.string.guardando)
        Thread {
            var error: String? = null
            var repetido = false
            try {
                val lugarId = trabajo()
                if (queHacer.isNotEmpty()) Api.agregar(this, idAviso, queHacer, lugarId)
                Actualizar.ahora(this)
            } catch (e: LugarRepetido) {
                repetido = true
            } catch (e: Exception) {
                error = e.message ?: "No se pudo guardar."
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                enVuelo = false
                setFinishOnTouchOutside(true)
                cancelar.isEnabled = true
                guardar.isEnabled = true
                when {
                    repetido -> {
                        mensaje.text = ""
                        siRepetido()
                    }
                    error != null -> mensaje.text = error
                    else -> {
                        val hecho = if (queHacer.isEmpty()) "Lugar guardado ✓" else "Lugar y aviso guardados ✓"
                        Toast.makeText(this, hecho, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }.start()
    }

    @Deprecated("Se mantiene para bloquear el gesto de atrás mientras se guarda")
    override fun onBackPressed() {
        if (!enVuelo) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
