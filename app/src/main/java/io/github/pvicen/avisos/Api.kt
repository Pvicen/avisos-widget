package io.github.pvicen.avisos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

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
    private const val ESPERA = 15000
    private const val MARGEN_RENOVACION = 5 * 60 * 1000L

    private fun abrir(url: String, metodo: String): HttpURLConnection {
        val conexion = URL(url).openConnection() as HttpURLConnection
        conexion.requestMethod = metodo
        conexion.connectTimeout = ESPERA
        conexion.readTimeout = ESPERA
        conexion.setRequestProperty("apikey", Config.ANON_KEY)
        conexion.setRequestProperty("Accept", "application/json")
        return conexion
    }

    private fun ejecutar(conexion: HttpURLConnection, cuerpoEnvio: String?): Pair<Int, String> {
        try {
            if (cuerpoEnvio != null) {
                conexion.doOutput = true
                conexion.setRequestProperty("Content-Type", "application/json")
                conexion.outputStream.use { it.write(cuerpoEnvio.toByteArray(Charsets.UTF_8)) }
            }
            val codigo = conexion.responseCode
            val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
            val texto = flujo?.bufferedReader()?.use { it.readText() } ?: ""
            return codigo to texto
        } catch (e: IOException) {
            throw ErrorRed("Sin conexión con el servidor")
        } finally {
            conexion.disconnect()
        }
    }

    fun iniciarSesion(contexto: Context, correo: String, clave: String) {
        val cuerpo = JSONObject().put("email", correo).put("password", clave).toString()
        val (codigo, respuesta) = ejecutar(
            abrir("${Config.SUPABASE_URL}/auth/v1/token?grant_type=password", "POST"),
            cuerpo
        )
        if (codigo == 400) throw ErrorSesion("Correo o contraseña incorrectos.")
        if (codigo !in 200..299) throw ErrorRed("El servidor respondió $codigo")
        guardarRespuesta(contexto, respuesta)
    }

    private fun guardarRespuesta(contexto: Context, respuesta: String) {
        val datos = try {
            JSONObject(respuesta)
        } catch (e: Exception) {
            throw ErrorRed("Respuesta inesperada del servidor")
        }
        val acceso = datos.optString("access_token")
        val refresco = datos.optString("refresh_token")
        if (acceso.isBlank() || refresco.isBlank()) throw ErrorSesion("El servidor no entregó una sesión")
        val correo = datos.optJSONObject("user")?.optString("email")
        Sesion.guardarTokens(contexto, acceso, refresco, datos.optLong("expires_in", 3600L), correo)
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

        val (codigo, respuesta) = ejecutar(
            abrir("${Config.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token", "POST"),
            JSONObject().put("refresh_token", refresco).toString()
        )
        if (codigo in 400..499) throw ErrorSesion("La sesión caducó. Vuelve a iniciar sesión.")
        if (codigo !in 200..299) throw ErrorRed("El servidor respondió $codigo")
        guardarRespuesta(contexto, respuesta)
        return Sesion.acceso(contexto) ?: throw ErrorSesion("No se pudo renovar la sesión")
    }

    fun avisosPendientes(contexto: Context): List<Aviso> {
        val token = tokenValido(contexto)
        val url = Config.SUPABASE_URL + "/rest/v1/avisos" +
            "?select=id,texto,nota,prioridad,vence" +
            "&completado_en=is.null" +
            "&order=prioridad.desc,vence.asc.nullslast,creado_en.asc" +
            "&limit=50"
        val conexion = abrir(url, "GET")
        conexion.setRequestProperty("Authorization", "Bearer $token")
        val (codigo, respuesta) = ejecutar(conexion, null)
        if (codigo == 401 || codigo == 403) throw ErrorSesion("La sesión ya no es válida")
        if (codigo !in 200..299) throw ErrorRed("El servidor respondió $codigo")

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

    private fun textoOpcional(fila: JSONObject, campo: String): String? {
        if (fila.isNull(campo)) return null
        val valor = fila.optString(campo)
        return if (valor.isBlank()) null else valor
    }
}
