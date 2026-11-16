package com.andreccardosso.roletaanalyzer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Base64
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var lastFrameAt = 0L
    private val frameBusy = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(NativeCaptureBridge(), "AndroidCapture")
        setContentView(webView)
        webView.loadUrl("https://andreccardosso.github.io/roleta.-/y-roulette/")
    }

    inner class NativeCaptureBridge {
        @JavascriptInterface
        fun start() {
            runOnUiThread {
                val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(manager.createScreenCaptureIntent(), 2026)
            }
        }

        @JavascriptInterface
        fun stop() {
            runOnUiThread { stopProjection() }
        }
    }

    @Deprecated("Deprecated in Android API; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2026) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                startProjection(resultCode, data)
            } else {
                webView.evaluateJavascript("window.onNativeCaptureError && window.onNativeCaptureError('Permissão de captura não concedida')", null)
            }
        }
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                val now = System.currentTimeMillis()
                if (now - lastFrameAt < 180 || !frameBusy.compareAndSet(false, true)) {
                    reader.acquireLatestImage()?.close()
                    return@setOnImageAvailableListener
                }
                lastFrameAt = now
                val image = reader.acquireLatestImage()
                if (image == null) {
                    frameBusy.set(false)
                    return@setOnImageAvailableListener
                }
                try {
                    val plane = image.planes[0]
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val paddedWidth = width + rowPadding / pixelStride
                    val bitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(plane.buffer)
                    val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    val output = ByteArrayOutputStream()
                    cropped.compress(Bitmap.CompressFormat.JPEG, 55, output)
                    val encoded = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                    val js = "window.onNativeFrame && window.onNativeFrame('data:image/jpeg;base64,$encoded',$width,$height)"
                    runOnUiThread { webView.evaluateJavascript(js, null) }
                    bitmap.recycle()
                    if (cropped !== bitmap) cropped.recycle()
                } catch (_: Exception) {
                    runOnUiThread {
                        webView.evaluateJavascript("window.onNativeCaptureError && window.onNativeCaptureError('Falha ao ler quadro da tela')", null)
                    }
                } finally {
                    image.close()
                    frameBusy.set(false)
                }
            }, null)

            virtualDisplay = projection?.createVirtualDisplay(
                "RoletaScreenCapture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    runOnUiThread { stopProjection() }
                }
            }, null)
            webView.evaluateJavascript("window.onNativeCaptureStarted && window.onNativeCaptureStarted($width,$height)", null)
        } catch (error: Exception) {
            stopProjection()
            webView.evaluateJavascript("window.onNativeCaptureError && window.onNativeCaptureError('Não foi possível iniciar a captura nativa')", null)
        }
    }

    private fun stopProjection() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
        webView.evaluateJavascript("window.onNativeCaptureStopped && window.onNativeCaptureStopped()", null)
    }

    override fun onDestroy() {
        stopProjection()
        webView.removeJavascriptInterface("AndroidCapture")
        webView.destroy()
        super.onDestroy()
    }
}
