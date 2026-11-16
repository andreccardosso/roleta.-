package com.andreccardosso.roletaanalyzer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.DisplayMetrics
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Gravity
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
    private val projectionRequestCode = 7041
    private var projectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var projectionStarting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
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
                      var active=true, drawing=true, pointers={}, center=null, radius=0, rotation=0;
                      var first=null, dragStart=null, pinchStart=0, pinchRadius=0, pinchAngle=0, pinchRotation=0;
                      function angle(a,b){return Math.atan2(b.y-a.y,b.x-a.x);}
                      document.documentElement.style.touchAction='none';
                      function point(e){return {x:e.clientX,y:e.clientY};}
                      function distance(a,b){return Math.hypot(a.x-b.x,a.y-b.y);}
                      function paint(){
                        if(!center)return;
                        overlay.style.display='block';
                        overlay.style.left=center.x+'px';overlay.style.top=center.y+'px';
                        overlay.style.width=(radius*2)+'px';overlay.style.height=(radius*1.44)+'px';
                         overlay.style.transform='translate(-50%,-50%) rotate('+rotation+'rad)';
                      }
                      function send(){
                        if(!center||radius<8)return;
                        if(window.YBrowser&&window.YBrowser.onBrowserCircle){
                          window.YBrowser.onBrowserCircle(center.x/Math.max(1,innerWidth),center.y/Math.max(1,innerHeight),radius/Math.max(1,innerWidth),radius*.72/Math.max(1,innerHeight),rotation);
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
                          pinchStart=Math.max(1,distance(a,b));pinchRadius=radius;pinchAngle=angle(a,b);pinchRotation=rotation;dragStart=null;
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
                          radius=Math.max(8,pinchRadius*distance(a,b)/Math.max(1,pinchStart));
                           rotation=pinchRotation+(angle(a,b)-pinchAngle);paint();
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
                if (mediaProjection != null || projectionStarting) return@runOnUiThread
                projectionStarting = true
                captureStatus.text = "ANÁLISE: aguardando autorização para compartilhar a tela"
                try {
                    startActivityForResult(projectionManager!!.createScreenCaptureIntent(), projectionRequestCode)
                } catch (e: Exception) {
                    projectionStarting = false
                    analyzerError("Não foi possível solicitar a captura da tela.")
                }
            }
        }

        @JavascriptInterface
        fun stop() {
            runOnUiThread {
                stopScreenCapture()
                captureStatus.text = "ANÁLISE: captura pausada"
                analyzerView.evaluateJavascript(
                    "window.onNativeCaptureStopped && window.onNativeCaptureStopped()", null
                )
            }
        }
    }

    inner class BrowserPageBridge {
        @JavascriptInterface
        fun onBrowserCircle(cx: Double, cy: Double, rx: Double, ry: Double, rotation: Double) {
            runOnUiThread {
                val safeCx = cx.coerceIn(0.0, 1.0)
                val safeCy = cy.coerceIn(0.0, 1.0)
                val safeRx = rx.coerceIn(0.0, 0.5)
                val safeRy = ry.coerceIn(0.0, 0.5)
                val safeRotation = rotation.coerceIn(-3.14159, 3.14159)
                analyzerView.evaluateJavascript(
                    "window.onBrowserCircle && window.onBrowserCircle($safeCx,$safeCy,$safeRx,$safeRy,$safeRotation)", null
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

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != projectionRequestCode) return
        projectionStarting = false
        if (resultCode != Activity.RESULT_OK || data == null) {
            captureStatus.text = "ANÁLISE: compartilhamento cancelado"
            analyzerError("Autorize o compartilhamento da tela para analisar o vídeo.")
            return
        }
        try {
            val serviceIntent = Intent(this, CaptureForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
            handler.postDelayed({
                try {
                    val projection = projectionManager!!.getMediaProjection(resultCode, data)
                    mediaProjection = projection
                    projection.registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() {
                            runOnUiThread { stopScreenCapture() }
                        }
                    }, handler)
                    val metrics = DisplayMetrics()
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay.getRealMetrics(metrics)
                    val width = metrics.widthPixels.coerceAtLeast(1)
                    val height = metrics.heightPixels.coerceAtLeast(1)
                    imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 3)
                    imageReader!!.setOnImageAvailableListener({ reader ->
                        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        try {
                            if (capturing && !captureBusy) processScreenImage(image)
                        } finally {
                            image.close()
                        }
                    }, handler)
                    virtualDisplay = projection.createVirtualDisplay(
                        "YAnalyzerCapture", width, height, metrics.densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader!!.surface, null, handler
                    )
                    capturing = true
                    captureBusy = false
                    lastFrameAt = 0L
                    captureStatus.text = "ANÁLISE: vídeo do navegador conectado"
                    analyzerView.evaluateJavascript(
                        "window.onNativeCaptureStarted && window.onNativeCaptureStarted(720,1280)", null
                    )
                } catch (e: Exception) {
                    stopScreenCapture()
                    analyzerError("Falha ao iniciar a captura da tela: " + (e.message ?: "erro desconhecido"))
                }
            }, 350L)
        } catch (e: Exception) {
            stopScreenCapture()
            analyzerError("Não foi possível iniciar o serviço de captura.")
        }
    }

    private fun processScreenImage(image: Image) {
        val now = System.currentTimeMillis()
        if (now - lastFrameAt < captureIntervalMs) return
        lastFrameAt = now
        captureBusy = true
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val paddedWidth = image.width + rowPadding / pixelStride
            val full = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
            buffer.rewind()
            full.copyPixelsFromBuffer(buffer)
            val location = IntArray(2)
            browserView.getLocationOnScreen(location)
            val left = location[0].coerceIn(0, full.width - 1)
            val top = location[1].coerceIn(0, full.height - 1)
            val right = (location[0] + browserView.width).coerceIn(left + 1, full.width)
            val bottom = (location[1] + browserView.height).coerceIn(top + 1, full.height)
            val cropped = Bitmap.createBitmap(full, left, top, right - left, bottom - top)
            full.recycle()
            deliverFrame(cropped)
        } catch (_: Exception) {
            captureBusy = false
        }
    }

    private fun stopScreenCapture() {
        capturing = false
        captureBusy = false
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { imageReader?.setOnImageAvailableListener(null, null) } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null
        try { stopService(Intent(this, CaptureForegroundService::class.java)) } catch (_: Exception) {}
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
        stopScreenCapture()
        handler.removeCallbacksAndMessages(null)
        if (::browserView.isInitialized) browserView.destroy()
        if (::analyzerView.isInitialized) {
            analyzerView.removeJavascriptInterface("AndroidCapture")
            analyzerView.destroy()
        }
        super.onDestroy()
    }
}
