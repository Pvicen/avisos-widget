package io.github.pvicen.avisos

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Guarda la sesión de Supabase (tokens) y la copia local de los avisos.
 * Usa almacenamiento cifrado; si el llavero del dispositivo falla, primero
 * intenta rehacerlo y solo como último recurso cae a preferencias sin cifrar,
 * dejando constancia para poder avisarlo en pantalla.
 */
object Sesion {
    private const val ETIQUETA = "AvisosSesion"
    private const val ARCHIVO_CIFRADO = "avisos_sesion"
    private const val ARCHIVO_SIMPLE = "avisos_sesion_simple"

    @Volatile
    private var guardadas: SharedPreferences? = null

    @Volatile
    private var estaCifrado = true

    /** Sube cada vez que se cierra sesión: invalida escrituras de tokens en vuelo. */
    @Volatile
    private var generacionActual = 0

    fun generacion(): Int = generacionActual

    private fun crearCifradas(app: Context): SharedPreferences {
        val llave = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            app,
            ARCHIVO_CIFRADO,
            llave,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun prefs(contexto: Context): SharedPreferences {
        guardadas?.let { return it }
        synchronized(this) {
            guardadas?.let { return it }
            val app = contexto.applicationContext
            val nuevas = try {
                crearCifradas(app)
            } catch (e: Exception) {
                // Caso típico: el keyset quedó corrupto tras una actualización del
                // sistema o una migración de teléfono. Se rehace desde cero; el
                // precio es volver a iniciar sesión una vez.
                Log.w(ETIQUETA, "No se pudo abrir el almacén cifrado, se rehace", e)
                try {
                    app.deleteSharedPreferences(ARCHIVO_CIFRADO)
                    crearCifradas(app)
                } catch (e2: Exception) {
                    Log.w(ETIQUETA, "Sin almacén cifrado disponible", e2)
                    estaCifrado = false
                    app.getSharedPreferences(ARCHIVO_SIMPLE, Context.MODE_PRIVATE)
                }
            }
            guardadas = nuevas
            return nuevas
        }
    }

    /** false si los datos quedaron guardados sin cifrar (llavero no disponible). */
    fun cifrado(contexto: Context): Boolean {
        prefs(contexto)
        return estaCifrado
    }

    /**
     * Guarda los tokens salvo que se haya cerrado sesión mientras se pedían.
     * Devuelve false en ese caso, para no resucitar una sesión ya cerrada.
     */
    @Synchronized
    fun guardarTokens(
        contexto: Context,
        acceso: String,
        refresco: String,
        duracionSegundos: Long,
        correo: String?,
        generacionEsperada: Int
    ): Boolean {
        if (generacionEsperada != generacionActual) return false
        val editor = prefs(contexto).edit()
        editor.putString("acceso", acceso)
        editor.putString("refresco", refresco)
        editor.putLong("expira", System.currentTimeMillis() + duracionSegundos * 1000L)
        if (!correo.isNullOrBlank()) editor.putString("correo", correo)
        editor.apply()
        return true
    }

    fun acceso(contexto: Context): String? = prefs(contexto).getString("acceso", null)

    fun refresco(contexto: Context): String? = prefs(contexto).getString("refresco", null)

    fun expira(contexto: Context): Long = prefs(contexto).getLong("expira", 0L)

    fun correo(contexto: Context): String? = prefs(contexto).getString("correo", null)

    fun hay(contexto: Context): Boolean = !refresco(contexto).isNullOrBlank()

    @Synchronized
    fun limpiar(contexto: Context) {
        generacionActual++
        prefs(contexto).edit().clear().apply()
    }
}
