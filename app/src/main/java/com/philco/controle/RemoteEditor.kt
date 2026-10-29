package com.philco.controle

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * Assistente em tela cheia para criar (ou editar) um controle, em 2 passos:
 *  1. Nome, cor, ponto de partida e endereço do aparelho.
 *  2. Montagem visual dos botões numa grade: toque em "＋" para adicionar, toque num botão para editar.
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

    private var step = 1
    private var selectedTemplate = 0
    private var appliedTemplate = if (isNew) -1 else 0

    private lateinit var nameInput: EditText
    private lateinit var addrInput: EditText
    private lateinit var stepText: TextView
    private lateinit var bar1: View
    private lateinit var bar2: View
    private lateinit var btnBack: TextView
    private lateinit var btnNext: TextView
    private lateinit var bodyHolder: FrameLayout
    private lateinit var gridBody: LinearLayout

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
        showStep(1)
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
        label(t, 16f, Color.WHITE, true).apply { setPadding(0, dp(20), 0, dp(6)) }

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

    /** Grade de cores; [onPick] é chamado quando o usuário toca numa. */
    private fun swatches(initial: Int, onPick: (Int) -> Unit): LinearLayout {
        val box = LinearLayout(activity)
        box.orientation = LinearLayout.VERTICAL
        var current = initial
        fun fill() {
            box.removeAllViews()
            for (rowColors in Palette.COLORS.chunked(5)) {
                val row = LinearLayout(activity)
                for (c in rowColors) {
                    val v = View(activity)
                    v.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(c)
                        if (c == current) setStroke(dp(3), Color.WHITE) else setStroke(dp(1), Color.parseColor("#55FFFFFF"))
                    }
                    v.setOnClickListener {
                        current = c
                        onPick(c)
                        fill()
                    }
                    row.addView(v, LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
                }
                box.addView(row)
            }
        }
        fill()
        return box
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
        header.addView(label(if (isNew) "Criar novo controle" else "Editar controle", 22f, Color.WHITE, true))
        stepText = label("", 13f, muted)
        stepText.setPadding(0, dp(2), 0, dp(10))
        header.addView(stepText)
        val bars = LinearLayout(activity)
        bar1 = View(activity)
        bar2 = View(activity)
        bars.addView(bar1, LinearLayout.LayoutParams(0, dp(5), 1f).apply { rightMargin = dp(4) })
        bars.addView(bar2, LinearLayout.LayoutParams(0, dp(5), 1f).apply { leftMargin = dp(4) })
        header.addView(bars)
        rootView.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))

        bodyHolder = FrameLayout(activity)
        rootView.addView(bodyHolder, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val footer = LinearLayout(activity)
        footer.orientation = LinearLayout.HORIZONTAL
        footer.setPadding(dp(16), dp(10), dp(16), dp(16))
        btnBack = actionButton("", cardColor, Color.WHITE)
        btnNext = actionButton("", primary, Color.WHITE)
        btnBack.setOnClickListener { handleBack() }
        btnNext.setOnClickListener { if (step == 1) goNext() else saveAndClose() }
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
        bar1.background = rounded(primary, 3)
        bar2.background = rounded(if (n == 2) primary else lineColor, 3)
        if (n == 1) {
            stepText.text = "Passo 1 de 2  •  Nome e aparência"
            btnBack.text = "Cancelar"
            btnNext.text = "Próximo  ➜"
            bodyHolder.addView(buildStep1())
        } else {
            stepText.text = "Passo 2 de 2  •  Monte os botões"
            btnBack.text = "Voltar"
            btnNext.text = "✔  Salvar controle"
            bodyHolder.addView(buildStep2())
        }
    }

    private fun handleBack() {
        if (step == 2) {
            showStep(1)
            return
        }
        val nothingDone = isNew && remote.buttons.isEmpty() && nameInput.text.toString().isBlank()
        if (nothingDone) {
            dialog.dismiss()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("Sair sem salvar?")
            .setMessage("O que você fez neste controle será perdido.")
            .setPositiveButton("Sair") { _, _ -> dialog.dismiss() }
            .setNegativeButton("Continuar editando", null)
            .show()
    }

    private fun goNext() {
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            toast("Dê um nome ao controle para continuar.")
            nameInput.requestFocus()
            return
        }
        val a = parseHex(addrInput.text.toString(), 0xFFFF)
        if (a == null) {
            toast("Endereço inválido. Use até 4 dígitos hexadecimais, ex.: BF00")
            addrInput.requestFocus()
            return
        }
        remote.name = name
        remote.addr = a
        if (isNew && appliedTemplate != selectedTemplate) {
            applyTemplate()
            appliedTemplate = selectedTemplate
        }
        showStep(2)
    }

    private fun saveAndClose() {
        if (remote.buttons.isEmpty()) {
            toast("Adicione pelo menos um botão antes de salvar.")
            return
        }
        onSave(remote)
        dialog.dismiss()
    }

    // ------------------------------------------------------------------
    // Passo 1: nome, cor, ponto de partida e endereço
    // ------------------------------------------------------------------

    private fun templateCards(): LinearLayout {
        val row = LinearLayout(activity)
        val items = listOf(
            Triple("📄", "Em branco", "Você monta tudo do zero."),
            Triple("📺", "TV básico", "Liga/desliga, volume, canais e setas já prontos, com os códigos da TV Philco.")
        )
        fun fill() {
            row.removeAllViews()
            items.forEachIndexed { i, (icon, title, desc) ->
                val selected = selectedTemplate == i
                val card = LinearLayout(activity)
                card.orientation = LinearLayout.VERTICAL
                card.setPadding(dp(12), dp(12), dp(12), dp(12))
                card.background = rounded(cardColor, 16, if (selected) primary else lineColor, if (selected) 3 else 1)
                card.addView(label(icon, 26f))
                card.addView(label(title, 15f, Color.WHITE, true))
                card.addView(label(desc, 12f, muted))
                card.setOnClickListener {
                    selectedTemplate = i
                    if (i == 1) {
                        addrInput.setText("BF00")
                    } else if (addrInput.text.toString().trim().uppercase() == "BF00") {
                        addrInput.setText("")
                    }
                    fill()
                }
                row.addView(card, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
        }
        fill()
        return row
    }

    private fun buildStep1(): View {
        val sv = ScrollView(activity)
        val col = LinearLayout(activity)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(0), dp(20), dp(24))
        sv.addView(col)

        var n = 0
        fun title(t: String): TextView = section("${++n}.  $t")

        // Nome
        col.addView(title("Dê um nome ao controle"))
        nameInput = field(remote.name, "Ex.: Ar-condicionado, Som, Ventilador", 24)
        col.addView(nameInput)

        // Cor + pré-visualização
        col.addView(title("Escolha uma cor"))
        val chip = label("", 14f, Color.WHITE, true)
        chip.gravity = Gravity.CENTER
        chip.setPadding(dp(20), dp(9), dp(20), dp(9))
        fun refreshChip() {
            chip.text = nameInput.text.toString().trim().ifBlank { "Meu controle" }
            chip.background = rounded(remote.accent, 20)
            chip.setTextColor(readableOn(remote.accent))
        }
        refreshChip()
        nameInput.onChange { refreshChip() }
        col.addView(swatches(remote.accent) {
            remote.accent = it
            refreshChip()
        })
        col.addView(hint("É assim que a aba dele vai aparecer no app:"))
        col.addView(chip, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(6) })

        // Ponto de partida (só para controle novo)
        if (isNew) {
            col.addView(title("Como você quer começar?"))
            col.addView(templateCards())
            col.addView(hint("Se trocar o modelo depois, os botões que você montou serão substituídos."))
        }

        // Endereço
        col.addView(title("Código do aparelho (endereço)"))
        addrInput = field(if (remote.addr >= 0) "%04X".format(remote.addr) else "", "Ex.: BF00", 6, caps = true)
        col.addView(addrInput)
        col.addView(hint("É o número que identifica o aparelho. Ele vale para todos os botões (dá para mudar em cada botão). No app: BF00 = TV Philco, EF00 = fita LED."))
        col.addView(hint("ℹ️ O app envia no formato NEC (38 kHz), o mesmo dos dois controles que já existem. Outros formatos ainda não são suportados."))
        col.addView(hint("💡 O celular só transmite, não copia controles. Os códigos vêm do manual, de uma lista na internet ou de uma captura feita por você."))
        return sv
    }

    private fun applyTemplate() {
        remote.buttons.clear()
        if (selectedTemplate == 1) {
            remote.rows = 6
            val a = remote.addr
            val gray = Palette.COLORS[0]
            val red = Palette.COLORS[1]
            fun add(label: String, cmd: Int, color: Int, r: Int, c: Int, rep: Boolean = false) {
                remote.buttons.add(CustomButton(label, cmd, a, color, r, c, 1, rep))
            }
            add("⏻", 0x03, red, 0, 0)
            add("🔇", 0x15, gray, 0, 3)
            add("VOL +", 0x16, gray, 1, 0, true)
            add("MENU", 0x1C, gray, 1, 1)
            add("⌂", 0x1E, gray, 1, 2)
            add("CH +", 0x14, gray, 1, 3, true)
            add("VOL −", 0x1A, gray, 2, 0, true)
            add("CH −", 0x18, gray, 2, 3, true)
            add("▲", 0x1D, gray, 3, 1, true)
            add("EXIT", 0x44, gray, 3, 3)
            add("◀", 0x40, gray, 4, 0, true)
            add("OK", 0x41, gray, 4, 1)
            add("▶", 0x42, gray, 4, 2, true)
            add("↩", 0x0E, gray, 4, 3)
            add("▼", 0x45, gray, 5, 1, true)
            add("INFO", 0x46, gray, 5, 3)
        } else {
            remote.rows = 5
        }
    }

    // ------------------------------------------------------------------
    // Passo 2: grade visual de botões
    // ------------------------------------------------------------------

    private fun buildStep2(): View {
        val sv = ScrollView(activity)
        val col = LinearLayout(activity)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(0), dp(16), dp(24))
        sv.addView(col)

        col.addView(label("👆 Toque num espaço com ＋ para adicionar um botão. Toque num botão para editar, testar ou apagar.", 13.5f, Color.WHITE).apply {
            setPadding(dp(4), dp(0), dp(4), dp(10))
        })

        gridBody = LinearLayout(activity)
        gridBody.orientation = LinearLayout.VERTICAL
        gridBody.setPadding(dp(8), dp(10), dp(8), dp(10))
        gridBody.background = rounded(Color.parseColor("#1B1B1D"), 28, remote.accent, 3)
        col.addView(gridBody, LinearLayout.LayoutParams(MATCH, WRAP))

        val ctl = LinearLayout(activity)
        val addRow = actionButton("＋ Linha", cardColor, Color.WHITE)
        val delRow = actionButton("− Linha", cardColor, Color.WHITE)
        addRow.setOnClickListener {
            if (remote.rows < 12) {
                remote.rows++
                renderGrid()
            } else {
                toast("O máximo é 12 linhas.")
            }
        }
        delRow.setOnClickListener {
            val last = remote.rows - 1
            if (remote.rows <= 1) {
                toast("O controle precisa de pelo menos 1 linha.")
            } else if (remote.buttons.any { it.row == last }) {
                toast("A última linha tem botões. Apague-os antes de remover a linha.")
            } else {
                remote.rows--
                renderGrid()
            }
        }
        ctl.addView(addRow, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(4), dp(10), dp(4), 0) })
        ctl.addView(delRow, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(4), dp(10), dp(4), 0) })
        col.addView(ctl)

        renderGrid()
        return sv
    }

    private fun renderGrid() {
        gridBody.removeAllViews()
        for (r in 0 until remote.rows) {
            val row = LinearLayout(activity)
            row.orientation = LinearLayout.HORIZONTAL
            var c = 0
            while (c < CustomRemote.COLS) {
                val b = remote.buttonAt(r, c)
                if (b != null && b.col == c) {
                    row.addView(cellButton(b), cellLp(b.span.toFloat()))
                    c += b.span
                } else {
                    row.addView(cellEmpty(r, c), cellLp(1f))
                    c += 1
                }
            }
            gridBody.addView(row)
        }
    }

    private fun cellLp(weight: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, dp(60), weight).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }

    private fun cellEmpty(r: Int, c: Int): View {
        val tv = label("＋", 22f, Color.parseColor("#77FFFFFF"))
        tv.gravity = Gravity.CENTER
        tv.background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(2), Color.parseColor("#55FFFFFF"), dp(6).toFloat(), dp(4).toFloat())
        }
        tv.setOnClickListener { showButtonDialog(r, c, null) }
        return tv
    }

    private fun cellButton(b: CustomButton): View {
        val noName = b.label.isBlank()
        val tv = label(if (noName) "%02X".format(b.cmd) else b.label, if (noName) 11f else 15f, readableOn(b.color), true)
        tv.gravity = Gravity.CENTER
        tv.maxLines = 2
        tv.background = rounded(b.color, 14)
        tv.setOnClickListener { showButtonDialog(b.row, b.col, b) }
        return tv
    }

    // ------------------------------------------------------------------
    // Janela de criar/editar um botão
    // ------------------------------------------------------------------

    private fun showButtonDialog(row: Int, col: Int, existing: CustomButton?) {
        var color = existing?.color ?: Palette.COLORS[0]
        var span = existing?.span ?: 1
        val canWide = col + 1 < CustomRemote.COLS &&
                remote.buttonAt(row, col + 1).let { it == null || it === existing }

        val sv = ScrollView(activity)
        val box = LinearLayout(activity)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(18), dp(20), dp(10))
        sv.addView(box)

        val labelInput = field(existing?.label ?: "", "Ex.: ⏻  Vol +  Ligar", 10)
        val cmdInput = field(if (existing != null) "%02X".format(existing.cmd) else "", "Ex.: 03", 4, caps = true)
        val addrField = field("%04X".format(existing?.addr ?: remote.addr), "Ex.: BF00", 6, caps = true)
        val repeatBox = CheckBox(activity).apply {
            text = "Repetir enquanto estiver segurando (setas, volume, canal)"
            setTextColor(Color.WHITE)
            textSize = 14f
            isChecked = existing?.repeat ?: false
        }

        val preview = TextView(activity)
        preview.gravity = Gravity.CENTER
        preview.typeface = Typeface.DEFAULT_BOLD
        preview.textSize = 18f
        fun updatePreview() {
            preview.text = labelInput.text.toString().trim()
            preview.background = rounded(color, 18)
            preview.setTextColor(readableOn(color))
            preview.layoutParams = LinearLayout.LayoutParams(dp(if (span == 2) 200 else 100), dp(60))
        }

        val sizeRow = LinearLayout(activity)
        fun fillSize() {
            sizeRow.removeAllViews()
            listOf(1 to "Normal", 2 to "Largo (2 casas)").forEach { (v, name) ->
                val selected = span == v
                val chip = label(name, 14f, if (v == 2 && !canWide) Color.parseColor("#55FFFFFF") else Color.WHITE, true)
                chip.gravity = Gravity.CENTER
                chip.setPadding(dp(14), dp(10), dp(14), dp(10))
                chip.background = rounded(if (selected) primary else fieldColor, 20, lineColor, 1)
                chip.setOnClickListener {
                    if (v == 2 && !canWide) {
                        toast("Não há espaço ao lado para um botão largo.")
                    } else {
                        span = v
                        fillSize()
                        updatePreview()
                    }
                }
                sizeRow.addView(chip, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(8) })
            }
        }

        // Montagem da janela
        box.addView(label(if (existing == null) "Novo botão" else "Editar botão", 20f, Color.WHITE, true))
        box.addView(hint("Veja como ele vai ficar:"))
        val previewHolder = LinearLayout(activity)
        previewHolder.gravity = Gravity.CENTER
        previewHolder.setPadding(0, dp(10), 0, dp(4))
        previewHolder.addView(preview)
        box.addView(previewHolder, LinearLayout.LayoutParams(MATCH, WRAP))

        box.addView(section("Nome do botão"))
        box.addView(labelInput)
        box.addView(hint("Opcional. Pode usar emoji ou símbolos (⏻ ▲ 🔊). Sem nome, o botão fica só com a cor."))

        box.addView(section("Cor"))
        box.addView(swatches(color) {
            color = it
            updatePreview()
        })

        box.addView(section("Tamanho"))
        box.addView(sizeRow)

        box.addView(section("O que este botão envia"))
        box.addView(label("Código do comando", 13f, Color.WHITE, true))
        box.addView(cmdInput)
        box.addView(hint("2 dígitos hexadecimais, de 00 a FF, como na tabela do seu controle."))
        box.addView(label("Endereço", 13f, Color.WHITE, true).apply { setPadding(0, dp(10), 0, 0) })
        box.addView(addrField)

        val test = actionButton("▶  Testar agora", Color.parseColor("#2E9E5B"), Color.WHITE)
        box.addView(test, LinearLayout.LayoutParams(MATCH, dp(46)).apply { topMargin = dp(12) })
        box.addView(hint("Aponte o topo do celular para o aparelho e toque em Testar."))
        box.addView(repeatBox, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) })

        val buttons = LinearLayout(activity)
        buttons.setPadding(0, dp(16), 0, dp(4))
        val delete = actionButton("Apagar", Color.parseColor("#7A1F1F"), Color.WHITE)
        val cancel = actionButton("Cancelar", fieldColor, Color.WHITE)
        val save = actionButton("OK", primary, Color.WHITE)
        if (existing != null) {
            buttons.addView(delete, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(4) })
        }
        buttons.addView(cancel, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        buttons.addView(save, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(4) })
        box.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP))

        labelInput.onChange { updatePreview() }
        updatePreview()
        fillSize()

        val dlg = AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(sv)
            .create()
        dlg.window?.setBackgroundDrawable(rounded(cardColor, 22))
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        test.setOnClickListener {
            val cmd = parseHex(cmdInput.text.toString(), 0xFF)
            val ad = parseHex(addrField.text.toString(), 0xFFFF)
            if (cmd == null || ad == null) {
                toast("Preencha o comando (00–FF) e o endereço (ex.: BF00) para testar.")
            } else {
                onTest(ad, cmd)
            }
        }
        cancel.setOnClickListener { dlg.dismiss() }
        delete.setOnClickListener {
            remote.buttons.remove(existing)
            renderGrid()
            dlg.dismiss()
        }
        save.setOnClickListener {
            val cmd = parseHex(cmdInput.text.toString(), 0xFF)
            val ad = parseHex(addrField.text.toString(), 0xFFFF)
            if (cmd == null) {
                toast("Código do comando inválido. Use 2 dígitos de 00 a FF, ex.: 03")
                return@setOnClickListener
            }
            if (ad == null) {
                toast("Endereço inválido. Use até 4 dígitos, ex.: BF00")
                return@setOnClickListener
            }
            val nb = CustomButton(labelInput.text.toString().trim(), cmd, ad, color, row, col, span, repeatBox.isChecked)
            if (existing != null) remote.buttons.remove(existing)
            remote.buttons.add(nb)
            renderGrid()
            dlg.dismiss()
        }
        dlg.show()
    }
}
