package com.andreccardosso.roletaanalyzer

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.Context
import android.content.SharedPreferences
import android.view.ViewGroup

class MainActivity : Activity() {
    private lateinit var browser: WebView
    private lateinit var address: EditText
    private lateinit var toolbar: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var panelHeader: TextView
    private lateinit var panelBody: LinearLayout
    private lateinit var resultText: TextView
    private lateinit var historyText: TextView
    private lateinit var prefs: SharedPreferences
    private val wheel = intArrayOf(0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26)
    private val offsets = intArrayOf(0,5,9,14,19,23,28,32)
    private val labels = arrayOf("+1","X1","+2","X2","+3","X3","+4","X4")
    private val history = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()
        prefs = getSharedPreferences("y_analyzer", Context.MODE_PRIVATE)
        history.addAll(prefs.getString("history", "")!!.split(",").mapNotNull { it.toIntOrNull() }.take(100))
        buildScreen()
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    private fun buildScreen() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        browser = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.loadsImagesAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!url.isNullOrBlank() && url != "about:blank") address.setText(url)
                }
            }
        }
        root.addView(browser, FrameLayout.LayoutParams(-1, -1))

        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(5, 4, 5, 4)
            setBackgroundColor(Color.rgb(20, 30, 40))
        }
        toolbar.addView(smallButton("‹") { if (browser.canGoBack()) browser.goBack() }, LinearLayout.LayoutParams(42, 42))
        toolbar.addView(smallButton("›") { if (browser.canGoForward()) browser.goForward() }, LinearLayout.LayoutParams(42, 42))
        toolbar.addView(smallButton("↻") { browser.reload() }, LinearLayout.LayoutParams(42, 42))
        address = EditText(this).apply {
            setSingleLine(true)
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            hint = "Pesquisar ou digitar endereço"
            setPadding(10, 0, 10, 0)
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO) { navigate(); true } else false
            }
        }
        toolbar.addView(address, LinearLayout.LayoutParams(0, 44, 1f))
        toolbar.addView(smallButton("IR") { navigate() }, LinearLayout.LayoutParams(48, 42))
        root.addView(toolbar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        buildAnalyzerPanel(root)
        setContentView(root)
        browser.loadUrl("about:blank")
    }

    private fun smallButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 17f
        setTextColor(Color.WHITE)
        setPadding(0, 0, 0, 0)
        setBackgroundColor(Color.rgb(42, 58, 72))
        setOnClickListener { action() }
    }

    private fun navigate() {
        val entered = address.text.toString().trim()
        if (entered.isEmpty()) return
        val target = when {
            entered.startsWith("https://", true) || entered.startsWith("http://", true) -> entered
            entered.contains(" ") || !entered.contains(".") -> "https://www.google.com/search?q=" + java.net.URLEncoder.encode(entered, "UTF-8")
            else -> "https://$entered"
        }
        browser.loadUrl(target)
    }

    private fun buildAnalyzerPanel(root: FrameLayout) {
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(7, 7, 7, 7)
            setBackgroundColor(Color.rgb(12, 20, 29))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(12, 20, 29)); setStroke(2, Color.rgb(210, 169, 82)); cornerRadius = 16f
            }
        }
        panelHeader = TextView(this).apply {
            text = "Y ANALYZER   −"
            textSize = 13f
            setTextColor(Color.rgb(245, 210, 135))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(4, 7, 4, 7)
        }
        panel.addView(panelHeader)
        resultText = TextView(this).apply {
            text = "Selecione um número"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(3, 5, 3, 7)
        }
        panel.addView(resultText)
        panelBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply { columnCount = 4 }
        for (n in 0..36) {
            val button = Button(this).apply {
                text = n.toString()
                textSize = 13f
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, 0)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(numberColor(n)); cornerRadius = 8f
                    setStroke(1, Color.DKGRAY)
                }
                setOnClickListener { selectNumber(n) }
            }
            val params = GridLayout.LayoutParams().apply {
                width = dp(39); height = dp(38)
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            grid.addView(button, params)
        }
        scroll.addView(grid)
        panelBody.addView(scroll, LinearLayout.LayoutParams(-1, dp(360)))
        val register = smallButton("REGISTRAR RESULTADO") { registerSelected() }
        register.textSize = 10f
        panelBody.addView(register, LinearLayout.LayoutParams(-1, dp(40)))
        historyText = TextView(this).apply {
            textSize = 10f
            setTextColor(Color.LTGRAY)
            setPadding(3, 5, 3, 3)
        }
        panelBody.addView(historyText)
        panel.addView(panelBody)
        updateHistory()
        val panelParams = FrameLayout.LayoutParams(dp(174), -2, Gravity.LEFT or Gravity.CENTER_VERTICAL).apply {
            leftMargin = dp(8); topMargin = dp(28)
        }
        root.addView(panel, panelParams)
        makeDraggable(panelHeader, panel, panelParams)
    }

    private fun togglePanel() {
        val collapsed = panelBody.visibility == View.GONE
        panelBody.visibility = if (collapsed) View.VISIBLE else View.GONE
        resultText.visibility = if (collapsed) View.VISIBLE else View.GONE
        panelHeader.text = if (collapsed) "Y ANALYZER   −" else "Y ANALYZER   +"
    }

    private var selectedNumber: Int? = null
    private fun selectNumber(n: Int) {
        selectedNumber = n
        if (n == 0) {
            resultText.text = "Zero selecionado. Use Registrar para salvar."
            return
        }
        val index = wheel.indexOf(n)
        val values = offsets.map { wheel[(index + it) % wheel.size] }
        resultText.text = "BASE $n\n" + values.mapIndexed { i, v -> labels[i] + ": " + v }.joinToString("  ") +
            "\nLista: " + values.joinToString(" – ")
    }

    private fun registerSelected() {
        val n = selectedNumber ?: return
        history.add(0, n)
        while (history.size > 100) history.removeAt(history.lastIndex)
        prefs.edit().putString("history", history.joinToString(",")).apply()
        updateHistory()
    }

    private fun updateHistory() {
        if (::historyText.isInitialized) historyText.text =
            "HISTÓRICO\n" + if (history.isEmpty()) "Nenhum resultado registrado" else history.take(12).joinToString(" · ")
    }

    private fun numberColor(n: Int): Int {
        if (n == 0) return Color.rgb(0, 125, 70)
        return if (n in listOf(1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36))
            Color.rgb(180, 43, 48) else Color.rgb(35, 43, 52)
    }

    private fun makeDraggable(handle: View, target: View, params: FrameLayout.LayoutParams) {
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        var moved = false
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = params.leftMargin; startY = params.topMargin
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    if (moved) {
                        params.gravity = Gravity.TOP or Gravity.LEFT
                        params.leftMargin = (startX + dx).coerceIn(0, (rootWidth() - target.width).coerceAtLeast(0))
                        params.topMargin = (startY + dy).coerceIn(0, (rootHeight() - target.height).coerceAtLeast(0))
                        target.layoutParams = params
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                    true
                }
                else -> true
            }
        }
    }

    private fun rootWidth(): Int = window.decorView.width
    private fun rootHeight(): Int = window.decorView.height
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::browser.isInitialized && browser.canGoBack()) browser.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::browser.isInitialized) { browser.stopLoading(); browser.destroy() }
        super.onDestroy()
    }
}
