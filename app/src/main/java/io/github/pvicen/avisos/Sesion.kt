package io.github.pvicen.avisos

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Guarda la sesión de Supabase (tokens) y la copia local de los avisos.
 * Usa almacenamiento cifrado; si el llavero del dispositivo falla, cae a las
 * preferencias privadas de la app antes que dejar el widget inservible.
 */
object Sesion {
    private const val ARCHIVO_CIFRADO = "avisos_sesion"
    private const val ARCHIVO_SIMPLE = "avisos_sesion_simple"

    @Volatile
    private var guardadas: SharedPreferences? = null

    fun prefs(contexto: Context): SharedPreferences {
        guardadas?.let { return it }
        synchronized(this) {
            guardadas?.let { return it }
            val app = contexto.applicationContext
            val nuevas = try {
                val llave = MasterKey.Builder(app)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    app,
                    ARCHIVO_CIFRADO,
                    llave,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                app.getSharedPreferences(ARCHIVO_SIMPLE, Context.MODE_PRIVATE)
            }
            guardadas = nuevas
            return nuevas
        }
    }

    fun guardarTokens(
        contexto: Context,
        acceso: String,
        refresco: String,
        duracionSegundos: Long,
        correo: String?
    ) {
        val editor = prefs(contexto).edit()
        editor.putString("acceso", acceso)
        editor.putString("refresco", refresco)
        editor.putLong("expira", System.currentTimeMillis() + duracionSegundos * 1000L)
        if (!correo.isNullOrBlank()) editor.putString("correo", correo)
        editor.apply()
    }

    fun acceso(contexto: Context): String? = prefs(contexto).getString("acceso", null)

    fun refresco(contexto: Context): String? = prefs(contexto).getString("refresco", null)

    fun expira(contexto: Context): Long = prefs(contexto).getLong("expira", 0L)

    fun correo(contexto: Context): String? = prefs(contexto).getString("correo", null)

    fun hay(contexto: Context): Boolean = !refresco(contexto).isNullOrBlank()

    fun limpiar(contexto: Context) {
        prefs(contexto).edit().clear().apply()
    }
}
