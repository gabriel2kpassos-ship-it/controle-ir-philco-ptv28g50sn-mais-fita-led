package com.philco.controle

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.hardware.ConsumerIrManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** LED vermelho que fica no topo do controle e acende quando um botão é apertado. */
class LedView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    var lit: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f
        val core = r * 0.34f

        if (lit) {
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(0xCCFF2A2A.toInt(), 0x00FF2A2A),
                floatArrayOf(0.15f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r, paint)
            paint.shader = null
            paint.color = 0xFFFF3030.toInt()
        } else {
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = 0xFF4A1212.toInt()
        }
        canvas.drawCircle(cx, cy, core, paint)

        // aro do LED
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = 0xFF0E0E10.toInt()
        canvas.drawCircle(cx, cy, core, paint)

        // brilho
        paint.style = Paint.Style.FILL
        paint.color = if (lit) 0xCCFFFFFF.toInt() else 0x33FFFFFF
        canvas.drawCircle(cx - core * 0.3f, cy - core * 0.3f, core * 0.25f, paint)
    }
}

/**
 * Controles infravermelhos (protocolo NEC, 38 kHz):
 *  - TV Philco PTV28G50SN (controle AD1484), endereço 0xBF00
 *  - Fita LED de 24 teclas, endereço 0xEF00
 * Cada toque: vibra, toca um clique e acende o LED vermelho do controle.
 */
class MainActivity : Activity() {

    private var ir: ConsumerIrManager? = null
    private var vibrator: Vibrator? = null
    private var click: AudioTrack? = null
    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    private lateinit var tvLed: LedView
    private lateinit var ledLed: LedView
    private lateinit var activeLed: LedView
    private val ledOffRunnable = Runnable { activeLed.lit = false }

    private lateinit var root: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var content: FrameLayout
    private lateinit var tabStrip: LinearLayout
    private lateinit var tvPage: View
    private lateinit var ledPage: View

    // Controles criados pelo usuário (abas 2, 3, 4...)
    private val customRemotes = mutableListOf<CustomRemote>()
    private val customPages = mutableListOf<View>()
    private val customLeds = mutableListOf<LedView>()
    private val tabViews = mutableListOf<TextView>()
    private var currentTab = 0

    /** Endereço NEC usado pelos botões criados no momento. */
    private var currentAddr = 0xBF00

    // ---- Comandos da TV (endereço 0xBF00) ----
    private object Cmd {
        const val POWER = 0x03
        const val MUTE = 0x15
        const val N1 = 0x00; const val N2 = 0x01; const val N3 = 0x02
        const val N4 = 0x04; const val N5 = 0x05; const val N6 = 0x06
        const val N7 = 0x08; const val N8 = 0x09; const val N9 = 0x0A
        const val N0 = 0x0D
        const val INFO = 0x46
        const val DOT = 0x0C
        const val RED = 0x48; const val GREEN = 0x49
        const val YELLOW = 0x4A; const val BLUE = 0x4C
        const val QMENU = 0x60
        const val BACK = 0x0E
        const val UP = 0x1D; const val DOWN = 0x45
        const val LEFT = 0x40; const val RIGHT = 0x42
        const val OK = 0x41
        const val MENU = 0x1C
        const val GUIDE = 0x59
        const val USB = 0x56
        const val EXIT = 0x44
        const val VOL_UP = 0x16; const val VOL_DOWN = 0x1A
        const val CH_UP = 0x14; const val CH_DOWN = 0x18
        const val SOURCE = 0x1E
        const val LIST = 0x57
        const val PLAY = 0x52; const val PAUSE = 0x4F
        const val STOP = 0x53; const val REC = 0x5A
        const val REW = 0x4D; const val PREV = 0x50
        const val NEXT = 0x51; const val FF = 0x4E
        const val NETFLIX = 0x43
        const val YOUTUBE = 0x65
        const val GLOBOPLAY = 0x25
        const val PRIME = 0x24
    }

    private val bodyColor = Color.parseColor("#1B1B1D")
    private val btnColor = Color.parseColor("#34353A")
    private val ringColor = Color.parseColor("#2A2B2F")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ir = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        @Suppress("DEPRECATION")
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        click = buildClickTrack()
        if (ir == null || ir?.hasIrEmitter() != true) {
            Toast.makeText(this, "Este celular não tem emissor infravermelho.", Toast.LENGTH_LONG).show()
        }
        setContentView(buildUi())
    }

    override fun onPause() {
        super.onPause()
        stopRepeat()
        allLedsOff()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            click?.release()
        } catch (_: Exception) {
        }
        click = null
    }

    // ------------------------------------------------------------------
    // Envio IR (NEC)
    // ------------------------------------------------------------------

    private fun necFrame(address: Int, command: Int): IntArray {
        val cmd = command and 0xFF
        val raw: Long = ((cmd.inv() and 0xFF).toLong() shl 24) or
                (cmd.toLong() shl 16) or
                (address.toLong() and 0xFFFF)
        val p = ArrayList<Int>(70)
        p.add(9000); p.add(4500)
        for (i in 0 until 32) {
            p.add(560)
            p.add(if (((raw shr i) and 1L) == 1L) 1690 else 560)
        }
        p.add(560)
        p.add(40000)
        return p.toIntArray()
    }

    private val repeatFrame = intArrayOf(9000, 2250, 560, 96000)

    private fun send(pattern: IntArray) {
        try {
            val m = ir
            if (m != null && m.hasIrEmitter()) m.transmit(38000, pattern)
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao enviar IR: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRepeat() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
    }

    // ------------------------------------------------------------------
    // Feedback: vibração, som de clique e LED
    // ------------------------------------------------------------------

    private fun vibrate() {
        try {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(30)
            }
        } catch (_: Exception) {
        }
    }

    private fun buildClickTrack(): AudioTrack? {
        return try {
            val rate = 44100
            val n = rate * 40 / 1000
            val data = ShortArray(n) { i ->
                val t = i / rate.toDouble()
                val env = exp(-t * 130.0)
                (sin(2.0 * PI * 1800.0 * t) * env * 0.85 * Short.MAX_VALUE).toInt().toShort()
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(data, 0, data.size)
            track
        } catch (e: Exception) {
            null
        }
    }

    private fun playClick() {
        try {
            val t = click ?: return
            t.stop()
            t.reloadStaticData()
            t.play()
        } catch (_: Exception) {
        }
    }

    private fun ledOn() {
        handler.removeCallbacks(ledOffRunnable)
        activeLed.lit = true
    }

    private fun ledOffSoon() {
        handler.removeCallbacks(ledOffRunnable)
        handler.postDelayed(ledOffRunnable, 150)
    }

    // ------------------------------------------------------------------
    // Helpers de interface
    // ------------------------------------------------------------------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun shift(c: Int, d: Int): Int {
        val r = (Color.red(c) + d).coerceIn(0, 255)
        val g = (Color.green(c) + d).coerceIn(0, 255)
        val b = (Color.blue(c) + d).coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun lighten(c: Int): Int = shift(c, 60)

    private fun bg(color: Int, radiusDp: Int): StateListDrawable {
        fun shape(c: Int) = GradientDrawable().apply {
            setColor(c)
            cornerRadius = dp(radiusDp).toFloat()
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(lighten(color)))
            addState(intArrayOf(), shape(color))
        }
    }

    /** Botão redondo com brilho, no estilo do controle de fita LED. */
    private fun glossy(color: Int): StateListDrawable {
        val normal = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(shift(color, 35), shift(color, -35))
        ).apply { shape = GradientDrawable.OVAL }
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(shift(color, 70))
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    /** Cria um botão que transmite [cmd]. Com [repeat] ele repete enquanto estiver pressionado. */
    private fun key(
        label: String,
        cmd: Int,
        color: Int = btnColor,
        textColor: Int = Color.WHITE,
        radius: Int = 22,
        sizeSp: Float = 15f,
        repeat: Boolean = false,
        bold: Boolean = true,
        background: Drawable? = null,
        addr: Int = currentAddr
    ): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.setTextColor(textColor)
        tv.textSize = sizeSp
        tv.gravity = Gravity.CENTER
        tv.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        tv.background = background ?: bg(color, radius)
        tv.isClickable = true
        tv.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    // impede o ScrollView de "roubar" o toque e cancelar o botão segurado
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    vibrate()
                    playClick()
                    ledOn()
                    send(necFrame(addr, cmd))
                    if (repeat) {
                        stopRepeat()
                        val r = object : Runnable {
                            override fun run() {
                                // reenvia o comando completo enquanto o botão estiver pressionado
                                send(necFrame(addr, cmd))
                                handler.postDelayed(this, 60)
                            }
                        }
                        repeatRunnable = r
                        handler.postDelayed(r, 350)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    stopRepeat()
                    ledOffSoon()
                    true
                }
                else -> true
            }
        }
        return tv
    }

    private fun lp(w: Int, h: Int, weight: Float = 0f, m: Int = 4): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight).apply { setMargins(dp(m), dp(m), dp(m), dp(m)) }

    /** Linha horizontal; cada filho ocupa partes iguais. */
    private fun rowOf(heightDp: Int, vararg views: View): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        for (v in views) row.addView(v, lp(0, dp(heightDp), 1f))
        return row
    }

    private fun spacer(): View = View(this)

    private fun label(text: String, sizeSp: Float = 11f, color: Int = Color.parseColor("#9A9AA0")): TextView =
        TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            gravity = Gravity.CENTER
        }

    private fun ledLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(4)
        }

    private fun wrapBody(body: View): View {
        val holder = FrameLayout(this)
        holder.setPadding(dp(12), dp(8), dp(12), dp(12))
        holder.addView(
            body,
            FrameLayout.LayoutParams(dp(330), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL)
        )
        return holder
    }

    // ------------------------------------------------------------------
    // Tela principal com abas
    // ------------------------------------------------------------------

    private fun tab(text: String, index: Int): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(16), 0, dp(16), 0)
            isClickable = true
            setOnClickListener { setTab(index) }
        }

    private fun styleTab(tv: TextView, selected: Boolean) {
        tv.setTextColor(if (selected) Color.WHITE else Color.parseColor("#B8BAC4"))
        tv.background = if (selected) {
            GradientDrawable().apply {
                setColor(Color.parseColor("#4A6CF7"))
                cornerRadius = dp(20).toFloat()
            }
        } else ColorDrawable(Color.TRANSPARENT)
    }

    private fun allLedsOff() {
        handler.removeCallbacks(ledOffRunnable)
        tvLed.lit = false
        ledLed.lit = false
        for (l in customLeds) l.lit = false
    }

    /** Monta de novo a barra de abas: TV, Fita LED, controles criados e o botão "＋ Novo". */
    private fun rebuildTabs() {
        tabStrip.removeAllViews()
        tabViews.clear()
        fun add(t: TextView) {
            tabViews.add(t)
            tabStrip.addView(t, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)))
        }
        add(tab("TV Philco", 0))
        add(tab("Fita LED", 1))
        customRemotes.forEachIndexed { i, r ->
            add(tab(r.name, 2 + i).apply {
                setOnLongClickListener {
                    showRemoteMenu(i)
                    true
                }
            })
        }
        val plus = TextView(this).apply {
            text = "＋ Novo"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(16), 0, dp(16), 0)
            setTextColor(Color.parseColor("#8FA8FF"))
            isClickable = true
            setOnClickListener { openCreator(null) }
        }
        tabStrip.addView(plus, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)))
    }

    private fun rebuildCustomPages() {
        customLeds.clear()
        customPages.clear()
        for (r in customRemotes) {
            val led = LedView(this)
            customLeds.add(led)
            customPages.add(wrapBody(buildCustomBody(r, led)))
        }
    }

    private fun setTab(index: Int) {
        currentTab = index
        allLedsOff()
        stopRepeat()
        content.removeAllViews()
        val ci = index - 2
        when {
            index == 0 -> {
                content.addView(tvPage)
                activeLed = tvLed
                root.background = ColorDrawable(Color.BLACK)
            }
            index == 1 -> {
                content.addView(ledPage)
                activeLed = ledLed
                root.background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.parseColor("#1E3A8A"), Color.parseColor("#1FB5C8"))
                )
            }
            ci in customRemotes.indices -> {
                content.addView(customPages[ci])
                activeLed = customLeds[ci]
                root.background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(shift(customRemotes[ci].accent, -90), Color.BLACK)
                )
            }
            else -> {
                setTab(0)
                return
            }
        }
        for (i in tabViews.indices) styleTab(tabViews[i], i == index)
        scroll.scrollTo(0, 0)
    }

    private fun buildUi(): View {
        tvLed = LedView(this)
        ledLed = LedView(this)
        activeLed = tvLed

        tvPage = wrapBody(buildTvBody())
        ledPage = wrapBody(buildLedBody())

        customRemotes.clear()
        customRemotes.addAll(RemoteStore.load(this))
        rebuildCustomPages()

        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        tabStrip = LinearLayout(this)
        tabStrip.orientation = LinearLayout.HORIZONTAL
        val tabScroll = HorizontalScrollView(this)
        tabScroll.isHorizontalScrollBarEnabled = false
        tabScroll.addView(
            tabStrip,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        )
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(dp(4), dp(4), dp(4), dp(4))
        bar.background = GradientDrawable().apply {
            setColor(Color.parseColor("#CC1E1E24"))
            cornerRadius = dp(24).toFloat()
        }
        bar.addView(
            tabScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            bar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(10), dp(16), dp(4)) }
        )

        content = FrameLayout(this)
        scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        rebuildTabs()
        setTab(0)
        return root
    }

    // ------------------------------------------------------------------
    // Controles criados pelo usuário
    // ------------------------------------------------------------------

    private fun buildCustomBody(r: CustomRemote, led: LedView): View {
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(14), dp(10), dp(14), dp(18))
        body.background = GradientDrawable().apply {
            setColor(bodyColor)
            cornerRadius = dp(30).toFloat()
            setStroke(dp(2), r.accent)
        }
        body.addView(led, ledLp())

        for (row in 0 until r.rows) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            var c = 0
            while (c < CustomRemote.COLS) {
                val b = r.buttonAt(row, c)
                if (b != null && b.col == c) {
                    line.addView(
                        key(
                            b.label, b.cmd,
                            color = b.color,
                            textColor = readableOn(b.color),
                            radius = 18,
                            sizeSp = if (b.label.length > 6) 11f else 16f,
                            repeat = b.repeat,
                            addr = b.addr
                        ),
                        lp(0, dp(60), b.span.toFloat())
                    )
                    c += b.span
                } else {
                    line.addView(spacer(), lp(0, dp(60), 1f))
                    c += 1
                }
            }
            body.addView(line)
        }

        body.addView(label(r.name, 10f).apply { setPadding(0, dp(10), 0, 0) })
        return body
    }

    /** Abre o assistente: [editIndex] = null cria um controle novo; senão edita o controle dessa posição. */
    private fun openCreator(editIndex: Int?) {
        val working = if (editIndex == null) {
            CustomRemote(System.currentTimeMillis().toString(), "", Palette.COLORS[6], -1, 5)
        } else {
            customRemotes[editIndex].deepCopy()
        }
        RemoteEditor(
            this,
            working,
            editIndex == null,
            { saved ->
                if (editIndex == null) customRemotes.add(saved) else customRemotes[editIndex] = saved
                RemoteStore.save(this, customRemotes)
                rebuildCustomPages()
                rebuildTabs()
                setTab(if (editIndex == null) 1 + customRemotes.size else 2 + editIndex)
            },
            { addr, cmd -> testSend(addr, cmd) }
        ).show()
    }

    /** Envio de teste feito pelo assistente (com o mesmo feedback dos botões). */
    private fun testSend(addr: Int, cmd: Int) {
        vibrate()
        playClick()
        send(necFrame(addr, cmd))
    }

    private fun showRemoteMenu(i: Int) {
        val r = customRemotes[i]
        AlertDialog.Builder(this)
            .setTitle(r.name)
            .setItems(arrayOf("✏️  Editar controle", "🗑️  Excluir controle")) { _, which ->
                if (which == 0) openCreator(i) else confirmDelete(i)
            }
            .show()
    }

    private fun confirmDelete(i: Int) {
        val r = customRemotes[i]
        AlertDialog.Builder(this)
            .setTitle("Excluir \"${r.name}\"?")
            .setMessage("Os botões deste controle serão apagados.")
            .setPositiveButton("Excluir") { _, _ ->
                customRemotes.removeAt(i)
                RemoteStore.save(this, customRemotes)
                rebuildCustomPages()
                rebuildTabs()
                setTab(0)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ------------------------------------------------------------------
    // Controle da TV (AD1484)
    // ------------------------------------------------------------------

    private fun buildTvBody(): View {
        currentAddr = 0xBF00

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(14), dp(10), dp(14), dp(22))
        body.background = GradientDrawable().apply {
            setColor(bodyColor)
            cornerRadius = dp(34).toFloat()
        }

        body.addView(tvLed, ledLp())

        // POWER (vermelho) e MUTE
        body.addView(
            rowOf(
                46,
                key("⏻", Cmd.POWER, color = Color.parseColor("#D32F2F"), radius = 23, sizeSp = 20f),
                spacer(),
                spacer(),
                key("🔇", Cmd.MUTE, radius = 23, sizeSp = 18f)
            )
        )

        // 1 2 3 / 4 5 6 / 7 8 9
        val nums = listOf(
            "1" to Cmd.N1, "2" to Cmd.N2, "3" to Cmd.N3,
            "4" to Cmd.N4, "5" to Cmd.N5, "6" to Cmd.N6,
            "7" to Cmd.N7, "8" to Cmd.N8, "9" to Cmd.N9
        )
        for (i in 0 until 3) {
            body.addView(
                rowOf(
                    42,
                    *Array(3) { j ->
                        val (t, c) = nums[i * 3 + j]
                        key(t, c, radius = 12, sizeSp = 17f)
                    }
                )
            )
        }
        // INFO 0 •
        body.addView(
            rowOf(
                42,
                key("INFO", Cmd.INFO, radius = 12, sizeSp = 13f),
                key("0", Cmd.N0, radius = 12, sizeSp = 17f),
                key("•", Cmd.DOT, radius = 12, sizeSp = 20f)
            )
        )

        // Coloridos
        body.addView(
            rowOf(
                26,
                key("", Cmd.RED, color = Color.parseColor("#E53935"), radius = 8),
                key("", Cmd.GREEN, color = Color.parseColor("#2E9E5B"), radius = 8),
                key("", Cmd.YELLOW, color = Color.parseColor("#F2C811"), radius = 8),
                key("", Cmd.BLUE, color = Color.parseColor("#1E73D8"), radius = 8)
            )
        )

        // QMENU ........ BACK
        body.addView(
            rowOf(
                34,
                key("QMENU", Cmd.QMENU, radius = 17, sizeSp = 10f),
                spacer(),
                spacer(),
                key("↩", Cmd.BACK, radius = 17, sizeSp = 17f)
            )
        )

        // Anel de navegação com OK no centro
        body.addView(
            buildDpad(),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(236))
                .apply { setMargins(0, dp(4), 0, dp(4)) }
        )

        // MENU GUIDE USB EXIT
        body.addView(
            rowOf(
                30,
                key("MENU", Cmd.MENU, radius = 15, sizeSp = 10f),
                key("GUIDE", Cmd.GUIDE, radius = 15, sizeSp = 10f),
                key("USB", Cmd.USB, radius = 15, sizeSp = 10f),
                key("EXIT", Cmd.EXIT, radius = 15, sizeSp = 10f)
            )
        )

        // VOL | SOURCE/LIST | CH
        body.addView(
            buildVolChRow(),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(176))
                .apply { setMargins(0, dp(6), 0, dp(6)) }
        )

        // PLAY PAUSE STOP REC
        body.addView(
            rowOf(
                38,
                key("▶", Cmd.PLAY, radius = 8, sizeSp = 16f),
                key("❚❚", Cmd.PAUSE, radius = 8, sizeSp = 13f),
                key("■", Cmd.STOP, radius = 8, sizeSp = 16f),
                key("●", Cmd.REC, radius = 8, sizeSp = 16f, textColor = Color.parseColor("#E53935"))
            )
        )
        // REW PREV NEXT FF
        body.addView(
            rowOf(
                38,
                key("⏪", Cmd.REW, radius = 8, sizeSp = 16f),
                key("⏮", Cmd.PREV, radius = 8, sizeSp = 16f),
                key("⏭", Cmd.NEXT, radius = 8, sizeSp = 16f),
                key("⏩", Cmd.FF, radius = 8, sizeSp = 16f)
            )
        )

        // NETFLIX / YouTube
        body.addView(
            rowOf(
                36,
                key("NETFLIX", Cmd.NETFLIX, color = Color.WHITE, textColor = Color.parseColor("#E50914"), radius = 6, sizeSp = 13f),
                key("YouTube", Cmd.YOUTUBE, color = Color.WHITE, textColor = Color.parseColor("#D00000"), radius = 6, sizeSp = 13f)
            )
        )
        // GLOBOPLAY / (botão preto sem marca = Prime Video)
        body.addView(
            rowOf(
                36,
                key("GLOBOPLAY", Cmd.GLOBOPLAY, color = Color.parseColor("#E8205A"), radius = 6, sizeSp = 11f),
                key("prime video", Cmd.PRIME, color = Color.parseColor("#0A0A0B"), textColor = Color.parseColor("#6E6E75"), radius = 6, sizeSp = 11f)
            )
        )

        body.addView(label("AD1484  •  Philco PTV28G50SN", 10f).apply { setPadding(0, dp(10), 0, 0) })
        return body
    }

    private fun buildDpad(): View {
        val ring = LinearLayout(this)
        ring.orientation = LinearLayout.VERTICAL
        ring.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ringColor)
        }
        ring.setPadding(dp(8), dp(8), dp(8), dp(8))

        fun arrow(t: String, c: Int) =
            key(t, c, color = Color.TRANSPARENT, radius = 40, sizeSp = 22f, repeat = true)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(spacer(), lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f, 0))
            addView(arrow("▲", Cmd.UP), lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f, 0))
            addView(spacer(), lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f, 0))
        }
        val mid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(arrow("◀", Cmd.LEFT), lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f, 0))
            addView(
                key("OK", Cmd.OK, color = Color.parseColor("#3C3D43"), radius = 60, sizeSp = 22f),
                lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.25f, 2)
            )
            addView(arrow("▶", Cmd.RIGHT), lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f, 0))
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(spacer(), lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f, 0))
            addView(arrow("▼", Cmd.DOWN), lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f, 0))
            addView(spacer(), lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f, 0))
        }
        ring.addView(top, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        ring.addView(mid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.25f))
        ring.addView(bottom, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val wrap = FrameLayout(this)
        wrap.addView(ring, FrameLayout.LayoutParams(dp(236), dp(236), Gravity.CENTER))
        return wrap
    }

    private fun pill(top: TextView, mid: View, bottom: TextView): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL
        p.background = bg(btnColor, 30)
        p.addView(top, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        p.addView(mid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(24)))
        p.addView(bottom, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return p
    }

    private fun buildVolChRow(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL

        val vol = pill(
            key("+", Cmd.VOL_UP, color = Color.TRANSPARENT, radius = 30, sizeSp = 26f, repeat = true),
            label("🔈", 16f, Color.WHITE),
            key("−", Cmd.VOL_DOWN, color = Color.TRANSPARENT, radius = 30, sizeSp = 26f, repeat = true)
        )

        val center = LinearLayout(this)
        center.orientation = LinearLayout.VERTICAL
        center.gravity = Gravity.CENTER
        center.addView(label("SOURCE", 9f), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(16)))
        center.addView(
            key("⌂", Cmd.SOURCE, radius = 26, sizeSp = 22f),
            LinearLayout.LayoutParams(dp(52), dp(52)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(2) }
        )
        center.addView(spacer(), LinearLayout.LayoutParams(1, dp(16)))
        center.addView(
            key("LIST", Cmd.LIST, radius = 20, sizeSp = 11f),
            LinearLayout.LayoutParams(dp(52), dp(40)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        )

        val ch = pill(
            key("∧", Cmd.CH_UP, color = Color.TRANSPARENT, radius = 30, sizeSp = 22f, repeat = true),
            label("P", 16f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD },
            key("∨", Cmd.CH_DOWN, color = Color.TRANSPARENT, radius = 30, sizeSp = 22f, repeat = true)
        )

        row.addView(vol, lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f, 4))
        row.addView(center, lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f, 4))
        row.addView(ch, lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f, 4))
        return row
    }

    // ------------------------------------------------------------------
    // Controle da fita LED (24 teclas, NEC 0xEF00)
    // ------------------------------------------------------------------

    private class LedBtn(
        val label: String,
        val cmd: Int,
        val color: Int,
        val text: Int = Color.WHITE,
        val size: Float = 14f,
        val repeat: Boolean = false
    )

    private fun buildLedBody(): View {
        currentAddr = 0xEF00

        val gray = Color.parseColor("#7E8A93")
        val grayText = Color.parseColor("#EDEFF0")
        val white = Color.parseColor("#F4F4F6")
        val dark = Color.parseColor("#1C1C1E")

        val buttons = listOf(
            LedBtn("☀\uFE0E", 0x00, white, dark, 26f, true),
            LedBtn("☼", 0x01, white, dark, 20f, true),
            LedBtn("OFF", 0x02, dark, Color.WHITE, 17f),
            LedBtn("ON", 0x03, Color.parseColor("#E8404B"), Color.WHITE, 17f),

            LedBtn("R", 0x04, Color.parseColor("#E8404B"), Color.WHITE, 18f),
            LedBtn("G", 0x05, Color.parseColor("#4CA567"), Color.WHITE, 18f),
            LedBtn("B", 0x06, Color.parseColor("#2F6DB5"), Color.WHITE, 18f),
            LedBtn("W", 0x07, white, dark, 18f),

            LedBtn("", 0x08, Color.parseColor("#F0A860")),
            LedBtn("", 0x09, Color.parseColor("#7CC47C")),
            LedBtn("", 0x0A, Color.parseColor("#5B9BD5")),
            LedBtn("FLASH", 0x0B, gray, grayText, 11f),

            LedBtn("", 0x0C, Color.parseColor("#F08A50")),
            LedBtn("", 0x0D, Color.parseColor("#86D3C2")),
            LedBtn("", 0x0E, Color.parseColor("#6F5DB0")),
            LedBtn("STROBE", 0x0F, gray, grayText, 11f),

            LedBtn("", 0x10, Color.parseColor("#F5C242")),
            LedBtn("", 0x11, Color.parseColor("#2FA9A3")),
            LedBtn("", 0x12, Color.parseColor("#A363B3")),
            LedBtn("FADE", 0x13, gray, grayText, 11f),

            LedBtn("", 0x14, Color.parseColor("#FFF53A")),
            LedBtn("", 0x15, Color.parseColor("#2E8A98")),
            LedBtn("", 0x16, Color.parseColor("#E58BC0")),
            LedBtn("SMOOTH", 0x17, gray, grayText, 10f)
        )

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(14), dp(10), dp(14), dp(18))
        body.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#9BB0CC"), Color.parseColor("#9ACFDA"))
        ).apply {
            cornerRadius = dp(30).toFloat()
            setStroke(dp(2), Color.parseColor("#CCFFFFFF"))
        }

        body.addView(ledLed, ledLp())

        for (r in 0 until 6) {
            val views = Array(4) { c ->
                val b = buttons[r * 4 + c]
                key(
                    b.label, b.cmd,
                    textColor = b.text,
                    sizeSp = b.size,
                    repeat = b.repeat,
                    background = glossy(b.color)
                )
            }
            body.addView(rowOf(67, *views))
        }

        body.addView(label("Fita LED  •  24 teclas", 10f, Color.parseColor("#E8F1F5")).apply {
            setPadding(0, dp(8), 0, 0)
        })
        return body
    }
}
