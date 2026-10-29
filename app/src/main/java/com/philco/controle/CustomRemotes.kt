package com.philco.controle

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/** Cores oferecidas na ferramenta de criação de controles. */
object Palette {
    val COLORS: List<Int> by lazy {
        listOf(
            "#34353A", // cinza
            "#D32F2F", // vermelho
            "#F57C00", // laranja
            "#F2C811", // amarelo
            "#2E9E5B", // verde
            "#2FA9A3", // turquesa
            "#1E73D8", // azul
            "#7E57C2", // roxo
            "#E58BC0", // rosa
            "#F4F4F6"  // branco
        ).map { Color.parseColor(it) }
    }
}

/** Escolhe texto claro ou escuro, conforme o brilho da cor de fundo. */
fun readableOn(color: Int): Int {
    val luminance = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
    return if (luminance > 150) Color.parseColor("#1C1C1E") else Color.WHITE
}

/** Converte texto hexadecimal ("BF00", "0x03") em número; devolve null se for inválido ou maior que [max]. */
fun parseHex(text: String, max: Int): Int? {
    val t = text.trim().removePrefix("0x").removePrefix("0X")
    if (t.isEmpty()) return null
    val v = t.toIntOrNull(16) ?: return null
    return if (v in 0..max) v else null
}

/** Um botão de um controle criado pelo usuário. Fica numa grade de [CustomRemote.COLS] colunas. */
class CustomButton(
    var label: String,
    var cmd: Int,
    var addr: Int,
    var color: Int,
    var row: Int,
    var col: Int,
    var span: Int = 1,
    var repeat: Boolean = false
) {
    fun copy(): CustomButton = CustomButton(label, cmd, addr, color, row, col, span, repeat)

    fun toJson(): JSONObject = JSONObject()
        .put("label", label)
        .put("cmd", cmd)
        .put("addr", addr)
        .put("color", color)
        .put("row", row)
        .put("col", col)
        .put("span", span)
        .put("repeat", repeat)

    companion object {
        fun fromJson(o: JSONObject): CustomButton = CustomButton(
            label = o.optString("label", ""),
            cmd = o.getInt("cmd"),
            addr = o.getInt("addr"),
            color = o.getInt("color"),
            row = o.getInt("row"),
            col = o.getInt("col"),
            span = o.optInt("span", 1),
            repeat = o.optBoolean("repeat", false)
        )
    }
}

/** Um controle criado pelo usuário: nome, cor, endereço NEC padrão e botões. */
class CustomRemote(
    val id: String,
    var name: String,
    var accent: Int,
    /** Endereço NEC padrão (16 bits). -1 enquanto ainda não foi definido. */
    var addr: Int,
    var rows: Int,
    val buttons: MutableList<CustomButton> = mutableListOf()
) {
    /** Botão que ocupa a casa ([row], [col]), se houver. */
    fun buttonAt(row: Int, col: Int): CustomButton? =
        buttons.firstOrNull { it.row == row && col >= it.col && col < it.col + it.span }

    fun deepCopy(): CustomRemote =
        CustomRemote(id, name, accent, addr, rows, buttons.map { it.copy() }.toMutableList())

    fun toJson(): JSONObject {
        val arr = JSONArray()
        for (b in buttons) arr.put(b.toJson())
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .put("accent", accent)
            .put("addr", addr)
            .put("rows", rows)
            .put("buttons", arr)
    }

    companion object {
        const val COLS = 4

        fun fromJson(o: JSONObject): CustomRemote {
            val list = mutableListOf<CustomButton>()
            val arr = o.optJSONArray("buttons") ?: JSONArray()
            for (i in 0 until arr.length()) list.add(CustomButton.fromJson(arr.getJSONObject(i)))
            return CustomRemote(
                id = o.getString("id"),
                name = o.optString("name", "Controle"),
                accent = o.getInt("accent"),
                addr = o.getInt("addr"),
                rows = o.optInt("rows", 5).coerceIn(1, 12),
                buttons = list
            )
        }
    }
}

/** Guarda os controles criados no próprio celular (SharedPreferences, em JSON). */
object RemoteStore {
    private const val PREFS = "controles_personalizados"
    private const val KEY = "lista"

    fun load(ctx: Context): MutableList<CustomRemote> {
        val txt = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return mutableListOf()
        return try {
            val arr = JSONArray(txt)
            val out = mutableListOf<CustomRemote>()
            for (i in 0 until arr.length()) out.add(CustomRemote.fromJson(arr.getJSONObject(i)))
            out
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(ctx: Context, list: List<CustomRemote>) {
        val arr = JSONArray()
        for (r in list) arr.put(r.toJson())
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .apply()
    }
}
