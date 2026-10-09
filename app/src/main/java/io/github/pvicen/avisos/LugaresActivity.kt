package io.github.pvicen.avisos

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.math.roundToInt

/**
 * Avisos por lugar: los permisos, «Guardar dónde estoy» y la lista de sitios guardados (con
 * cuántos avisos pendientes tiene cada uno). Los lugares se comparten con la app web.
 */
class LugaresActivity : Activity() {

    private lateinit var estadoPermisos: TextView
    private lateinit var darPermiso: Button
    private lateinit var nombre: EditText
    private lateinit var guardar: Button
    private lateinit var mensaje: TextView
    private lateinit var lista: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lugares)
        estadoPermisos = findViewById(R.id.estado_permisos)
        darPermiso = findViewById(R.id.dar_permiso)
        nombre = findViewById(R.id.nombre_lugar)
        guardar = findViewById(R.id.guardar_aqui)
        mensaje = findViewById(R.id.mensaje_lugares)
        lista = findViewById(R.id.lista_lugares)

        darPermiso.setOnClickListener { pedirPermisos() }
        guardar.setOnClickListener { guardarAqui() }
    }

    override fun onResume() {
        super.onResume()
        pintarPermisos()
        pintarLista(Cache.lugares(this))
        cargarLista()
    }

    // ---------- Permisos ----------

    private fun tiene(permiso: String) = checkSelfPermission(permiso) == PackageManager.PERMISSION_GRANTED

    private fun faltaUbicacion() = !tiene(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun faltaSiempre() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !tiene(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun faltanNotificaciones() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !tiene(Manifest.permission.POST_NOTIFICATIONS)

    private fun pintarPermisos() {
        val falta = when {
            faltaUbicacion() -> getString(R.string.falta_ubicacion)
            faltaSiempre() -> getString(R.string.falta_siempre)
            faltanNotificaciones() -> getString(R.string.falta_notificaciones)
            else -> null
        }
        estadoPermisos.text = falta ?: getString(R.string.permisos_ok)
        darPermiso.visibility = if (falta == null) View.GONE else View.VISIBLE
    }

    /** Uno cada vez: Android exige pedir «todo el tiempo» después de la ubicación normal. */
    private fun pedirPermisos() {
        when {
            faltaUbicacion() -> requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                PEDIR_UBICACION
            )
            faltaSiempre() -> requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), PEDIR_SIEMPRE)
            faltanNotificaciones() -> requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), PEDIR_NOTIFICACIONES)
        }
    }

    override fun onRequestPermissionsResult(codigo: Int, permisos: Array<out String>, resultados: IntArray) {
        super.onRequestPermissionsResult(codigo, permisos, resultados)
        pintarPermisos()
        // Con todo concedido, empieza a vigilar enseguida
        if (Geovallas.hayPermisos(this)) Geovallas.sincronizar(this, forzar = true)
    }

    // ---------- Guardar dónde estoy ----------

    @SuppressLint("MissingPermission") // se comprueba con faltaUbicacion()
    private fun guardarAqui() {
        val texto = nombre.text.toString().trim()
        if (texto.isEmpty()) {
            mensaje.text = "Escribe el nombre del sitio."
            return
        }
        if (faltaUbicacion()) {
            mensaje.text = getString(R.string.falta_ubicacion)
            return
        }
        guardar.isEnabled = false
        mensaje.text = "Buscando dónde estás…"
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { posicion ->
                if (posicion == null) {
                    guardar.isEnabled = true
                    mensaje.text = "No se pudo saber dónde estás. Comprueba que la ubicación está encendida."
                    return@addOnSuccessListener
                }
                val precision = posicion.accuracy.roundToInt()
                enSegundoPlano(
                    trabajo = { Api.guardarLugar(this, texto, posicion.latitude, posicion.longitude) },
                    alAcabar = {
                        nombre.setText("")
                        mensaje.text = "Guardado «$texto» (precisión ±$precision m)."
                    },
                    siRepetido = { ofrecerMover(texto, posicion.latitude, posicion.longitude) }
                )
            }
            .addOnFailureListener { e ->
                guardar.isEnabled = true
                mensaje.text = "No se pudo saber dónde estás: ${e.message}"
            }
    }

    /** El nombre ya existe: ¿mover ese lugar a donde estás? */
    private fun ofrecerMover(texto: String, lat: Double, lon: Double) {
        val clave = texto.trim().lowercase()
        val existente = Cache.lugares(this).find { it.nombre.trim().lowercase() == clave }
        if (existente == null) {
            mensaje.text = "Ya hay un lugar llamado «$texto». Actualiza y vuelve a probar."
            return
        }
        AlertDialog.Builder(this)
            .setMessage("Ya hay un lugar «${existente.nombre}». ¿Lo muevo a donde estás ahora?")
            .setPositiveButton("Moverlo") { _, _ ->
                enSegundoPlano(
                    trabajo = { Api.moverLugar(this, existente.id, lat, lon) },
                    alAcabar = {
                        nombre.setText("")
                        mensaje.text = "«${existente.nombre}» está ahora donde estás."
                    }
                )
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    // ---------- La lista ----------

    private fun cargarLista() {
        Thread {
            val lugares = try {
                Api.lugares(this).also { Cache.guardarLugares(this, it) }
            } catch (e: Exception) {
                null
            }
            if (lugares != null) runOnUiThread { pintarLista(lugares) }
        }.start()
    }

    private fun pintarLista(lugares: List<Lugar>) {
        lista.removeAllViews()
        if (lugares.isEmpty()) {
            lista.addView(TextView(this).apply { text = getString(R.string.sin_lugares) })
            return
        }
        val avisos = Cache.leerAvisos(this)
        for (lugar in lugares) {
            val cuantos = avisos.count { it.lugarId == lugar.id }
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val etiqueta = TextView(this).apply {
                text = "📍 ${lugar.nombre}" + when (cuantos) {
                    0 -> ""
                    1 -> " · 1 aviso"
                    else -> " · $cuantos avisos"
                }
                textSize = 15f
            }
            fila.addView(etiqueta, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val borrar = Button(this).apply {
                text = getString(R.string.borrar)
                setOnClickListener { confirmarBorrar(lugar) }
            }
            fila.addView(borrar)
            lista.addView(fila)
        }
    }

    private fun confirmarBorrar(lugar: Lugar) {
        AlertDialog.Builder(this)
            .setMessage("¿Borrar «${lugar.nombre}»? Sus avisos se quedan, sin lugar.")
            .setPositiveButton(R.string.borrar) { _, _ ->
                enSegundoPlano(
                    trabajo = { Api.borrarLugar(this, lugar.id) },
                    alAcabar = { mensaje.text = "«${lugar.nombre}» borrado." }
                )
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    /**
     * Hace `trabajo` con red y, si va bien, actualiza todo (lista, widget y zonas vigiladas)
     * antes de `alAcabar`. Un nombre repetido va a `siRepetido`; cualquier otro error, al mensaje.
     */
    private fun enSegundoPlano(trabajo: () -> Unit, alAcabar: () -> Unit, siRepetido: () -> Unit = {}) {
        guardar.isEnabled = false
        Thread {
            var error: String? = null
            var repetido = false
            try {
                trabajo()
                Actualizar.ahora(this)
            } catch (e: LugarRepetido) {
                repetido = true
            } catch (e: Exception) {
                error = e.message ?: "No se pudo guardar."
            }
            runOnUiThread {
                guardar.isEnabled = true
                when {
                    repetido -> siRepetido()
                    error != null -> mensaje.text = error
                    else -> {
                        alAcabar()
                        pintarLista(Cache.lugares(this))
                    }
                }
            }
        }.start()
    }

    private companion object {
        const val PEDIR_UBICACION = 1
        const val PEDIR_SIEMPRE = 2
        const val PEDIR_NOTIFICACIONES = 3
    }
}
