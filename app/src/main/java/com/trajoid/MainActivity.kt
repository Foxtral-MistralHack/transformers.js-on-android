package com.trajoid

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.FileInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()

        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        val editText: EditText = findViewById(R.id.editTextMessage)
        val buttonSend: Button = findViewById(R.id.buttonSend)

        chatAdapter = ChatAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = chatAdapter

        setupWebView()

        buttonSend.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotBlank()) {
                addMessage(text, true)
                editText.text.clear()
                // Send to WebView for processing
                val escapedText = text.replace("\"", "\\\"").replace("\n", "\\n")
                webView.evaluateJavascript("window.generateText(\"$escapedText\")", null)
            }
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
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
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true

        webView.addJavascriptInterface(WebAppInterface(), "Android")

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/sdcard/", SdcardPathHandler())
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
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
                    response.responseHeaders = headers
                }
                return response
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
