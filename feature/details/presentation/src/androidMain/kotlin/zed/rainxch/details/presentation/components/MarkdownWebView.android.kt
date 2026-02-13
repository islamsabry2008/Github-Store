package zed.rainxch.details.presentation.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun MarkdownWebView(
    html: String,
    modifier: Modifier,
    onLinkClick: ((url: String) -> Unit)?,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var contentHeightPx by remember { mutableIntStateOf(0) }

    val contentHeightDp = with(density) {
        if (contentHeightPx > 0) contentHeightPx.toDp() else 400.dp
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                setBackgroundColor(Color.TRANSPARENT)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    setSupportZoom(false)
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    cacheMode = WebSettings.LOAD_DEFAULT
                    allowFileAccess = false
                    textZoom = 100
                    @SuppressLint("SetJavaScriptEnabled")
                    layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                }

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onContentHeight(height: Int) {
                            val scaledHeight = (height * resources.displayMetrics.density).toInt()
                            contentHeightPx = scaledHeight
                        }

                        @JavascriptInterface
                        fun onLinkClick(url: String) {
                            if (onLinkClick != null) {
                                onLinkClick(url)
                            } else {
                                try {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    )
                                } catch (_: Exception) {
                                }
                            }
                        }
                    },
                    "AndroidBridge"
                )

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        if (url.startsWith("data:") || url.startsWith("javascript:")) {
                            return false
                        }
                        if (onLinkClick != null) {
                            onLinkClick(url)
                        } else {
                            try {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (_: Exception) {
                            }
                        }
                        return true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript("reportHeight();", null)
                    }
                }

                webChromeClient = WebChromeClient()

                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER

                isNestedScrollingEnabled = false
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://github.com",
                html,
                "text/html",
                "UTF-8",
                null
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp, max = contentHeightDp.coerceAtLeast(100.dp))
    )
}