package com.guardians.app.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.guardians.app.data.SettingsRepository
import com.guardians.app.data.tr
import com.guardians.app.model.TimerType

/**
 * Mostra il popup in sovraimpressione (a schermo intero) quando un timer scatta.
 * Non blocca davvero l'app a livello di sistema: copre lo schermo e comunica
 * la chiusura, mentre il servizio rimanda alla home.
 */
object OverlayManager {

    private const val AUTO_HIDE_MS = 30_000L

    private val handler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var appContext: Context? = null
    private val autoHide = Runnable { hideInternal() }

    fun show(
        context: Context,
        type: TimerType,
        title: String,
        message: String,
        snowflake: Boolean = false,
    ) {
        val app = context.applicationContext
        appContext = app
        vibrateIfEnabled(app)
        playSoundIfEnabled(app)
        if (!Settings.canDrawOverlays(app)) return

        handler.post {
            hideInternal()
            val windowManager =
                app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val root = buildView(app, type, title, message, snowflake)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            try {
                windowManager.addView(root, params)
                currentView = root
                handler.removeCallbacks(autoHide)
                handler.postDelayed(autoHide, AUTO_HIDE_MS)
            } catch (_: Exception) {
            }
        }
    }

    fun hide() {
        handler.post { hideInternal() }
    }

    /**
     * Schermata del pedaggio dell'Esattore: copre l'app per [durationMs] con un
     * countdown di respiro. Alla fine "ENTRA" si sblocca e si può proseguire;
     * "LASCIA PERDERE" chiude subito, torna alla schermata home e avvisa il
     * servizio tramite [onGiveUp] (così il pedaggio si ripresenta al prossimo
     * tentativo, senza sconti).
     */
    fun showToll(
        context: Context,
        type: TimerType,
        title: String,
        message: String,
        durationMs: Long,
        onGiveUp: (() -> Unit)? = null,
    ) {
        val app = context.applicationContext
        appContext = app
        vibrateIfEnabled(app)
        playSoundIfEnabled(app)
        if (!Settings.canDrawOverlays(app)) return

        handler.post {
            hideInternal()
            val windowManager =
                app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val root = buildTollView(app, type, title, message, durationMs, onGiveUp)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            try {
                windowManager.addView(root, params)
                currentView = root
                // Rete di sicurezza: se l'utente non tocca nulla, sparisce da solo
                // un minuto dopo la fine del pedaggio.
                handler.removeCallbacks(autoHide)
                handler.postDelayed(autoHide, durationMs + 60_000L)
            } catch (_: Exception) {
            }
        }
    }

    /** Torna alla schermata home del telefono (usato da "lascia perdere"). */
    private fun goHome() {
        try {
            appContext?.startActivity(
                android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_HOME)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Suono di avviso quando scatta un guardiano (disattivabile nelle impostazioni).
     * Usa il canale audio delle notifiche: così rispetta "Non disturbare" e non
     * copre sveglie o avvisi di emergenza del sistema.
     */
    private fun playSoundIfEnabled(context: Context) {
        SettingsRepository.load(context)
        if (!SettingsRepository.soundOnAlert.value) return
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
        } catch (_: Exception) {
        }
    }

    /** Breve vibrazione quando scatta un guardiano (disattivabile nelle impostazioni). */
    private fun vibrateIfEnabled(context: Context) {
        SettingsRepository.load(context)
        if (!SettingsRepository.vibrateOnAlert.value) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(
                VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (_: Exception) {
        }
    }

    private fun hideInternal() {
        val view = currentView ?: return
        currentView = null
        handler.removeCallbacks(autoHide)
        val windowManager =
            appContext?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
    }

    // -------------------------------------------------------------- UI views

    private fun buildView(
        context: Context,
        type: TimerType,
        title: String,
        message: String,
        snowflake: Boolean = false,
    ): View {
        fun dp(value: Int): Int =
            (value * context.resources.displayMetrics.density).toInt()

        val accent = type.colorArgb.toInt()

        val root = FrameLayout(context).apply {
            setBackgroundColor(0xF0070B14.toInt())
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(32), dp(28), dp(28))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xFF141B2E.toInt())
                setStroke(dp(1), 0x33FFFFFF)
            }
        }

        val shape: View = if (snowflake) SnowflakeView(context) else ShapeView(context, type)
        card.addView(shape, LinearLayout.LayoutParams(dp(96), dp(96)))

        val titleView = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        card.addView(titleView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(20) })

        val messageView = TextView(context).apply {
            text = message
            setTextColor(0xFFB7C0D8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
        }
        card.addView(messageView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        val button = Button(context).apply {
            text = tr("HO CAPITO", "GOT IT")
            setTextColor(0xFF10141F.toInt())
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = true
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(accent)
            }
            setPadding(dp(32), dp(12), dp(32), dp(12))
            setOnClickListener { hideInternal() }
        }
        card.addView(button, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(24) })

        root.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply {
            leftMargin = dp(24)
            rightMargin = dp(24)
        })

        return root
    }

    /** Come [buildView], ma con il countdown del pedaggio al posto di "HO CAPITO". */
    private fun buildTollView(
        context: Context,
        type: TimerType,
        title: String,
        message: String,
        durationMs: Long,
        onGiveUp: (() -> Unit)?,
    ): View {
        fun dp(value: Int): Int =
            (value * context.resources.displayMetrics.density).toInt()

        val accent = type.colorArgb.toInt()

        val root = FrameLayout(context).apply {
            setBackgroundColor(0xF0070B14.toInt())
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(32), dp(28), dp(28))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xFF141B2E.toInt())
                setStroke(dp(1), 0x33FFFFFF)
            }
        }

        card.addView(ShapeView(context, type), LinearLayout.LayoutParams(dp(96), dp(96)))

        val titleView = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        card.addView(titleView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(20) })

        val messageView = TextView(context).apply {
            text = message
            setTextColor(0xFFB7C0D8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
        }
        card.addView(messageView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        // Bottone principale: parte spento con il countdown, poi diventa "ENTRA".
        val enterButton = Button(context).apply {
            setTextColor(0xFF10141F.toInt())
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = true
            isEnabled = false
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xFF4A4F5C.toInt())
            }
            setPadding(dp(32), dp(12), dp(32), dp(12))
            setOnClickListener { hideInternal() }
        }
        card.addView(enterButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(24) })

        val giveUpButton = Button(context).apply {
            text = tr("LASCIA PERDERE", "NEVER MIND")
            setTextColor(0xFFB7C0D8.toInt())
            isAllCaps = true
            background = null
            setOnClickListener {
                hideInternal()
                goHome()
                onGiveUp?.invoke()
            }
        }
        card.addView(giveUpButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })

        // Countdown: aggiorna il bottone ogni secondo finché questa vista è mostrata.
        val readyAt = System.currentTimeMillis() + durationMs
        val ticker = object : Runnable {
            override fun run() {
                if (currentView !== root) return
                val leftSec = (readyAt - System.currentTimeMillis() + 999) / 1000L
                if (leftSec > 0L) {
                    enterButton.text = tr("Puoi entrare tra ", "You can enter in ") + "${leftSec}s"
                    handler.postDelayed(this, 250L)
                } else {
                    enterButton.text = tr("ENTRA", "ENTER")
                    enterButton.isEnabled = true
                    enterButton.background = GradientDrawable().apply {
                        cornerRadius = dp(24).toFloat()
                        setColor(accent)
                    }
                }
            }
        }
        handler.post(ticker)

        root.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply {
            leftMargin = dp(24)
            rightMargin = dp(24)
        })

        return root
    }

    /** Elmo del guardiano (PNG) o forma geometrica di riserva. */
    private class ShapeView(context: Context, private val type: TimerType) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = type.colorArgb.toInt()
            pathEffect = CornerPathEffect(18f)
        }
        private val bitmap: android.graphics.Bitmap? = guardianOverlayDrawable(type)?.let {
            try {
                android.graphics.BitmapFactory.decodeResource(context.resources, it)
            } catch (_: Throwable) {
                null
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            bitmap?.let {
                canvas.drawBitmap(it, null, android.graphics.RectF(0f, 0f, w, h), null)
                return
            }
            when (type) {
                TimerType.SENTINELLA -> {
                    val path = Path().apply {
                        moveTo(w / 2f, h * 0.05f)
                        lineTo(w * 0.97f, h * 0.93f)
                        lineTo(w * 0.03f, h * 0.93f)
                        close()
                    }
                    canvas.drawPath(path, paint)
                }

                TimerType.GUARDIANO -> canvas.drawRoundRect(
                    w * 0.08f, h * 0.08f, w * 0.92f, h * 0.92f,
                    w * 0.12f, w * 0.12f, paint
                )

                TimerType.CUSTODE -> canvas.drawCircle(w / 2f, h / 2f, w * 0.44f, paint)

                TimerType.GENDARME -> {
                    val path = Path().apply {
                        moveTo(w / 2f, h * 0.03f)
                        lineTo(w * 0.97f, h / 2f)
                        lineTo(w / 2f, h * 0.97f)
                        lineTo(w * 0.03f, h / 2f)
                        close()
                    }
                    canvas.drawPath(path, paint)
                }

                TimerType.VEDETTA -> {
                    val cx = w / 2f
                    val cy = h / 2f
                    val outer = w * 0.48f
                    val inner = w * 0.2f
                    val path = Path()
                    for (i in 0 until 10) {
                        val r = if (i % 2 == 0) outer else inner
                        val a = Math.toRadians((-90 + i * 36).toDouble())
                        val x = cx + (r * kotlin.math.cos(a)).toFloat()
                        val y = cy + (r * kotlin.math.sin(a)).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    canvas.drawPath(path, paint)
                }

                // Il Messaggero non usa il popup, ma il when dev'essere completo.
                TimerType.MESSAGGERO -> {
                    val cx = w / 2f
                    val cy = h / 2f
                    val r = w * 0.5f
                    val path = Path()
                    for (i in 0 until 5) {
                        val a = Math.toRadians((-90 + i * 72).toDouble())
                        val x = cx + (r * kotlin.math.cos(a)).toFloat()
                        val y = cy + (r * kotlin.math.sin(a)).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    canvas.drawPath(path, paint)
                }

                // Alba d'argento dell'Araldo: mezzo sole sull'orizzonte.
                TimerType.ARALDO -> {
                    val rect = android.graphics.RectF(w * 0.1f, h * 0.22f, w * 0.9f, h * 1.02f)
                    canvas.drawArc(rect, 180f, 180f, true, paint)
                    canvas.drawRoundRect(
                        0f, h * 0.7f, w, h * 0.83f, h * 0.065f, h * 0.065f, paint,
                    )
                }

                // Esagono bronzo dell'Esattore (schermata del pedaggio).
                TimerType.ESATTORE -> {
                    val cx = w / 2f
                    val cy = h / 2f
                    val r = w * 0.5f
                    val path = Path()
                    for (i in 0 until 6) {
                        val a = Math.toRadians((-90 + i * 60).toDouble())
                        val x = cx + (r * kotlin.math.cos(a)).toFloat()
                        val y = cy + (r * kotlin.math.sin(a)).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    canvas.drawPath(path, paint)
                }

                // Torre merlata del Castellano.
                TimerType.CASTELLANO -> {
                    val left = w * 0.14f
                    val right = w * 0.86f
                    val bottom = h * 0.92f
                    val battTop = h * 0.10f
                    val battBottom = h * 0.34f
                    val seg = (right - left) / 5f
                    val path = Path().apply {
                        moveTo(left, bottom)
                        lineTo(left, battTop)
                        lineTo(left + seg, battTop)
                        lineTo(left + seg, battBottom)
                        lineTo(left + 2 * seg, battBottom)
                        lineTo(left + 2 * seg, battTop)
                        lineTo(left + 3 * seg, battTop)
                        lineTo(left + 3 * seg, battBottom)
                        lineTo(left + 4 * seg, battBottom)
                        lineTo(left + 4 * seg, battTop)
                        lineTo(right, battTop)
                        lineTo(right, bottom)
                        close()
                    }
                    canvas.drawPath(path, paint)
                }
            }
        }
    }
}

/**
 * Fiocco di neve a 6 bracci per il popup del Congelamento (azzurro ghiaccio):
 * ogni braccio ha due rametti laterali a metà lunghezza.
 */
private class SnowflakeView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9BE7FF.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) * 0.46f
        paint.strokeWidth = minOf(width, height) * 0.055f
        for (i in 0 until 6) {
            val a = Math.toRadians((i * 60).toDouble())
            val dx = kotlin.math.cos(a).toFloat()
            val dy = kotlin.math.sin(a).toFloat()
            // Braccio principale.
            canvas.drawLine(cx, cy, cx + dx * r, cy + dy * r, paint)
            // Rametti a 45° dal punto a metà braccio.
            val midX = cx + dx * r * 0.55f
            val midY = cy + dy * r * 0.55f
            val twig = r * 0.28f
            for (sign in intArrayOf(-1, 1)) {
                val ta = a + sign * Math.toRadians(40.0)
                canvas.drawLine(
                    midX, midY,
                    midX + (kotlin.math.cos(ta) * twig).toFloat(),
                    midY + (kotlin.math.sin(ta) * twig).toFloat(),
                    paint,
                )
            }
        }
        // Piccolo nucleo centrale.
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, paint.strokeWidth * 0.9f, paint)
        paint.style = Paint.Style.STROKE
    }
}

/** Il PNG dell'elmo per il tipo (ora TUTTI i guardiani hanno il proprio). */
private fun guardianOverlayDrawable(type: TimerType): Int? = when (type) {
    TimerType.SENTINELLA -> com.guardians.app.R.drawable.guardian_sentinella
    TimerType.GUARDIANO -> com.guardians.app.R.drawable.guardian_guardiano
    TimerType.CUSTODE -> com.guardians.app.R.drawable.guardian_custode
    TimerType.GENDARME -> com.guardians.app.R.drawable.guardian_gendarme
    TimerType.VEDETTA -> com.guardians.app.R.drawable.guardian_vedetta
    TimerType.ESATTORE -> com.guardians.app.R.drawable.guardian_esattore
    TimerType.ARALDO -> com.guardians.app.R.drawable.guardian_araldo
    TimerType.MESSAGGERO -> com.guardians.app.R.drawable.guardian_messaggero
    TimerType.CASTELLANO -> null
}
