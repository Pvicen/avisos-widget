package io.github.pvicen.avisos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Copia local de los avisos: el widget siempre pinta desde aquí, así que
 * sigue mostrando la última lista conocida aunque no haya red.
 *
 * Los avisos marcados como hechos desde el widget no se borran de la lista:
 * se anotan como "en camino" y se filtran al leer. Así desaparecen al instante,
 * pero si el servidor nunca llega a confirmarlo vuelven a aparecer en vez de
 * perderse.
 */
object Cache {
    private const val LISTA = "lista"
    private const val ACTUALIZADO = "actualizado"
    private const val ERROR = "error"
    private const val EN_CAMINO = "en_camino"

    /** Red de seguridad: nada queda oculto para siempre. */
    private const val VIDA_OCULTO = 24 * 60 * 60 * 1000L

    // ---------- Lista ----------

    fun guardarAvisos(contexto: Context, avisos: List<Aviso>) {
        val editor = Sesion.prefs(contexto).edit()
        editor.putString(LISTA, aJson(avisos))
        editor.putLong(ACTUALIZADO, System.currentTimeMillis())
        editor.remove(ERROR)
        editor.apply()
    }

    /** La lista visible: sin los avisos que se están marcando como hechos. */
    fun leerAvisos(contexto: Context): List<Aviso> {
        val enCamino = enCaminoVigentes(contexto)
        return leerTodos(contexto).filter { !enCamino.contains(it.id) }
    }

    private fun leerTodos(contexto: Context): List<Aviso> {
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

    private fun aJson(avisos: List<Aviso>): String {
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
        return arreglo.toString()
    }

    // ---------- Marcados como hechos desde el widget ----------

    /**
     * Lo esconde de la lista mientras viaja al servidor.
     * Devuelve false si ya estaba en camino (toque repetido).
     */
    @Synchronized
    fun ocultar(contexto: Context, id: String): Boolean {
        val enCamino = leerEnCamino(contexto)
        val ahora = System.currentTimeMillis()
        val anterior = enCamino.optLong(id, 0L)
        if (anterior != 0L && ahora - anterior < VIDA_OCULTO) return false

        enCamino.put(id, ahora)
        Sesion.prefs(contexto).edit().putString(EN_CAMINO, enCamino.toString()).apply()
        return true
    }

    /** El servidor lo confirmó: fuera de la lista y de los pendientes. */
    @Synchronized
    fun completado(contexto: Context, id: String) {
        val enCamino = leerEnCamino(contexto)
        enCamino.remove(id)
        val editor = Sesion.prefs(contexto).edit()
        editor.putString(LISTA, aJson(leerTodos(contexto).filter { it.id != id }))
        editor.putString(EN_CAMINO, enCamino.toString())
        editor.apply()
    }

    /** No se pudo completar: que vuelva a verse en la lista. */
    @Synchronized
    fun revertir(contexto: Context, id: String) {
        val enCamino = leerEnCamino(contexto)
        enCamino.remove(id)
        Sesion.prefs(contexto).edit().putString(EN_CAMINO, enCamino.toString()).apply()
    }

    private fun leerEnCamino(contexto: Context): JSONObject {
        val texto = Sesion.prefs(contexto).getString(EN_CAMINO, null) ?: return JSONObject()
        return try {
            JSONObject(texto)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun enCaminoVigentes(contexto: Context): Set<String> {
        val enCamino = leerEnCamino(contexto)
        if (enCamino.length() == 0) return emptySet()

        val ahora = System.currentTimeMillis()
        val vigentes = HashSet<String>()
        val caducados = ArrayList<String>()
        for (id in enCamino.keys()) {
            if (ahora - enCamino.optLong(id, 0L) < VIDA_OCULTO) vigentes.add(id) else caducados.add(id)
        }
        if (caducados.isNotEmpty()) {
            for (id in caducados) enCamino.remove(id)
            Sesion.prefs(contexto).edit().putString(EN_CAMINO, enCamino.toString()).apply()
        }
        return vigentes
    }

    // ---------- Estado ----------

    fun guardarError(contexto: Context, mensaje: String) {
        Sesion.prefs(contexto).edit().putString(ERROR, mensaje).apply()
    }

    fun error(contexto: Context): String? = Sesion.prefs(contexto).getString(ERROR, null)

    fun actualizado(contexto: Context): Long = Sesion.prefs(contexto).getLong(ACTUALIZADO, 0L)
}
