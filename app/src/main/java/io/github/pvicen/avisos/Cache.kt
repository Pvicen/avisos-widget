package io.github.pvicen.avisos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Copia local de los avisos: el widget siempre pinta desde aquí, así que
 * sigue mostrando la última lista conocida aunque no haya red.
 */
object Cache {
    private const val LISTA = "lista"
    private const val ACTUALIZADO = "actualizado"
    private const val ERROR = "error"

    fun guardarAvisos(contexto: Context, avisos: List<Aviso>) {
        val arreglo = JSONArray()
        for (aviso in avisos) {
            val fila = JSONObject()
            fila.put("id", aviso.id)
            fila.put("texto", aviso.texto)
            fila.put("prioridad", aviso.prioridad)
            if (aviso.nota != null) fila.put("nota", aviso.nota)
            if (aviso.vence != null) fila.put("vence", aviso.vence)
            arreglo.put(fila)
        }
        val editor = Sesion.prefs(contexto).edit()
        editor.putString(LISTA, arreglo.toString())
        editor.putLong(ACTUALIZADO, System.currentTimeMillis())
        editor.remove(ERROR)
        editor.apply()
    }

    fun leerAvisos(contexto: Context): List<Aviso> {
        val texto = Sesion.prefs(contexto).getString(LISTA, null) ?: return emptyList()
        return try {
            val arreglo = JSONArray(texto)
            val avisos = ArrayList<Aviso>(arreglo.length())
            for (i in 0 until arreglo.length()) {
                val fila = arreglo.optJSONObject(i) ?: continue
                avisos.add(
                    Aviso(
                        id = fila.optString("id"),
                        texto = fila.optString("texto"),
                        nota = if (fila.isNull("nota")) null else fila.optString("nota"),
                        prioridad = fila.optBoolean("prioridad", false),
                        vence = if (fila.isNull("vence")) null else fila.optString("vence")
                    )
                )
            }
            avisos
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun guardarError(contexto: Context, mensaje: String) {
        Sesion.prefs(contexto).edit().putString(ERROR, mensaje).apply()
    }

    fun error(contexto: Context): String? = Sesion.prefs(contexto).getString(ERROR, null)

    fun actualizado(contexto: Context): Long = Sesion.prefs(contexto).getLong(ACTUALIZADO, 0L)
}
