package io.github.pvicen.avisos

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** La sesión no sirve: hay que volver a iniciar sesión. */
class ErrorSesion(mensaje: String) : Exception(mensaje)

/** Problema de red o del servidor: se reintenta más tarde. */
class ErrorRed(mensaje: String) : Exception(mensaje)

data class Aviso(
    val id: String,
    val texto: String,
    val nota: String?,
    val prioridad: Boolean,
    val vence: String?
)

object Api {
    private const val MARGEN_RENOVACION = 5 * 60 * 1000L
    private val TIPO_JSON = "application/json; charset=utf-8".toMediaType()

    private val cliente: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun pedir(
        url: String,
        metodo: String,
        cuerpo: String? = null,
        token: String? = null,
        prefer: String? = null
    ): Pair<Int, String> {
        val constructor = Request.Builder()
            .url(url)
            .method(metodo, cuerpo?.toRequestBody(TIPO_JSON))
            .header("apikey", Config.ANON_KEY)
            .header("Accept", "application/json")
        if (token != null) constructor.header("Authorization", "Bearer $token")
        if (prefer != null) constructor.header("Prefer", prefer)

        try {
            cliente.newCall(constructor.build()).execute().use { respuesta ->
                return respuesta.code to (respuesta.body?.string() ?: "")
            }
        } catch (e: IOException) {
            throw ErrorRed("Sin conexión con el servidor")
        }
    }

    // ---------- Sesión ----------

    fun iniciarSesion(contexto: Context, correo: String, clave: String) {
        val generacion = Sesion.generacion()
        val (codigo, respuesta) = pedir(
            "${Config.SUPABASE_URL}/auth/v1/token?grant_type=password",
            "POST",
            JSONObject().put("email", correo).put("password", clave).toString()
        )
        if (codigo == 400) throw ErrorSesion("Correo o contraseña incorrectos.")
        if (codigo == 429) throw ErrorRed("Demasiados intentos. Espera un momento.")
        if (codigo !in 200..299) throw ErrorRed("El servidor respondió $codigo")
        guardarRespuesta(contexto, respuesta, generacion)
    }

    /** Cierra la sesión también en el servidor. Si falla, no pasa nada grave. */
    fun cerrarSesionEnServidor(contexto: Context) {
        val acceso = Sesion.acceso(contexto) ?: return
        try {
            pedir(
                "${Config.SUPABASE_URL}/auth/v1/logout?scope=local",
                "POST",
                "{}",
                acceso
            )
        } catch (e: Exception) {
            // Sin red o token ya vencido: la sesión local se borra igual.
        }
    }

    private fun guardarRespuesta(contexto: Context, respuesta: String, generacion: Int) {
        val datos = try {
            JSONObject(respuesta)
        } catch (e: Exception) {
            throw ErrorRed("Respuesta inesperada del servidor")
        }
        val acceso = datos.optString("access_token")
        val refresco = datos.optString("refresh_token")
        if (acceso.isBlank() || refresco.isBlank()) throw ErrorSesion("El servidor no entregó una sesión")
        val correo = datos.optJSONObject("user")?.optString("email")
        val guardado = Sesion.guardarTokens(
            contexto, acceso, refresco, datos.optLong("expires_in", 3600L), correo, generacion
        )
        if (!guardado) throw ErrorSesion("La sesión se cerró mientras se renovaba")
    }

    /** Distingue "el refresco ya no sirve" de "el servidor tuvo un problema". */
    private fun refrescoInvalido(codigo: Int, respuesta: String): Boolean {
        if (codigo != 400 && codigo != 401) return false
        val texto = respuesta.lowercase()
        return texto.contains("invalid_grant") ||
            texto.contains("refresh_token_not_found") ||
            texto.contains("refresh_token_already_used") ||
            texto.contains("invalid refresh token")
    }

    /** Devuelve un token de acceso vigente, renovándolo si hace falta. */
    @Synchronized
    fun tokenValido(contexto: Context): String {
        val refresco = Sesion.refresco(contexto)
        if (refresco.isNullOrBlank()) throw ErrorSesion("No hay sesión iniciada")

        val acceso = Sesion.acceso(contexto)
        if (!acceso.isNullOrBlank() && System.currentTimeMillis() < Sesion.expira(contexto) - MARGEN_RENOVACION) {
            return acceso
        }

        val generacion = Sesion.generacion()
        val (codigo, respuesta) = pedir(
            "${Config.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token",
            "POST",
            JSONObject().put("refresh_token", refresco).toString()
        )
        // Solo se da la sesión por perdida si el servidor dice que el refresco no
        // sirve; un 429 o un 5xx son problemas pasajeros y se reintentan.
        if (refrescoInvalido(codigo, respuesta)) {
            throw ErrorSesion("La sesión caducó. Vuelve a iniciar sesión.")
        }
        if (codigo !in 200..299) throw ErrorRed("El servidor respondió $codigo")
        guardarRespuesta(contexto, respuesta, generacion)
        return Sesion.acceso(contexto) ?: throw ErrorSesion("No se pudo renovar la sesión")
    }

    // ---------- Avisos ----------

    private fun revisarRespuesta(codigo: Int) {
        if (codigo == 401 || codigo == 403) throw ErrorSesion("La sesión ya no es válida")
        if (codigo !in 200..299) throw ErrorRed("El servidor respondió $codigo")
    }

    fun avisosPendientes(contexto: Context): List<Aviso> {
        val token = tokenValido(contexto)
        val url = Config.SUPABASE_URL + "/rest/v1/avisos" +
            "?select=id,texto,nota,prioridad,vence" +
            "&completado_en=is.null" +
            "&order=prioridad.desc,vence.asc.nullslast,creado_en.asc" +
            "&limit=50"
        val (codigo, respuesta) = pedir(url, "GET", token = token)
        revisarRespuesta(codigo)

        val arreglo = try {
            JSONArray(respuesta)
        } catch (e: Exception) {
            throw ErrorRed("Respuesta inesperada del servidor")
        }
        val avisos = ArrayList<Aviso>(arreglo.length())
        for (i in 0 until arreglo.length()) {
            val fila = arreglo.optJSONObject(i) ?: continue
            avisos.add(
                Aviso(
                    id = fila.optString("id"),
                    texto = fila.optString("texto"),
                    nota = textoOpcional(fila, "nota"),
                    prioridad = fila.optBoolean("prioridad", false),
                    vence = textoOpcional(fila, "vence")
                )
            )
        }
        return avisos
    }

    /** Marca un aviso como hecho (pasa al historial de la app). */
    fun completar(contexto: Context, id: String) {
        val token = tokenValido(contexto)
        val ahora = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val (codigo, respuesta) = pedir(
            "${Config.SUPABASE_URL}/rest/v1/avisos?id=eq.$id",
            "PATCH",
            JSONObject().put("completado_en", ahora).toString(),
            token,
            "return=representation"
        )
        revisarRespuesta(codigo)
        // 0 filas = alguien ya lo completó o lo borró desde otro dispositivo:
        // no es un error, el próximo refresco deja todo al día.
    }

    /**
     * Crea un aviso nuevo. El id viaja desde el cliente para que un reintento
     * no cree dos avisos iguales: el segundo choca con el mismo id.
     */
    fun agregar(contexto: Context, id: String, texto: String) {
        val token = tokenValido(contexto)
        val (codigo, respuesta) = pedir(
            "${Config.SUPABASE_URL}/rest/v1/avisos",
            "POST",
            JSONObject().put("id", id).put("texto", texto).toString(),
            token,
            "return=representation"
        )
        // 409 = ese id ya existe: el aviso se guardó en un intento anterior.
        if (codigo == 409) return
        revisarRespuesta(codigo)
        val creados = try {
            JSONArray(respuesta).length()
        } catch (e: Exception) {
            0
        }
        if (creados == 0) throw ErrorRed("El aviso no se guardó. Inténtalo de nuevo.")
    }

    private fun textoOpcional(fila: JSONObject, campo: String): String? {
        if (fila.isNull(campo)) return null
        val valor = fila.optString(campo)
        return if (valor.isBlank()) null else valor
    }
}
