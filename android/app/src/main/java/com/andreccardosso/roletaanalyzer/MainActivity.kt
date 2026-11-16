package com.andreccardosso.roletaanalyzer

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.PixelCopy
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {
    private lateinit var browserView: WebView
    private lateinit var analyzerView: WebView
    private lateinit var addressBar: EditText
    private lateinit var captureStatus: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var capturing = false
    private var captureBusy = false
    private var lastFrameAt = 0L
    private val captureIntervalMs = 80L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildInterface()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B1117.toInt())
        }

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 6, 8, 6)
            setBackgroundColor(0xFF17232E.toInt())
        }
        addressBar = EditText(this).apply {
            setSingleLine(true)
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9AABB8.toInt())
            hint = "Endereço do site"
            setPadding(12, 4, 12, 4)
            setText("")
        }
        val goButton = Button(this).apply {
            text = "IR"
            setOnClickListener { navigateToAddress() }
        }
        navigation.addView(addressBar, LinearLayout.LayoutParams(0, 48, 1f))
        navigation.addView(goButton, LinearLayout.LayoutParams(64, 48))

        browserView = WebView(this).apply {
            settings.javaScriptEnabled = true
            addJavascriptInterface(BrowserPageBridge(), "YBrowser")
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = settings.userAgentString
                .replace("; wv)", ")")
                .replace("Version/4.0 ", "")
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.allowContentAccess = true
            settings.allowFileAccess = false
            settings.loadsImagesAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setOnTouchListener { view, _ ->
                view.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!url.isNullOrBlank()) addressBar.setText(url)
                }
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }

        val browserLabel = TextView(this).apply {
            text = "NAVEGADOR · ENTRE NO SITE E ABRA A ROLETA"
            textSize = 10f
            setTextColor(0xFFE4B85D.toInt())
            setPadding(10, 5, 10, 5)
            setBackgroundColor(0xFF101923.toInt())
        }

        captureStatus = TextView(this).apply {
            text = "ANÁLISE: aguardando captura do navegador"
            textSize = 10f
            setTextColor(0xFF9AABB8.toInt())
            setPadding(10, 5, 10, 5)
            setBackgroundColor(0xFF101923.toInt())
        }

        analyzerView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(BrowserCaptureBridge(), "AndroidCapture")
        }

        root.addView(navigation, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(browserLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(browserView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.58f
        ))
        root.addView(captureStatus, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(analyzerView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.42f
        ))

        setContentView(root)
        browserView.loadUrl(addressBar.text.toString())
        analyzerView.loadUrl("https://andreccardosso.github.io/roleta.-/y-roulette/")
    }

    private fun navigateToAddress() {
        var url = addressBar.text.toString().trim()
        if (url.isBlank()) return
        if (!url.startsWith("https://") && !url.startsWith("http://")) url = "https://$url"
        browserView.loadUrl(url)
    }

    inner class BrowserCaptureBridge {
        @JavascriptInterface
        fun setCircleMark() {
            runOnUiThread {
                val script = """
                    (function(){
                      var overlay=document.getElementById('__yWheelCircle');
                      if(!overlay){
                        overlay=document.createElement('div');
                        overlay.id='__yWheelCircle';
                        overlay.style.cssText='position:fixed;display:none;border:3px solid #ffd34e;border-radius:50%;background:rgba(255,211,78,.10);box-shadow:0 0 12px #ffd34e;transform:translate(-50%,-50%);z-index:2147483647;pointer-events:none;';
                        document.documentElement.appendChild(overlay);
                      }
                      var active=true, drawing=true, pointers={}, center=null, radius=0;
                      var first=null, dragStart=null, pinchStart=0, pinchRadius=0;
                      document.documentElement.style.touchAction='none';
                      function point(e){return {x:e.clientX,y:e.clientY};}
                      function distance(a,b){return Math.hypot(a.x-b.x,a.y-b.y);}
                      function paint(){
                        if(!center)return;
                        overlay.style.display='block';
                        overlay.style.left=center.x+'px';overlay.style.top=center.y+'px';
                        overlay.style.width=(radius*2)+'px';overlay.style.height=(radius*2)+'px';
                      }
                      function send(){
                        if(!center||radius<8)return;
                        if(window.YBrowser&&window.YBrowser.onBrowserCircle){
                          window.YBrowser.onBrowserCircle(center.x/Math.max(1,innerWidth),center.y/Math.max(1,innerHeight),radius/Math.max(1,innerWidth),radius/Math.max(1,innerHeight));
                        }
                      }
                      function down(e){
                        if(!active||e.pointerType==='mouse'&&e.button!==0)return;
                        e.preventDefault();e.stopPropagation();
                        var p=point(e);pointers[e.pointerId]=p;
                        try{document.documentElement.setPointerCapture&&document.documentElement.setPointerCapture(e.pointerId);}catch(_){}
                        var ids=Object.keys(pointers);
                        if(drawing){
                          if(!first)first=p;
                        }else if(ids.length===1){
                          dragStart={point:p,center:{x:center.x,y:center.y}};
                        }else if(ids.length===2){
                          var a=pointers[ids[0]],b=pointers[ids[1]];
                          pinchStart=Math.max(1,distance(a,b));pinchRadius=radius;dragStart=null;
                        }
                      }
                      function move(e){
                        if(!active||!Object.prototype.hasOwnProperty.call(pointers,e.pointerId))return;
                        e.preventDefault();e.stopPropagation();
                        var p=point(e);pointers[e.pointerId]=p;
                        var ids=Object.keys(pointers);
                        if(drawing&&first){
                          var d=distance(first,p);
                          center={x:(first.x+p.x)/2,y:(first.y+p.y)/2};
                          radius=d/2;paint();
                        }else if(ids.length>=2){
                          var a=pointers[ids[0]],b=pointers[ids[1]];
                          radius=Math.max(8,pinchRadius*distance(a,b)/Math.max(1,pinchStart));paint();
                        }else if(dragStart){
                          center={x:dragStart.center.x+(p.x-dragStart.point.x),y:dragStart.center.y+(p.y-dragStart.point.y)};
                          paint();
                        }
                      }
                      function up(e){
                        if(!Object.prototype.hasOwnProperty.call(pointers,e.pointerId))return;
                        e.preventDefault();e.stopPropagation();
                        delete pointers[e.pointerId];
                        if(drawing&&first&&Object.keys(pointers).length===0){
                          drawing=false;first=null;
                          if(radius<12){overlay.style.display='none';center=null;return;}
                          paint();send();
                        }else if(!drawing){
                          if(Object.keys(pointers).length<2){pinchStart=0;pinchRadius=radius;}
                          if(Object.keys(pointers).length===0){dragStart=null;send();}
                        }
                      }
                      if(!window.__yCircleHandlers){
                        document.addEventListener('pointerdown',down,true);
                        document.addEventListener('pointermove',move,true);
                        document.addEventListener('pointerup',up,true);
                        document.addEventListener('pointercancel',up,true);
                        window.__yCircleHandlers=true;
                      }
                      window.__yCircleDrawingActive=true;
                    })();
                """.trimIndent()
                browserView.evaluateJavascript(script, null)
            }
        }

        @JavascriptInterface
        fun setMark(mode: String) {
            val safeMode = mode.filter { it.isLetter() }
            if (safeMode !in listOf("center", "ball", "wheel")) return
            runOnUiThread {
                val script = """
                    (function(){
                      window.__yMarkMode = '$safeMode';
                      if(window.__yMarkHandler) return;
                      window.__yMarkHandler = function(ev){
                        if(!window.__yMarkMode) return;
                        var mode=window.__yMarkMode;
                        window.__yMarkMode=null;
                        if(window.YBrowser && window.YBrowser.onBrowserMark){
                          window.YBrowser.onBrowserMark(mode, ev.clientX/Math.max(1,window.innerWidth), ev.clientY/Math.max(1,window.innerHeight));
                        }
                      };
                      document.addEventListener('click',window.__yMarkHandler,true);
                    })();
                """.trimIndent()
                browserView.evaluateJavascript(script, null)
            }
        }

        @JavascriptInterface
        fun showLaser(x: Double, y: Double) {
            runOnUiThread {
                val script = if (x < 0 || y < 0) {
                    "document.getElementById('__yAnalyzerLaser')?.remove();"
                } else {
                    val safeX = x.coerceIn(0.0, 1.0) * 100.0
                    val safeY = y.coerceIn(0.0, 1.0) * 100.0
                    """
                    (function(){
                      var laser=document.getElementById('__yAnalyzerLaser');
                      if(!laser){
                        laser=document.createElement('div');
                        laser.id='__yAnalyzerLaser';
                        laser.style.cssText='position:fixed;width:26px;height:26px;border:2px solid #ff3030;border-radius:50%;box-shadow:0 0 8px #ff3030, inset 0 0 6px #ff3030;transform:translate(-50%,-50%);z-index:2147483647;pointer-events:none;';
                        var dot=document.createElement('div');
                        dot.style.cssText='position:absolute;width:5px;height:5px;background:#ff3030;border-radius:50%;left:50%;top:50%;transform:translate(-50%,-50%);';
                        laser.appendChild(dot);
                        document.documentElement.appendChild(laser);
                      }
                      laser.style.left='${safeX}vw';
                      laser.style.top='${safeY}vh';
                    })();
                    """.trimIndent()
                }
                browserView.evaluateJavascript(script, null)
            }
        }
        @JavascriptInterface
        fun start() {
            runOnUiThread {
                if (browserView.url.isNullOrBlank()) {
                    analyzerError("Abra primeiro o site da roleta no navegador acima.")
                    return@runOnUiThread
                }
                capturing = true
                captureStatus.text = "ANÁLISE: capturando somente a área do navegador"
                analyzerView.evaluateJavascript(
                    "window.onNativeCaptureStarted && window.onNativeCaptureStarted(720,1280)", null
                )
                scheduleCapture(0)
            }
        }

        @JavascriptInterface
        fun stop() {
            runOnUiThread {
                capturing = false
                captureStatus.text = "ANÁLISE: captura pausada"
                analyzerView.evaluateJavascript(
                    "window.onNativeCaptureStopped && window.onNativeCaptureStopped()", null
                )
            }
        }
    }

    inner class BrowserPageBridge {
        @JavascriptInterface
        fun onBrowserCircle(cx: Double, cy: Double, rx: Double, ry: Double) {
            runOnUiThread {
                val safeCx = cx.coerceIn(0.0, 1.0)
                val safeCy = cy.coerceIn(0.0, 1.0)
                val safeRx = rx.coerceIn(0.0, 0.5)
                val safeRy = ry.coerceIn(0.0, 0.5)
                analyzerView.evaluateJavascript(
                    "window.onBrowserCircle && window.onBrowserCircle($safeCx,$safeCy,$safeRx,$safeRy)", null
                )
            }
        }

        @JavascriptInterface
        fun onBrowserMark(mode: String, x: Double, y: Double) {
            runOnUiThread {
                val safeMode = mode.filter { it.isLetter() }
                if (safeMode !in listOf("center", "ball", "wheel")) return@runOnUiThread
                val safeX = x.coerceIn(0.0, 1.0)
                val safeY = y.coerceIn(0.0, 1.0)
                analyzerView.evaluateJavascript(
                    "window.onBrowserMark && window.onBrowserMark('$safeMode',$safeX,$safeY)", null
                )
            }
        }
    }

    private fun scheduleCapture(delay: Long) {
        handler.postDelayed({
            if (capturing) captureBrowserFrame()
        }, delay)
    }

    private fun captureBrowserFrame() {
        if (!capturing || captureBusy || browserView.width <= 0 || browserView.height <= 0) {
            if (capturing) scheduleCapture(captureIntervalMs)
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastFrameAt < captureIntervalMs) {
            scheduleCapture(captureIntervalMs - (now - lastFrameAt))
            return
        }
        lastFrameAt = now
        captureBusy = true

        val targetWidth = browserView.width
        val targetHeight = browserView.height
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val location = IntArray(2)
            browserView.getLocationInWindow(location)
            val sourceRect = Rect(
                location[0], location[1],
                location[0] + browserView.width, location[1] + browserView.height
            )
            PixelCopy.request(window, sourceRect, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) deliverFrame(bitmap)
                else {
                    bitmap.recycle()
                    captureBusy = false
                    if (capturing) scheduleCapture(captureIntervalMs)
                }
            }, handler)
        } else {
            try {
                val canvas = Canvas(bitmap)
                canvas.scale(
                    targetWidth.toFloat() / browserView.width,
                    targetHeight.toFloat() / browserView.height
                )
                browserView.draw(canvas)
                deliverFrame(bitmap)
            } catch (_: Exception) {
                bitmap.recycle()
                captureBusy = false
                if (capturing) scheduleCapture(captureIntervalMs)
            }
        }
    }

    private fun deliverFrame(bitmap: Bitmap) {
        try {
            val output = ByteArrayOutputStream()
            val scaled = if (bitmap.width > 720) Bitmap.createScaledBitmap(bitmap, 720, (bitmap.height * 720f / bitmap.width).toInt().coerceAtLeast(1), true) else bitmap
            scaled.compress(Bitmap.CompressFormat.JPEG, 55, output)
            val encoded = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            val width = scaled.width
            val height = scaled.height
            if (scaled !== bitmap) scaled.recycle()
            val js = "window.onNativeFrame && window.onNativeFrame('data:image/jpeg;base64,$encoded',$width,$height)"
            analyzerView.evaluateJavascript(js, null)
        } catch (_: Exception) {
            analyzerError("Falha ao preparar imagem do navegador.")
        } finally {
            bitmap.recycle()
            captureBusy = false
            if (capturing) scheduleCapture(captureIntervalMs)
        }
    }

    private fun analyzerError(message: String) {
        val safe = message.replace("\\", "\\\\").replace("'", "\\'")
        analyzerView.evaluateJavascript(
            "window.onNativeCaptureError && window.onNativeCaptureError('$safe')", null
        )
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (::browserView.isInitialized && browserView.canGoBack()) browserView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        capturing = false
        handler.removeCallbacksAndMessages(null)
        if (::browserView.isInitialized) browserView.destroy()
        if (::analyzerView.isInitialized) {
            analyzerView.removeJavascriptInterface("AndroidCapture")
            analyzerView.destroy()
        }
        super.onDestroy()
    }
}
