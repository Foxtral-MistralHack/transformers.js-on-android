package com.trajoid

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    private lateinit var waveformView: WaveformView
    private lateinit var textViewSpeakNow: TextView

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private var lastLoudTimeMs: Long = System.currentTimeMillis()
    private val silenceThreshold = 0.05f 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()

        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        waveformView = findViewById(R.id.waveformView)
        textViewSpeakNow = findViewById(R.id.textViewSpeakNow)

        chatAdapter = ChatAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = chatAdapter

        setupWebView()

        textViewSpeakNow.setOnClickListener {
            // Dismiss manually if required
            textViewSpeakNow.visibility = View.GONE
            lastLoudTimeMs = System.currentTimeMillis()
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
        )
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 1)
            return false
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        if (isRecording.get()) return
        if (!checkPermissions()) return

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording.set(true)
        lastLoudTimeMs = System.currentTimeMillis()

        thread {
            val audioBuffer = ShortArray(bufferSize)
            while (isRecording.get()) {
                val readResult = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0
                if (readResult > 0) {
                    val floatBuffer = FloatArray(readResult)
                    var sumSquares = 0.0
                    for (i in 0 until readResult) {
                        floatBuffer[i] = audioBuffer[i] / 32768.0f
                        sumSquares += (floatBuffer[i] * floatBuffer[i])
                    }
                    val rms = Math.sqrt(sumSquares / readResult).toFloat()

                    runOnUiThread {
                        waveformView.addSample(rms * 5f) // scale up for visibility

                        if (rms > silenceThreshold) {
                            lastLoudTimeMs = System.currentTimeMillis()
                            if (textViewSpeakNow.visibility == View.VISIBLE) {
                                textViewSpeakNow.visibility = View.GONE
                            }
                        } else {
                            if (System.currentTimeMillis() - lastLoudTimeMs > 60000) {
                                if (textViewSpeakNow.visibility == View.GONE) {
                                    textViewSpeakNow.visibility = View.VISIBLE
                                }
                            }
                        }
                    }

                    sendAudioToWebView(floatBuffer)
                }
            }
        }
    }

    private fun stopRecording() {
        isRecording.set(false)
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun sendAudioToWebView(floatBuffer: FloatArray) {
        // Convert FloatArray to byte array to Base64 to send via evaluateJavascript
        val byteBuffer = java.nio.ByteBuffer.allocate(floatBuffer.size * 4)
        byteBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (f in floatBuffer) {
            byteBuffer.putFloat(f)
        }
        val encoded = Base64.encodeToString(byteBuffer.array(), Base64.NO_WRAP)
        
        runOnUiThread {
            webView.evaluateJavascript("window.receiveAudioChunk('$encoded')", null)
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        runOnUiThread {
            messages.add(ChatMessage(text, isUser))
            chatAdapter.notifyItemInserted(messages.size - 1)
            findViewById<RecyclerView>(R.id.recyclerView).scrollToPosition(messages.size - 1)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = findViewById(R.id.hiddenWebView)
        // Configure WebView
        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        webView.addJavascriptInterface(WebAppInterface(), "Android")

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/sdcard/", SdcardPathHandler())
            .build()

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d("TrajoidConsole", "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                Log.d("TrajoidNetwork", "Intercepting: ${request.url}")
                if (request.method.equals("OPTIONS", ignoreCase = true)) {
                    val response = WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                    response.responseHeaders = mapOf(
                        "Access-Control-Allow-Origin" to "*",
                        "Access-Control-Allow-Methods" to "GET, HEAD, OPTIONS",
                        "Access-Control-Allow-Headers" to "*",
                        "Access-Control-Expose-Headers" to "Content-Length, Content-Range",
                        "Accept-Ranges" to "bytes"
                    )
                    return response
                }
                val response = assetLoader.shouldInterceptRequest(request.url)
                if (response != null) {
                    val headers = response.responseHeaders?.toMutableMap() ?: mutableMapOf()
                    headers["Access-Control-Allow-Origin"] = "*"
                    headers["Access-Control-Allow-Methods"] = "GET, HEAD, OPTIONS"
                    headers["Access-Control-Allow-Headers"] = "*"
                    headers["Access-Control-Expose-Headers"] = "Content-Length, Content-Range"
                    headers["Accept-Ranges"] = "bytes"
                    headers["Content-Security-Policy"] = "worker-src 'self' blob: data: https://appassets.androidplatform.net; connect-src 'self' https://* blob: data:;"
                    response.responseHeaders = headers
                }
                return response
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("Trajoid", "Page finished loading, starting JS model initialization...")
                view?.evaluateJavascript("window.appInterface.initModel();", null)
            }
        }

        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun onMessageReceived(text: String) {
            Log.d("Trajoid", "Received from JS: $text")
            addMessage(text, false)
        }

        @JavascriptInterface
        fun onLog(log: String) {
            Log.d("TrajoidJS", log)
        }

        @JavascriptInterface
        fun onModelReady() {
            Log.d("Trajoid", "Model is ready, starting continuous record")
            runOnUiThread {
                startRecording()
            }
        }
    }

    class SdcardPathHandler : WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
            try {
                val file = File("/sdcard/$path")
                if (file.exists()) {
                    val extension = MimeTypeMap.getFileExtensionFromUrl(path)
                    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
                    Log.d("Trajoid", "Serving local file: ${file.absolutePath} with mime: $mimeType")
                    val response = WebResourceResponse(mimeType, "utf-8", FileInputStream(file))
                    response.responseHeaders = mapOf(
                        "Access-Control-Allow-Origin" to "*",
                        "Access-Control-Allow-Methods" to "GET, HEAD, OPTIONS",
                        "Access-Control-Allow-Headers" to "*",
                        "Access-Control-Expose-Headers" to "Content-Length, Content-Range",
                        "Accept-Ranges" to "bytes",
                        "Content-Length" to file.length().toString()
                    )
                    return response
                }
            } catch (e: Exception) {
                Log.e("Trajoid", "Error serving SD card file", e)
            }
            return null
        }
    }
}
