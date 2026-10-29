package com.philco.controle

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Botão pronto de um modelo de aparelho (ainda sem código). */
private class Spec(
    val label: String,
    val color: Int,
    val r: Int,
    val c: Int,
    val repeat: Boolean = false
)

/** Tipo de aparelho mostrado na primeira tela, com o controle já montado. */
private class DeviceType(
    val icon: String,
    val name: String,
    val accent: Int,
    val rows: Int,
    val specs: List<Spec>
)

private fun numpad(r0: Int): List<Spec> {
    val g = Palette.COLORS[0]
    val l = mutableListOf<Spec>()
    var n = 1
    for (r in 0 until 3) {
        for (c in 0 until 3) {
            l.add(Spec(n.toString(), g, r0 + r, c))
            n++
        }
    }
    l.add(Spec("0", g, r0 + 3, 1))
    return l
}

private fun tvLike(withVol: Boolean): List<Spec> {
    val g = Palette.COLORS[0]
    val red = Palette.COLORS[1]
    val l = mutableListOf<Spec>()
    l.add(Spec("⏻", red, 0, 0))
    l.add(Spec("🔇", g, 0, 3))
    l.addAll(numpad(1))
    l.add(Spec("CH +", g, 1, 3, true))
    l.add(Spec("CH −", g, 2, 3, true))
    if (withVol) {
        l.add(Spec("VOL +", g, 3, 3, true))
        l.add(Spec("VOL −", g, 4, 3, true))
    }
    l.add(Spec("MENU", g, 5, 0))
    l.add(Spec(if (withVol) "FONTE" else "GUIA", g, 5, 1))
    l.add(Spec("INFO", g, 5, 2))
    l.add(Spec("EXIT", g, 5, 3))
    l.add(Spec("▲", g, 6, 1, true))
    l.add(Spec("◀", g, 7, 0, true))
    l.add(Spec("OK", g, 7, 1))
    l.add(Spec("▶", g, 7, 2, true))
    l.add(Spec("↩", g, 7, 3))
    l.add(Spec("▼", g, 8, 1, true))
    return l
}

private fun deviceTypes(): List<DeviceType> {
    val c = Palette.COLORS
    val g = c[0]
    val red = c[1]
    return listOf(
        DeviceType("📺", "TV", c[6], 9, tvLike(true)),
        DeviceType("📦", "Receptor / TV box", c[8], 9, tvLike(false)),
        DeviceType(
            "🔊", "Som", c[7], 3, listOf(
                Spec("⏻", red, 0, 0), Spec("🔇", g, 0, 3),
                Spec("⏮", g, 1, 0), Spec("▶❚❚", g, 1, 1), Spec("⏭", g, 1, 2), Spec("FONTE", g, 1, 3),
                Spec("VOL −", g, 2, 0, true), Spec("VOL +", g, 2, 3, true)
            )
        ),
        DeviceType(
            "📽️", "Projetor", c[2], 5, listOf(
                Spec("⏻", red, 0, 0), Spec("🔇", g, 0, 3),
                Spec("MENU", g, 1, 0), Spec("FONTE", g, 1, 1), Spec("↩", g, 1, 2),
                Spec("▲", g, 2, 1, true), Spec("VOL +", g, 2, 3, true),
                Spec("◀", g, 3, 0, true), Spec("OK", g, 3, 1), Spec("▶", g, 3, 2, true), Spec("VOL −", g, 3, 3, true),
                Spec("▼", g, 4, 1, true)
            )
        ),
        DeviceType(
            "🌀", "Ventilador", c[5], 3, listOf(
                Spec("⏻", red, 0, 0), Spec("SONO", g, 0, 3),
                Spec("VEL +", g, 1, 0), Spec("VEL −", g, 1, 1), Spec("OSCILAR", g, 1, 2), Spec("TIMER", g, 1, 3),
                Spec("MODO", g, 2, 0)
            )
        ),
        DeviceType(
            "💡", "Luz / Fita LED", c[3], 3, listOf(
                Spec("☀+", c[9], 0, 0, true), Spec("☀−", c[9], 0, 1, true), Spec("OFF", g, 0, 2), Spec("ON", red, 0, 3),
                Spec("R", red, 1, 0), Spec("G", c[4], 1, 1), Spec("B", c[6], 1, 2), Spec("W", c[9], 1, 3),
                Spec("FLASH", g, 2, 0), Spec("STROBE", g, 2, 1), Spec("FADE", g, 2, 2), Spec("SMOOTH", g, 2, 3)
            )
        ),
        DeviceType("➕", "Em branco", c[6], 4, emptyList())
    )
}

/**
 * Assistente em tela cheia, no estilo do Mi Remote:
 *  1. Escolha o tipo de aparelho (TV, som, ventilador...). O controle já vem montado.
 *  2. Ensine os códigos: informe o código do aparelho e toque em cada botão para definir/testar/procurar o código.
 */
class RemoteEditor(
    private val activity: Activity,
    private val remote: CustomRemote,
    private val isNew: Boolean,
    private val onSave: (CustomRemote) -> Unit,
    private val onTest: (Int, Int) -> Unit
) {
    private val bgColor = Color.parseColor("#14151A")
    private val cardColor = Color.parseColor("#23252C")
    private val fieldColor = Color.parseColor("#1B1C21")
    private val lineColor = Color.parseColor("#3A3C45")
    private val muted = Color.parseColor("#9A9AA0")
    private val primary = Color.parseColor("#4A6CF7")
    private val green = Color.parseColor("#2E9E5B")
    private val orange = Color.parseColor("#FF9800")

    private var step = 1

    private lateinit var nameInput: EditText
    private lateinit var addrInput: EditText
    private lateinit var stepText: TextView
    private lateinit var bar1: View
    private lateinit var bar2: View
    private lateinit var bars: View
    private lateinit var btnBack: TextView
    private lateinit var btnNext: TextView
    private lateinit var bodyHolder: FrameLayout
    private lateinit var gridBody: LinearLayout
    private lateinit var progressText: TextView

    private val dialog: Dialog = object : Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar) {
        @Suppress("DEPRECATION")
        override fun onBackPressed() {
            handleBack()
        }
    }

    fun show() {
        dialog.window?.setBackgroundDrawable(ColorDrawable(bgColor))
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setContentView(buildShell())
        showStep(if (isNew) 1 else 2)
        dialog.show()
        dialog.window?.setLayout(MATCH, MATCH)
    }

    // ------------------------------------------------------------------
    // Helpers visuais
    // ------------------------------------------------------------------

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density + 0.5f).toInt()

    private fun toast(m: String) = Toast.makeText(activity, m, Toast.LENGTH_SHORT).show()

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null, strokeDp: Int = 2): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(strokeDp), strokeColor)
        }

    private fun label(t: String, sizeSp: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(activity).apply {
            text = t
            textSize = sizeSp
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun section(t: String): TextView =
        label(t, 15f, Color.WHITE, true).apply { setPadding(0, dp(18), 0, dp(6)) }

    private fun hint(t: String): TextView =
        label(t, 12.5f, muted).apply { setPadding(0, dp(4), 0, dp(2)) }

    private fun field(initial: String, hintText: String, maxLen: Int, caps: Boolean = false): EditText =
        EditText(activity).apply {
            setText(initial)
            hint = hintText
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#66FFFFFF"))
            textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or
                    (if (caps) InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS else InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
            filters = arrayOf<InputFilter>(InputFilter.LengthFilter(maxLen))
            background = rounded(fieldColor, 12, lineColor, 1)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

    private fun EditText.onChange(block: () -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                block()
            }
        })
    }

    private fun actionButton(t: String, bg: Int, fg: Int): TextView =
        TextView(activity).apply {
            text = t
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(fg)
            background = rounded(bg, 25)
            isClickable = true
        }

    // ------------------------------------------------------------------
    // Estrutura da tela (cabeçalho, corpo, rodapé)
    // ------------------------------------------------------------------

    private fun buildShell(): View {
        val rootView = LinearLayout(activity)
        rootView.orientation = LinearLayout.VERTICAL
        rootView.setBackgroundColor(bgColor)

        val header = LinearLayout(activity)
        header.orientation = LinearLayout.VERTICAL
        header.setPadding(dp(20), dp(28), dp(20), dp(12))
        header.addView(label(if (isNew) "Adicionar controle" else "Editar controle", 22f, Color.WHITE, true))
        stepText = label("", 13f, muted)
        stepText.setPadding(0, dp(2), 0, dp(10))
        header.addView(stepText)
        val barRow = LinearLayout(activity)
        bar1 = View(activity)
        bar2 = View(activity)
        barRow.addView(bar1, LinearLayout.LayoutParams(0, dp(5), 1f).apply { rightMargin = dp(4) })
        barRow.addView(bar2, LinearLayout.LayoutParams(0, dp(5), 1f).apply { leftMargin = dp(4) })
        bars = barRow
        header.addView(barRow)
        rootView.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))

        bodyHolder = FrameLayout(activity)
        rootView.addView(bodyHolder, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val footer = LinearLayout(activity)
        footer.orientation = LinearLayout.HORIZONTAL
        footer.setPadding(dp(16), dp(10), dp(16), dp(16))
        btnBack = actionButton("", cardColor, Color.WHITE)
        btnNext = actionButton("✔  Salvar controle", primary, Color.WHITE)
        btnBack.setOnClickListener { handleBack() }
        btnNext.setOnClickListener { trySave() }
        footer.addView(btnBack, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(6) })
        footer.addView(btnNext, LinearLayout.LayoutParams(0, dp(50), 1.4f).apply { leftMargin = dp(6) })
        rootView.addView(footer, LinearLayout.LayoutParams(MATCH, WRAP))
        return rootView
    }

    private fun hideKeyboard() {
        try {
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(bodyHolder.windowToken, 0)
        } catch (_: Exception) {
        }
    }

    private fun showStep(n: Int) {
        hideKeyboard()
        step = n
        bodyHolder.removeAllViews()
        bars.visibility = if (isNew) View.VISIBLE else View.GONE
        bar1.background = rounded(primary, 3)
        bar2.background = rounded(if (n == 2) primary else lineColor, 3)
        if (n == 1) {
            stepText.text = "Passo 1 de 2  •  Tipo de aparelho"
            btnBack.text = "Cancelar"
            btnNext.visibility = View.GONE
            bodyHolder.addView(buildPicker())
        } else {
            stepText.text = if (isNew) "Passo 2 de 2  •  Ensine os códigos dos botões" else "Códigos dos botões"
            btnBack.text = if (isNew) "Voltar" else "Cancelar"
            btnNext.visibility = View.VISIBLE
            bodyHolder.addView(buildEditScreen())
        }
    }

    private fun handleBack() {
        if (step == 1) {
            dialog.dismiss()
            return
        }
        val anyCode = remote.buttons.any { it.cmd >= 0 }
        if (isNew && !anyCode) {
            remote.buttons.clear()
            showStep(1)
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("Sair sem salvar?")
            .setMessage("O que você fez neste controle será perdido.")
            .setPositiveButton("Sair") { _, _ -> dialog.dismiss() }
            .setNegativeButton("Continuar editando", null)
            .show()
    }

    private fun trySave() {
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            toast("Dê um nome ao controle.")
            nameInput.requestFocus()
            return
        }
        if (remote.addr < 0) {
            toast("Preencha o código do aparelho no topo da tela.")
            addrInput.requestFocus()
            return
        }
        if (remote.buttons.none { it.cmd >= 0 }) {
            toast("Defina o código de pelo menos um botão.")
            return
        }
        remote.name = name
        for (b in remote.buttons) b.addr = remote.addr
        val missing = remote.buttons.count { it.cmd < 0 }
        if (missing == 0) {
            finishSave()
        } else {
            AlertDialog.Builder(activity)
                .setTitle("Salvar assim mesmo?")
                .setMessage(
                    if (missing == 1) "1 botão ainda está sem código e vai ficar escondido no controle."
                    else "$missing botões ainda estão sem código e vão ficar escondidos no controle."
                )
                .setPositiveButton("Salvar") { _, _ -> finishSave() }
                .setNegativeButton("Voltar", null)
                .show()
        }
    }

    private fun finishSave() {
        onSave(remote)
        dialog.dismiss()
    }

    // ------------------------------------------------------------------
    // Tela 1: escolher o tipo de aparelho
    // ------------------------------------------------------------------

    private fun buildPicker(): View {
        val sv = ScrollView(activity)
        val col = LinearLayout(activity)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(0), dp(16), dp(24))
        sv.addView(col)

        col.addView(label("Que aparelho você quer controlar?", 20f, Color.WHITE, true))
        col.addView(hint("Escolha o tipo. O controle já vem montado: depois é só ensinar o código de cada botão."))
        col.addView(View(activity), LinearLayout.LayoutParams(MATCH, dp(10)))

        val types = deviceTypes()
        var i = 0
        while (i < types.size) {
            val row = LinearLayout(activity)
            for (k in 0 until 2) {
                val t = types.getOrNull(i + k)
                val lp = LinearLayout.LayoutParams(0, dp(120), 1f).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
                if (t != null) row.addView(tile(t), lp) else row.addView(View(activity), lp)
            }
            col.addView(row)
            i += 2
        }
        return sv
    }

    private fun tile(t: DeviceType): View {
        val card = LinearLayout(activity)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER
        card.setPadding(dp(8), dp(8), dp(8), dp(8))
        card.background = rounded(cardColor, 20, lineColor, 1)
        card.addView(label(t.icon, 38f).apply { gravity = Gravity.CENTER })
        card.addView(label(t.name, 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        })
        card.setOnClickListener { pickType(t) }
        return card
    }

    private fun pickType(t: DeviceType) {
        remote.name = t.name
        remote.accent = t.accent
        remote.rows = t.rows
        remote.buttons.clear()
        for (s in t.specs) {
            remote.buttons.add(CustomButton(s.label, -1, -1, s.color, s.r, s.c, 1, s.repeat))
        }
        showStep(2)
    }

    // ------------------------------------------------------------------
    // Tela 2: código do aparelho + botões
    // ------------------------------------------------------------------

    private fun buildEditScreen(): View {
        val sv = ScrollView(activity)
        val col = LinearLayout(activity)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(0), dp(16), dp(24))
        sv.addView(col)

        col.addView(label("Nome do controle", 14f, Color.WHITE, true))
        nameInput = field(remote.name, "Ex.: TV da sala", 24)
        nameInput.onChange { remote.name = nameInput.text.toString().trim() }
        col.addView(nameInput)

        col.addView(section("Código do aparelho"))
        addrInput = field(if (remote.addr >= 0) "%04X".format(remote.addr) else "", "Ex.: BF00", 6, caps = true)
        addrInput.onChange { remote.addr = parseHex(addrInput.text.toString(), 0xFFFF) ?: -1 }
        col.addView(addrInput)
        col.addView(hint("Identifica o aparelho e vale para todos os botões. Vem da lista de códigos do seu aparelho (ex.: BF00 na TV Philco)."))

        val how = label(
            "Como fazer:\n1. Preencha o código do aparelho.\n2. Toque num botão e informe o código dele. Dá para testar na hora ou usar 🔎 Procurar.\n3. Botões com borda laranja ainda não têm código e ficam escondidos no controle.",
            12.5f, Color.WHITE
        )
        how.setPadding(dp(14), dp(12), dp(14), dp(12))
        how.background = rounded(cardColor, 14, lineColor, 1)
        col.addView(how, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })

        progressText = label("", 13f, muted, true)
        progressText.setPadding(dp(4), dp(18), dp(4), dp(8))
        col.addView(progressText)

        gridBody = LinearLayout(activity)
        gridBody.orientation = LinearLayout.VERTICAL
        gridBody.setPadding(dp(8), dp(10), dp(8), dp(10))
        gridBody.background = rounded(Color.parseColor("#1B1B1D"), 28, remote.accent, 3)
        col.addView(gridBody, LinearLayout.LayoutParams(MATCH, WRAP))

        val add = actionButton("＋  Adicionar botão", cardColor, Color.WHITE)
        add.setOnClickListener { addExtraButton() }
        col.addView(add, LinearLayout.LayoutParams(MATCH, dp(48)).apply { topMargin = dp(14) })

        renderGrid()
        return sv
    }

    private fun renderGrid() {
        gridBody.removeAllViews()
        val done = remote.buttons.count { it.cmd >= 0 }
        progressText.text = if (remote.buttons.isEmpty()) "Nenhum botão ainda"
        else "✔ $done de ${remote.buttons.size} botões com código"

        if (remote.buttons.isEmpty()) {
            gridBody.addView(label("Toque em ＋ Adicionar botão para começar.", 14f, muted).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(24), dp(8), dp(24))
            })
            return
        }
        for (r in 0 until remote.rows) {
            val row = LinearLayout(activity)
            row.orientation = LinearLayout.HORIZONTAL
            var c = 0
            var any = false
            while (c < CustomRemote.COLS) {
                val b = remote.buttonAt(r, c)
                if (b != null && b.col == c) {
                    row.addView(cellButton(b), cellLp(b.span.toFloat()))
                    any = true
                    c += b.span
                } else {
                    row.addView(View(activity), cellLp(1f))
                    c += 1
                }
            }
            if (any) gridBody.addView(row)
        }
    }

    private fun cellLp(weight: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, dp(56), weight).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }

    private fun cellButton(b: CustomButton): View {
        val shown = if (b.label.isNotBlank()) b.label else if (b.cmd >= 0) "%02X".format(b.cmd) else "?"
        val tv = label(shown, if (shown.length > 6) 11f else 15f, readableOn(b.color), true)
        tv.gravity = Gravity.CENTER
        tv.maxLines = 2
        tv.background = if (b.cmd >= 0) rounded(b.color, 14) else rounded(b.color, 14, orange, 3)
        tv.setOnClickListener { showCodeDialog(b, false) }
        return tv
    }

    private fun firstFreeCell(): Pair<Int, Int>? {
        for (r in 0 until remote.rows) {
            for (c in 0 until CustomRemote.COLS) {
                if (remote.buttonAt(r, c) == null) return Pair(r, c)
            }
        }
        return null
    }

    private fun addExtraButton() {
        val cell = firstFreeCell() ?: run {
            if (remote.rows >= 12) {
                toast("O controle está cheio.")
                return
            }
            remote.rows++
            Pair(remote.rows - 1, 0)
        }
        val nb = CustomButton("", -1, remote.addr, Palette.COLORS[0], cell.first, cell.second)
        remote.buttons.add(nb)
        renderGrid()
        showCodeDialog(nb, true)
    }

    // ------------------------------------------------------------------
    // Janela do botão: código, teste e busca automática
    // ------------------------------------------------------------------

    private fun showCodeDialog(b: CustomButton, isNewButton: Boolean) {
        val handler = Handler(Looper.getMainLooper())
        var committed = false
        var scanning = false
        var scanCmd = 0
        var lastSent = -1
        var scanRunnable: Runnable? = null

        val sv = ScrollView(activity)
        val box = LinearLayout(activity)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(18), dp(20), dp(10))
        sv.addView(box)

        val labelInput = field(b.label, "Ex.: ⏻  Vol +  Ligar", 10)
        val cmdInput = field(if (b.cmd >= 0) "%02X".format(b.cmd) else "", "00", 4, caps = true)
        cmdInput.textSize = 28f
        cmdInput.gravity = Gravity.CENTER

        box.addView(label(if (isNewButton) "Novo botão" else "Botão ${b.label}".trim(), 20f, Color.WHITE, true))

        box.addView(section("Código do botão"))
        box.addView(cmdInput)
        box.addView(hint("2 dígitos, de 00 a FF, como na lista de códigos do aparelho."))

        val test = actionButton("▶  Testar", green, Color.WHITE)
        val search = actionButton("🔎  Procurar", cardColor, Color.WHITE)
        val actions = LinearLayout(activity)
        actions.addView(test, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(4) })
        actions.addView(search, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(4) })
        box.addView(actions, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) })

        // Painel da busca automática
        val scanPanel = LinearLayout(activity)
        scanPanel.orientation = LinearLayout.VERTICAL
        scanPanel.gravity = Gravity.CENTER_HORIZONTAL
        scanPanel.setPadding(dp(14), dp(14), dp(14), dp(14))
        scanPanel.background = rounded(fieldColor, 16, primary, 2)
        scanPanel.visibility = View.GONE
        scanPanel.addView(label("Aponte para o aparelho. Vou enviar um código por vez. Assim que ele reagir, toque rápido em “Esse funcionou!”.", 12.5f, muted).apply {
            gravity = Gravity.CENTER
        })
        val scanHex = label("--", 40f, Color.WHITE, true)
        scanHex.gravity = Gravity.CENTER
        scanPanel.addView(scanHex)
        val scanAgain = actionButton("↺ Repetir", cardColor, Color.WHITE)
        val scanYes = actionButton("✔ Esse funcionou!", green, Color.WHITE)
        val scanRow = LinearLayout(activity)
        scanRow.addView(scanAgain, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(4) })
        scanRow.addView(scanYes, LinearLayout.LayoutParams(0, dp(44), 1.4f).apply { leftMargin = dp(4) })
        scanPanel.addView(scanRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
        val scanStop = actionButton("■ Parar", Color.parseColor("#7A1F1F"), Color.WHITE)
        scanPanel.addView(scanStop, LinearLayout.LayoutParams(MATCH, dp(40)).apply { topMargin = dp(8) })
        box.addView(scanPanel, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })

        box.addView(section("Nome do botão (opcional)"))
        box.addView(labelInput)
        box.addView(hint("Pode usar emoji ou símbolos (⏻ ▲ 🔊)."))

        val buttons = LinearLayout(activity)
        buttons.setPadding(0, dp(16), 0, dp(4))
        val delete = actionButton("Apagar", Color.parseColor("#7A1F1F"), Color.WHITE)
        val cancel = actionButton("Cancelar", fieldColor, Color.WHITE)
        val save = actionButton("OK", primary, Color.WHITE)
        if (!isNewButton) {
            buttons.addView(delete, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(4) })
        }
        buttons.addView(cancel, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        buttons.addView(save, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(4) })
        box.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP))

        fun stopScan() {
            scanning = false
            scanRunnable?.let { handler.removeCallbacks(it) }
            scanPanel.visibility = View.GONE
        }

        val tick = object : Runnable {
            override fun run() {
                if (!scanning) return
                if (scanCmd > 0xFF) {
                    stopScan()
                    toast("Testei todos os códigos até FF e nenhum funcionou. Confira o código do aparelho.")
                    return
                }
                lastSent = scanCmd
                scanHex.text = "%02X".format(scanCmd)
                onTest(remote.addr, scanCmd)
                scanCmd++
                handler.postDelayed(this, 2000)
            }
        }
        scanRunnable = tick

        fun startScan() {
            if (remote.addr < 0) {
                toast("Primeiro preencha o código do aparelho, no topo da tela.")
                return
            }
            scanning = true
            scanCmd = 0
            lastSent = -1
            scanPanel.visibility = View.VISIBLE
            handler.removeCallbacks(tick)
            handler.post(tick)
        }

        val dlg = AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(sv)
            .create()
        dlg.window?.setBackgroundDrawable(rounded(cardColor, 22))
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        test.setOnClickListener {
            val cmd = parseHex(cmdInput.text.toString(), 0xFF)
            if (remote.addr < 0) {
                toast("Primeiro preencha o código do aparelho, no topo da tela.")
            } else if (cmd == null) {
                toast("Digite o código do botão (00 a FF).")
            } else {
                onTest(remote.addr, cmd)
            }
        }
        search.setOnClickListener { startScan() }
        scanAgain.setOnClickListener { if (lastSent >= 0) onTest(remote.addr, lastSent) }
        scanYes.setOnClickListener {
            if (lastSent >= 0) {
                val found = "%02X".format(lastSent)
                stopScan()
                cmdInput.setText(found)
                toast("Código $found escolhido. Toque em Testar para confirmar e em OK para guardar.")
            }
        }
        scanStop.setOnClickListener { stopScan() }
        cancel.setOnClickListener { dlg.dismiss() }
        delete.setOnClickListener {
            remote.buttons.remove(b)
            committed = true
            renderGrid()
            dlg.dismiss()
        }
        save.setOnClickListener {
            val txt = cmdInput.text.toString().trim()
            val cmd = if (txt.isEmpty()) -1 else parseHex(txt, 0xFF)
            if (cmd == null) {
                toast("Código inválido. Use 2 dígitos de 00 a FF, ex.: 03")
                return@setOnClickListener
            }
            b.label = labelInput.text.toString().trim()
            b.cmd = cmd
            committed = true
            renderGrid()
            dlg.dismiss()
        }
        dlg.setOnDismissListener {
            stopScan()
            if (isNewButton && !committed) {
                remote.buttons.remove(b)
                renderGrid()
            }
        }
        dlg.show()
    }
}
