package com.andreccardosso.roletaanalyzer

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var browserView: WebView
    private lateinit var addressBar: EditText
    private lateinit var navigation: LinearLayout
    private lateinit var root: FrameLayout
    private lateinit var menuButton: Button
    private val startUrl = "https://www.casino.org/casinoscores/pt-br/immersive-roulette/"

    private val strategyScript = """
        (function(){
          if(document.getElementById('__yStrategyPanel')) return;
          const wheel=[0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26];
          const offsets=[0,5,9,14,19,23,28,32];
          const labels=['+1','X1','+2','X2','+3','X3','+4','X4'];
          const style=document.createElement('style');
          style.textContent=`
            #__yStrategyPanel{position:fixed;z-index:2147483647;left:7px;top:50%;transform:translateY(-50%);width:112px;max-height:82vh;overflow:auto;background:rgba(9,16,23,.96);color:#f3f6f8;border:1px solid #d8ad55;border-radius:12px;padding:7px;box-shadow:0 4px 18px #0009;font:12px Arial,sans-serif;overscroll-behavior:contain}
            #__yStrategyPanel .yt-title{font-weight:800;font-size:11px;text-align:center;color:#f1ce80;margin:0 0 6px}
            #__yStrategyPanel .yt-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:4px}
            #__yStrategyPanel button{min-width:0;height:29px;border:1px solid #ffffff30;border-radius:6px;color:#fff;font-weight:800;font-size:12px;background:#202a34;padding:0}
            #__yStrategyPanel button.yt-red{background:#b93236}
            #__yStrategyPanel button.yt-black{background:#202a34}
            #__yStrategyPanel button.yt-selected{outline:2px solid #f2c86d;outline-offset:1px}
            #__yStrategyResult{position:fixed;z-index:2147483647;left:7px;top:calc(50% - min(41vh, 300px) - 82px);width:min(220px,58vw);background:rgba(9,16,23,.97);color:#fff;border:1px solid #d8ad55;border-radius:10px;padding:7px;box-shadow:0 4px 18px #0009;font:12px Arial,sans-serif;display:none}
            #__yStrategyResult .yr-head{font-weight:800;color:#f1ce80;margin-bottom:5px}
            #__yStrategyResult .yr-list{display:grid;grid-template-columns:repeat(4,1fr);gap:4px}
            #__yStrategyResult .yr-cell{background:#1b2935;border-radius:6px;text-align:center;padding:4px 1px}
            #__yStrategyResult .yr-cell small{display:block;color:#aebdca;font-size:9px}
            #__yStrategyResult .yr-cell b{font-size:15px}
            #__yStrategyResult .yr-line{margin-top:5px;font-weight:800;line-height:1.4;overflow-wrap:anywhere}
            #__yVideoProbe{position:fixed;z-index:2147483646;right:8px;bottom:8px;max-width:210px;background:rgba(9,16,23,.92);color:#fff;border:1px solid #6d8799;border-radius:8px;padding:6px 9px;font:11px Arial,sans-serif;pointer-events:none}
            @media(max-width:600px){#__yStrategyPanel{width:96px;left:4px;padding:5px}#__yStrategyPanel button{height:27px;font-size:11px}#__yStrategyResult{left:4px;width:190px}}
          `;
          document.head.appendChild(style);
          const panel=document.createElement('div');panel.id='__yStrategyPanel';
          panel.innerHTML='<div class="yt-title">ESCOLHA 1–36</div><div class="yt-grid"></div>';
          const grid=panel.querySelector('.yt-grid');
          const result=document.createElement('div');result.id='__yStrategyResult';
          result.innerHTML='<div class="yr-head">O RESULTADO APARECE AQUI</div>';
          function color(n){if(n===0)return 'green';return [1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36].includes(n)?'red':'black';}
          for(let n=1;n<=36;n++){
            const b=document.createElement('button');b.textContent=n;b.className='yt-'+color(n);
            b.addEventListener('click',function(){
              const start=wheel.indexOf(n);if(start<0)return;
              grid.querySelectorAll('button').forEach(x=>x.classList.toggle('yt-selected',x===b));
              const values=offsets.map(o=>wheel[(start+o)%wheel.length]);
              result.style.display='block';
              result.innerHTML='<div class="yr-head">INICIAL '+n+' · SENTIDO HORÁRIO · 45°</div><div class="yr-list">'+values.map((v,i)=>'<div class="yr-cell"><small>'+labels[i]+'</small><b>'+v+'</b></div>').join('')+'</div><div class="yr-line">Lista: '+values.join(' – ')+'</div>';
            });
            grid.appendChild(b);
          }
          document.documentElement.appendChild(result);
          document.documentElement.appendChild(panel);

          const probe=document.createElement('div');probe.id='__yVideoProbe';probe.textContent='Vídeo: procurando fonte acessível…';
          document.documentElement.appendChild(probe);
          let lastState='';
          function inspectVideo(){
            const videos=Array.from(document.querySelectorAll('video'));
            if(!videos.length){
              const state='Nenhum vídeo HTML acessível nesta página (pode estar em iframe ou canvas).';
              if(state!==lastState){probe.textContent=state;lastState=state;}
              return;
            }
            const v=videos.find(x=>x.readyState>=2 && x.videoWidth>0 && x.videoHeight>0) || videos[0];
            let access='aguardando quadros';
            if(v.readyState>=2 && v.videoWidth>0){
              try{
                const c=document.createElement('canvas');c.width=2;c.height=2;
                const cx=c.getContext('2d',{willReadFrequently:true});cx.drawImage(v,0,0,2,2);cx.getImageData(0,0,1,1);
                access='quadros acessíveis';
              }catch(e){access='bloqueado por proteção/CORS';}
            }
            const state='Vídeo '+v.videoWidth+'×'+v.videoHeight+' · '+(v.paused?'pausado':'reproduzindo')+' · '+access;
            if(state!==lastState){probe.textContent=state;lastState=state;}
          }
          inspectVideo();setInterval(inspectVideo,1200);
        })();
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()
        buildInterface()
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    private fun buildInterface() {
        root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        browserView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = settings.userAgentString.replace("; wv)", ")").replace("Version/4.0 ", "")
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.allowContentAccess = true
            settings.allowFileAccess = false
            settings.loadsImagesAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportZoom(true)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!url.isNullOrBlank()) addressBar.setText(url)
                    browserView.evaluateJavascript(strategyScript, null)
                }
            }
        }
        root.addView(browserView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6, 4, 6, 4)
            setBackgroundColor(0xEE17232E.toInt())
            visibility = View.GONE
        }
        addressBar = EditText(this).apply {
            setSingleLine(true)
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9AABB8.toInt())
            hint = "Endereço do site"
            setPadding(10, 2, 10, 2)
            setText(startUrl)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                    navigateToAddress()
                    true
                } else false
            }
        }
        val goButton = Button(this).apply {
            text = "IR"
            setOnClickListener { navigateToAddress(); navigation.visibility = View.GONE }
        }
        navigation.addView(addressBar, LinearLayout.LayoutParams(0, 44, 1f))
        navigation.addView(goButton, LinearLayout.LayoutParams(56, 44))
        val navParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP
        )
        root.addView(navigation, navParams)

        menuButton = Button(this).apply {
            text = "⋮"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC17232E.toInt())
            setOnClickListener {
                navigation.visibility = if (navigation.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        val menuParams = FrameLayout.LayoutParams(48, 48, Gravity.TOP or Gravity.RIGHT).apply {
            topMargin = 8
            rightMargin = 8
        }
        root.addView(menuButton, menuParams)
        setContentView(root)
        browserView.loadUrl(startUrl)
    }

    private fun navigateToAddress() {
        var url = addressBar.text.toString().trim()
        if (url.isBlank()) return
        if (!url.startsWith("https://") && !url.startsWith("http://")) url = "https://$url"
        browserView.loadUrl(url)
    }

    override fun onBackPressed() {
        if (::browserView.isInitialized && browserView.canGoBack()) browserView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::browserView.isInitialized) {
            browserView.stopLoading()
            browserView.destroy()
        }
        super.onDestroy()
    }
}
