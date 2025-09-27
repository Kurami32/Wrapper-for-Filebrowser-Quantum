package com.kurami32.filebrowserq_wrapper

import PassName
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import java.net.URLDecoder

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var downloadId: Long = -1L  // For track the current downloads
    private lateinit var downloadManager: DownloadManager
    private lateinit var prefs: SharedPreferences
    private var filePathCallback: ValueCallback<Array<Uri>>? = null // This is the callback for file uploads

    // Fullscreen support
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var fullscreenContainer: FrameLayout

    // For error view
    private lateinit var errorView: View
    private lateinit var reloadButton: Button

    // Gesture detection properties
    private var lastGestureTime = 0L
    private val gestureCooldown = 1000L // Prevent multiple triggers in quick succession

    private val tag = "MainActivity"
    private var hasError = false // Track if we've already shown an error

    // File picker for select when uploading a file (opens the default of your android device)
    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val results: Array<Uri>? = if (result.resultCode == RESULT_OK && result.data != null) { // returns OK when the user select and upload the files
                val data = result.data!!
                data.clipData?.let { clip ->
                    Array(clip.itemCount) { i -> clip.getItemAt(i).uri } // If multiple files are selected by the user extracts
                } ?: data.data?.let { arrayOf(it) } // all the URIs (of the selected files) and create an array of all of them
            } else {
                null
            }

            filePathCallback?.onReceiveValue(results) // then passes the array of URIs back to webview and upload the files
            filePathCallback = null // clear callback
        }

    // Receiver for the custom action sent by DownloadReceiver
    private val downloadFinishedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
        }
    }

    @SuppressLint("SetJavaScriptEnabled") // Suppress JavaScript warnings.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Save the URL that the user filled on the input field on the startup screen
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val url = intent.getStringExtra("webview_url") ?: prefs.getString("webview_url", "") ?: ""

        // Starts webview
        webView = findViewById(R.id.webview)

        // Use hardware acceleration for improve the performance of webview, but this need testing
        // On some devices could cause some tiny glitches (but is unlikely to happen), if do I'll need to change to: View.LAYER_TYPE_SOFTWARE
        try {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } catch (ex: Exception) {
            Log.w(tag, "Could not enable hardware acceleration for WebView", ex)
        }

        progressBar = findViewById(R.id.progressBar)
        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager

        // Initialize fullscreen container, this is for hide all the Android UI of the screen and just show the media when on fullscreen
        fullscreenContainer = findViewById(R.id.fullscreen_container)

        // Initialize error view and reload button
        errorView = findViewById(R.id.error_view)
        reloadButton = findViewById(R.id.reload_button)

        // Set up reload button click listener
        reloadButton.setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage("com.kurami32.filebrowserq")
            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                startActivity(launchIntent)
                finishAffinity() // kill webview and its processes.
            } else {
                recreate()
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            domStorageEnabled = true // HTML5 local storage
            loadWithOverviewMode = true // Loads WebView completely zoomed out (100%)
            useWideViewPort = true // Loads WebView with the attributes defined in the meta tag of the webpage.
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false // If is true, you will need to manually click on play, I let it on false because filebrowser web already handle this.
        }

        // Disable scroll bars for use the custom ones from filebrowser.
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        webView.overScrollMode = View.OVER_SCROLL_NEVER

        // Set WebChromeClient for enable the fullscreen support, if not the "fullscreen" button will be disabled. Also enables other events that you normally expect on a browser
        webView.webChromeClient = object : WebChromeClient() {

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }

                // Hide status bar and action buttons when entering to fullscreen mode
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // If the Android device uses Android 11+
                    window.insetsController?.let {
                        it.hide(WindowInsets.Type.systemBars())
                        it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                }

                customView = view
                customViewCallback = callback
                fullscreenContainer.visibility = View.VISIBLE
                fullscreenContainer.addView(customView)

                // Determine video or media orientation when on fullscreen
                determineVideoOrientation()
            }

            // When exit from fullscreen mode
            override fun onHideCustomView() {
                if (customView == null) return

                // Show the systemUI again when exit fullscreen mode
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.show(WindowInsets.Type.systemBars())
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                }

                // Reverts the orientation that the user had before enter fullscreen
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER

                fullscreenContainer.visibility = View.GONE
                fullscreenContainer.removeView(customView)
                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallbackParam: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null) // First clear any previous callback for then
                filePathCallback = filePathCallbackParam // store new ones

                val intent = try {
                    fileChooserParams?.createIntent()
                } catch (e: Exception) {
                    Log.e(tag, "createIntent failed", e)
                    null
                }

                if (intent == null) {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                    return false
                }

                return try {
                    fileChooserLauncher.launch(intent) // Launch the file picker native of android
                    true
                } catch (e: Exception) {
                    Log.e(tag, "Failed to launch file picker", e)
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                    false
                }
            }
        }


        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                // Progress bar when loading the page initially, is a gray bar that you barely will see it, but I think that if good to have.
                progressBar.visibility = View.VISIBLE
                hasError = false
                super.onPageStarted(view, url, favicon)
            }

            // When the page loads, hide the progress bar.
            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                super.onPageFinished(view, url)
            }

            // For handle navigation requests, here will load the URL that you saved on the setup screen.
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request?.isForMainFrame == true) {
                    val u = request.url.toString()
                    if (u.startsWith("http://") || u.startsWith("https://")) {
                        view?.loadUrl(u) // Load the URL
                    } else {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, u.toUri())
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.w(tag, "No activity to handle url: $u", e)
                        }
                    }
                }
                return true
            }

            // Handle network errors - only show error view for connection failures
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)

                // Only show error for main frame requests and if we haven't already shown an error
                if (request?.isForMainFrame == true && !hasError) {
                    val errorCode = error?.errorCode ?: 0

                    // Only show error for connection-related errors
                    if (errorCode == ERROR_CONNECT || errorCode == ERROR_HOST_LOOKUP || errorCode == ERROR_TIMEOUT) {
                        hasError = true
                        showErrorView()
                    }
                }
            }

            @Suppress("DEPRECATION") // For older API support
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)

                // Only show error if we haven't already shown one
                if (!hasError) {
                    // Only show error for connection-related error
                    if (errorCode == ERROR_CONNECT || errorCode == ERROR_HOST_LOOKUP || errorCode == ERROR_TIMEOUT) {
                        hasError = true
                        showErrorView()
                    }
                }
            }

            // Intercept request for any file upload and show a toast that one file is being uploaded
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                detectUploadRequests(request)
                return super.shouldInterceptRequest(view, request)
            }

            private fun detectUploadRequests(request: WebResourceRequest?) {
                request ?: return
                val url = request.url.toString()

                // Detect upload requests based on filebrowser API pattern.
                if (url.contains("/api/resources") && request.method == "POST") {
                    // Extract filename from URL parameters
                    val uri = request.url
                    val pathParam = uri.getQueryParameter("path")
                    val filename = if (!pathParam.isNullOrEmpty()) {
                        // Decode URL encoding and extract the name of the file
                        URLDecoder.decode(pathParam, "UTF-8").substringAfterLast("/")
                    } else {
                        "file"
                    }

                    // Show the toast when upload starts
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Uploading $filename", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // For handle downloads correctly, here is needed to forward cookies + referer, is necessary because filebrowser needs to know them (because there is where you session is active).
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            try {
                val uri = url.toUri()
                val request = DownloadManager.Request(uri)

                // Forward cookies from WebView to DownloadManager
                val cookie = CookieManager.getInstance().getCookie(url)
                if (!cookie.isNullOrEmpty()) {
                    request.addRequestHeader("Cookie", cookie)
                }

                // Forward refererers, I tried without this and I had a error 404
                val referer = webView.url ?: url
                request.addRequestHeader("Referer", referer)

                request.setMimeType(mimetype)
                request.addRequestHeader("User-Agent", userAgent)
                request.setDescription("Downloading file") // Text of the toast when downloading some file

                // Filename detection, this is needed for download the files with their original name.
                // If not, will download all the files with the name "raw" and append their extension.
                fun filenameFromContentDisposition(cd: String?): String? {
                    if (cd == null) return null
                    // I added Regex rules for extract the name from the download URL.
                    Regex("filename\\*?=UTF-8''([^;\\r\\n]+)").find(cd)?.let { return it.groupValues[1].trim().trim('"') }
                    Regex("filename=\"([^\"]+)\"").find(cd)?.let { return it.groupValues[1] }
                    Regex("filename=([^;]+)").find(cd)?.let { return it.groupValues[1].trim().trim('"') }
                    return null
                }

                // Try content-disposition header first
                val fromCd = filenameFromContentDisposition(contentDisposition)

                // Try query params like ?files=... or ?file=... or ?filename=... (I saw that pattern inspecting the download link of filebrowser, and on its logs too)
                val fromQuery: String? = try {
                    val parsed = url.toUri()
                    val candidate = parsed.getQueryParameter("files")
                        ?: parsed.getQueryParameter("file")
                        ?: parsed.getQueryParameter("filename")
                    if (candidate.isNullOrEmpty()) null else {
                        // Decode and extract last path segment, handling "<SourceName>::/path/to/file.ext" pattern
                        val decoded = try { URLDecoder.decode(candidate, "UTF-8") } catch (_: Exception) { candidate }
                        // Drop any leading "<SourceName>::", then take after last slash
                        decoded.substringAfterLast("::").substringAfterLast('/')
                    }
                } catch (_: Exception) {
                    null
                }

                // Fallback to URLUtil.guessFileName (just in case)
                var fileName = fromCd ?: fromQuery ?: URLUtil.guessFileName(url, contentDisposition, mimetype)

                // Determine the algorithm parameter, for example, when you download a folder. On filebrowser you had two options for this ".zip" and ".tar.gz"
                val parsedUri = url.toUri()
                val algo = parsedUri.getQueryParameter("algo")

                // And ONLY apply the algo-based extension if this appears to be a folder download, I realized that on the download URL, the folders don't have any extension
                // The extension (and algorithm is determined by the option that you choose to download the folder)
                if (!algo.isNullOrEmpty() && !fileName.contains('.')) {
                    // Use the algorithm to determine the extension
                    fileName = when (algo) {
                        "zip" -> "$fileName.zip"
                        "tar.gz" -> "$fileName.tar.gz"
                        else -> fileName // Keep original extension if the algorithm is unknown
                    }
                }

                // Make sure that the file downloaded has their extension
                if (!fileName.contains('.') && !mimetype.isNullOrEmpty()) {
                    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype)
                    if (!ext.isNullOrEmpty()) fileName = "$fileName.$ext"
                }

                request.setTitle(fileName)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                request.setAllowedOverMetered(true)
                request.setAllowedOverRoaming(true)

                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

                downloadId = downloadManager.enqueue(request)
                Toast.makeText(this, "Downloading $fileName", Toast.LENGTH_SHORT).show()
                PassName.NameFile = fileName
            } catch (e: Exception) {
                Log.e(tag, "Download request failed", e)
                Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()
            }
        }

        // Setup gesture detection AFTER all WebView configuration
        setupGestureDetection()

        // Load the initial URL
        if (url.isNotBlank()) {
            if (isNetworkAvailable()) {
                webView.loadUrl(url)
            } else {
                hasError = true
                showErrorView()
            }
        } else {
            Log.w(tag, "No URL is set")
        }

        // Register the download receiver for finished downloads notifications from DownloadManager
        val filter = IntentFilter("com.kurami32.filebrowserq.DOWNLOAD_FINISHED")
        // The API-guarded call is explicit for API 33+ so the registration is marked as not-exported.
        // For older APIs (Android devices) is needed call the 2-arg overload; the linting can still give warnings, so I need a local suppression.
        @SuppressLint("UnprotectedBroadcastReceiver")
        run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Use the exported flag overload on API 33+
                registerReceiver(downloadFinishedReceiver, filter, RECEIVER_NOT_EXPORTED)
            } //else {
            //  registerReceiver(downloadFinishedReceiver, filter)
            // }
        }

        // Update the back button to handle fullscreen exit
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    // Tell to the WebChromeClient, "hey, hide the custom view" for return how was before.
                    webView.webChromeClient?.onHideCustomView()
                } else if (webView.canGoBack()) {
                    webView.goBack() // for navigate back on the webview history
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    // Gesture detection for reload and delete cookies
    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestureDetection() {
        var startY = 0f
        var fingerCount = 0
        var gestureTriggered = false

        webView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // When additional fingers are added
                    fingerCount = event.pointerCount
                    startY = event.getY(0) // Use first finger's Y position
                    gestureTriggered = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if ((fingerCount == 2 || fingerCount == 3) && !gestureTriggered) {
                        val currentY = event.getY(0)
                        val deltaY = currentY - startY

                        // Check if it's a significant downward swipe (more than 280 pixels)
                        if (deltaY > 280) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastGestureTime > gestureCooldown) {
                                gestureTriggered = true
                                when (fingerCount) {
                                    2 -> {
                                        handleTwoFingerGesture()
                                        lastGestureTime = currentTime
                                    }
                                    3 -> {
                                        handleThreeFingerGesture()
                                        lastGestureTime = currentTime
                                    }
                                }
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    // Reset when fingers are lifted
                    fingerCount = 0
                    gestureTriggered = false
                }
            }
            false
        }
    }

    private fun handleTwoFingerGesture() {
        runOnUiThread {
            if (::webView.isInitialized) {
                webView.reload()
                Toast.makeText(this, "Page reloaded 🔄", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleThreeFingerGesture() {
        runOnUiThread {
            showClearCookiesConfirmation()
        }
    }

     private fun showClearCookiesConfirmation() {
         AlertDialog.Builder(this)
             .setTitle("Clear All Cookies")
             .setMessage("Are you sure you want to clear the cookies?")
             .setPositiveButton("Clear") { dialog, _ ->
                 clearAllCookies()
                 dialog.dismiss()
             }
             .setNegativeButton("Cancel") { dialog, _ ->
                 dialog.dismiss()
             }
             .setIcon(android.R.drawable.ic_dialog_alert)
             .show()
     }

     private fun clearAllCookies() {
         try {
             val cookieManager = CookieManager.getInstance()

             cookieManager.removeAllCookies { success ->
                 runOnUiThread {
                     if (success) {
                         cookieManager.flush()
                         Toast.makeText(this, "All cookies cleared ✅", Toast.LENGTH_LONG).show()

                         // Reload the page to reflect the changes
                         if (::webView.isInitialized && webView.url != null) {
                             webView.reload()
                         }
                     } else {
                         Toast.makeText(this, "Failed to clear cookies ❌", Toast.LENGTH_SHORT).show()
                     }
                 }
             }
         } catch (e: Exception) {
             runOnUiThread {
                 Toast.makeText(this, "Error clearing cookies: ${e.message}", Toast.LENGTH_SHORT).show()
                 Log.e(tag, "Error clearing cookies", e)
             }
         }
     }

    // Helper method to show error view
    private fun showErrorView() {
        runOnUiThread {
            webView.visibility = View.GONE
            errorView.visibility = View.VISIBLE
        }
    }

    // Helper method to check network availability
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
        return networkCapabilities != null &&
                (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }

    // JavaScript to determine the orientation of the video/media playing, so the screen is rotated automatically.
    private fun determineVideoOrientation() {
        webView.evaluateJavascript(
            """
            (function() {
                var video = document.querySelector('video');
                if (video) {
                    return video.videoWidth > video.videoHeight ? 'landscape' : 'portrait';
                }
                return null;
            })();
            """.trimIndent()
        ) { result ->
            val orientation = result.removeSurrounding("\"")
            runOnUiThread {
                requestedOrientation = when (orientation) {
                    "landscape" -> {
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                    "portrait" -> {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    else -> {
                        // Default to landscape if is not possible determine the orientation
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // Clean up resources to avoid memory leaks.
        try {
            unregisterReceiver(downloadFinishedReceiver)
        } catch (_: IllegalArgumentException) {
            //
        }

        try {
            webView.stopLoading()
            webView.loadUrl("about:blank") // Clear the previous webview content and load the URL saved again.
            webView.removeAllViews()
            webView.destroy() // Kill webview process for don't let it lying there; other apps are using webview too, so I guess that this will only kill the "current tab" or content containing the filebrowser WebUI.
        } catch (e: Exception) {
            Log.w(tag, "Error while destroying WebView", e)
        }

        super.onDestroy()
    }
}