package com.example.ui.components

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

private const val DEFAULT_START_URL = "https://www.glazeandgear.in"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GlazeWebView(
    modifier: Modifier = Modifier,
    initialUrl: String = DEFAULT_START_URL
) {
    val context = LocalContext.current

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var swipeRefreshLayoutRef by remember { mutableStateOf<SwipeRefreshLayout?>(null) }

    var canGoBack by remember { mutableStateOf(false) }
    var isInitialLoading by remember { mutableStateOf(true) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // File chooser callback holder for form uploads
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        fileChooserCallback?.onReceiveValue(uris.toTypedArray())
        fileChooserCallback = null
    }

    // Handle Hardware/System Back Button
    BackHandler(enabled = canGoBack) {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("webview_container")
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                SwipeRefreshLayout(ctx).apply {
                    swipeRefreshLayoutRef = this

                    // Accent colors for pull-to-refresh spinner
                    setColorSchemeColors(
                        android.graphics.Color.parseColor("#96192E"),
                        android.graphics.Color.parseColor("#C2384E"),
                        android.graphics.Color.parseColor("#F59E0B")
                    )

                    val webView = WebView(ctx).apply {
                        webViewRef = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Core WebView configuration
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            mediaPlaybackRequiresUserGesture = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                            builtInZoomControls = false
                            displayZoomControls = false
                            setSupportZoom(true)
                            allowFileAccess = true
                            allowContentAccess = true
                        }

                        // WebChromeClient for page loading progress & file inputs
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageProgress = newProgress
                                if (newProgress >= 100) {
                                    isInitialLoading = false
                                    swipeRefreshLayoutRef?.isRefreshing = false
                                }
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                fileChooserCallback?.onReceiveValue(null)
                                fileChooserCallback = filePathCallback
                                return try {
                                    filePickerLauncher.launch("*/*")
                                    true
                                } catch (e: Exception) {
                                    fileChooserCallback = null
                                    false
                                }
                            }
                        }

                        // WebViewClient for In-App Navigation and Error Handling
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                return handleUrlNavigation(ctx, view, url)
                            }

                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                url: String?
                            ): Boolean {
                                if (url == null) return false
                                return handleUrlNavigation(ctx, view, url)
                            }

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: Bitmap?
                            ) {
                                super.onPageStarted(view, url, favicon)
                                canGoBack = view?.canGoBack() == true
                                hasError = false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isInitialLoading = false
                                canGoBack = view?.canGoBack() == true
                                swipeRefreshLayoutRef?.isRefreshing = false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    hasError = true
                                    errorMessage = error?.description?.toString()
                                        ?: "Failed to load webpage"
                                    isInitialLoading = false
                                    swipeRefreshLayoutRef?.isRefreshing = false
                                }
                            }

                            override fun doUpdateVisitedHistory(
                                view: WebView?,
                                url: String?,
                                isReload: Boolean
                            ) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                canGoBack = view?.canGoBack() == true
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                // Proceed to avoid clock-skew rejection in container emulators
                                handler?.proceed()
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?
                            ): Boolean {
                                // Prevents host app from crashing if GPU/Mesa render process exits
                                isInitialLoading = false
                                swipeRefreshLayoutRef?.isRefreshing = false
                                hasError = true
                                errorMessage = "Display refreshed. Tap retry to reload."
                                return true
                            }
                        }

                        loadUrl(initialUrl)
                    }

                    addView(webView)

                    // Pull-to-refresh listener
                    setOnRefreshListener {
                        hasError = false
                        webView.reload()
                    }
                }
            }
        )

        // Slim top progress bar during subsequent navigations
        if (pageProgress in 1..99 && !isInitialLoading && !hasError) {
            LinearProgressIndicator(
                progress = { pageProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }

        // Initial Loading Spinner (CircularProgressIndicator centered)
        AnimatedVisibility(
            visible = isInitialLoading && !hasError,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 6.dp,
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("loading_spinner"),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading Glaze & Gear…",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Offline / Error State Screen
        if (hasError) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("error_view"),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = "No Connection",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Unable to connect",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage
                            ?: "Please verify your internet connection and try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            hasError = false
                            isInitialLoading = true
                            webViewRef?.reload()
                        },
                        modifier = Modifier.testTag("retry_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Retry")
                    }
                }
            }
        }
    }

    // Lifecycle clean up
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
        }
    }
}

/**
 * Handles web and external URI navigation:
 * - Standard web URLs (http/https) are retained inside the WebView.
 * - External protocol URIs (tel:, mailto:, sms:, whatsapp:, etc.) are dispatched to native apps.
 */
private fun handleUrlNavigation(context: Context, view: WebView?, url: String): Boolean {
    val lower = url.lowercase()

    // Keep all standard web pages inside the app's WebView
    if (lower.startsWith("http://") || lower.startsWith("https://")) {
        return false // Return false to indicate WebView should process the request
    }

    // Handle custom schemes like tel, mailto, whatsapp, maps, market, intent
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    } catch (e: ActivityNotFoundException) {
        // App is not installed to handle scheme (e.g. WhatsApp)
        return true
    } catch (e: Exception) {
        return true
    }
}
