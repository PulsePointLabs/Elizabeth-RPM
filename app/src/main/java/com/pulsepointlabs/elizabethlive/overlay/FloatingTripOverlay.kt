package com.pulsepointlabs.elizabethlive.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.pulsepointlabs.elizabethlive.MainActivity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Compact native overlay modeled after Sarah Vital Signs' floating HR pill.
 * It contains only glanceable trip values and never invents unavailable measurements.
 */
class FloatingTripOverlay(
    private val context: Context,
    private val onClose: () -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var statusDot: TextView? = null
    private var averageText: TextView? = null
    private var liveText: TextView? = null
    private var costText: TextView? = null

    fun show() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(::show)
            return
        }
        if (!canDraw(context) || root != null) return

        val view = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(8), dp(6))
            background = cardBackground()
            elevation = dp(8).toFloat()
            contentDescription = "Elizabeth floating trip overlay"
        }

        statusDot = TextView(context).apply {
            text = "●"
            setTextColor(DISCONNECTED)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
        }

        averageText = TextView(context).apply {
            text = "--"
            setTextColor(Color.WHITE)
            textSize = 29f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            isSingleLine = true
        }
        val mpgText = TextView(context).apply {
            text = "AVG MPG"
            setTextColor(MUTED)
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        val averageColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(averageText, LinearLayout.LayoutParams(dp(68), dp(34)))
            addView(mpgText, LinearLayout.LayoutParams(dp(68), dp(12)))
        }

        val metricColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), 0, dp(2), 0)
        }
        liveText = metric("LIVE --")
        costText = metric("COST $0.00")
        metricColumn.addView(liveText, LinearLayout.LayoutParams(dp(72), dp(15)))
        metricColumn.addView(costText, LinearLayout.LayoutParams(dp(72), dp(15)))

        val closeText = TextView(context).apply {
            text = "×"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = closeBackground()
            contentDescription = "Hide floating trip overlay"
            setOnClickListener { onClose() }
        }

        view.addView(statusDot, LinearLayout.LayoutParams(dp(16), dp(26)))
        view.addView(averageColumn, LinearLayout.LayoutParams(dp(68), LinearLayout.LayoutParams.WRAP_CONTENT))
        view.addView(metricColumn, LinearLayout.LayoutParams(dp(78), LinearLayout.LayoutParams.WRAP_CONTENT))
        view.addView(closeText, LinearLayout.LayoutParams(dp(28), dp(28)))

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(120)
        }
        attachTouchHandling(view, lp)
        runCatching { windowManager.addView(view, lp) }.onFailure { return }
        root = view
        params = lp
    }

    fun update(metrics: TripOverlayMetrics) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { update(metrics) }
            return
        }
        if (!canDraw(context)) {
            hide()
            return
        }
        if (root == null) show()
        statusDot?.setTextColor(if (metrics.connected) CONNECTED else DISCONNECTED)
        averageText?.text = metrics.averageMpg?.formatOneDecimal() ?: "--"
        liveText?.text = "LIVE ${metrics.liveMpg?.formatOneDecimal() ?: "--"}"
        costText?.text = "COST $${String.format(Locale.US, "%.2f", metrics.tripCost)}"
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(::hide)
            return
        }
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        params = null
        statusDot = null
        averageText = null
        liveText = null
        costText = null
    }

    private fun attachTouchHandling(view: LinearLayout, lp: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val slop = dp(8)
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        val (x, y) = clampPosition(view, startX - dx.toInt(), startY + dy.toInt())
                        lp.x = x
                        lp.y = y
                        root?.let { runCatching { windowManager.updateViewLayout(it, lp) } }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) openApp()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    private fun clampPosition(view: View, desiredX: Int, desiredY: Int): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        val width = view.width.takeIf { it > 0 } ?: dp(190)
        val height = view.height.takeIf { it > 0 } ?: dp(52)
        val margin = dp(8)
        val top = statusBarHeight() + margin
        val bottom = navigationBarHeight() + margin
        val maxX = (metrics.widthPixels - width - margin).coerceAtLeast(margin)
        val maxY = (metrics.heightPixels - height - bottom).coerceAtLeast(top)
        val absoluteLeft = (metrics.widthPixels - desiredX - width).coerceIn(margin, maxX)
        return (metrics.widthPixels - absoluteLeft - width) to desiredY.coerceIn(top, maxY)
    }

    private fun openApp() {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun metric(value: String) = TextView(context).apply {
        text = value
        setTextColor(ACCENT_TEXT)
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        includeFontPadding = false
        isSingleLine = true
    }

    private fun cardBackground() = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.argb(240, 15, 23, 42), Color.argb(235, 42, 18, 42)),
    ).apply {
        cornerRadius = dp(16).toFloat()
        setStroke(dp(1), Color.argb(145, 251, 113, 133))
    }

    private fun closeBackground() = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.argb(92, 216, 180, 254))
        setStroke(dp(1), Color.argb(150, 253, 164, 175))
    }

    private fun Double.formatOneDecimal(): String = String.format(Locale.US, "%.1f", this)
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
    private fun statusBarHeight(): Int = systemDimension("status_bar_height").takeIf { it > 0 } ?: dp(24)
    private fun navigationBarHeight(): Int = systemDimension("navigation_bar_height").takeIf { it > 0 } ?: dp(24)
    private fun systemDimension(name: String): Int {
        val id = context.resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) context.resources.getDimension(id).roundToInt() else 0
    }

    companion object {
        private val CONNECTED = Color.rgb(74, 222, 128)
        private val DISCONNECTED = Color.rgb(148, 163, 184)
        private val MUTED = Color.rgb(148, 163, 184)
        private val ACCENT_TEXT = Color.rgb(253, 164, 175)

        fun canDraw(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

        fun permissionIntent(context: Context): Intent =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    }
}
