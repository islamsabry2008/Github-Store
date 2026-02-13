package zed.rainxch.details.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.paint.Color
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import java.awt.Desktop
import java.net.URI
import javax.swing.JPanel
import java.awt.BorderLayout

/**
 * Desktop actual implementation using JavaFX WebView embedded via SwingPanel.
 *
 * JavaFX WebView supports full HTML5/CSS3 rendering including:
 * - Centered divs, flexbox, grid
 * - Badge images, SVGs
 * - Details/summary, tables
 * - Dark/light themed CSS
 *
 * Requires JavaFX (included in JBR, or add OpenJFX dependency).
 */
@Composable
actual fun MarkdownWebView(
    html: String,
    modifier: Modifier,
    onLinkClick: ((url: String) -> Unit)?,
) {
    var contentHeight by remember { mutableStateOf(400) }
    var jfxInitialized by remember { mutableStateOf(false) }

    remember {
        try {
            Platform.startup { }
        } catch (_: IllegalStateException) {
        }
        Platform.setImplicitExit(false)
        jfxInitialized = true
        true
    }

    if (!jfxInitialized) return

    val heightDp = (contentHeight).dp.coerceIn(100.dp, 10000.dp)

    SwingPanel(
        factory = {
            val panel = JPanel(BorderLayout())
            val jfxPanel = JFXPanel()
            panel.add(jfxPanel, BorderLayout.CENTER)

            Platform.runLater {
                val webView = WebView()
                val webEngine: WebEngine = webView.engine

                webView.isContextMenuEnabled = false

                webEngine.loadWorker.stateProperty().addListener { _, _, newState ->
                    if (newState == Worker.State.SUCCEEDED) {
                        try {
                            val height = webEngine.executeScript(
                                "Math.max(document.body.scrollHeight, document.body.offsetHeight, " +
                                        "document.documentElement.scrollHeight, document.documentElement.offsetHeight)"
                            )
                            if (height is Int && height > 0) {
                                contentHeight = height
                            } else if (height is Number) {
                                contentHeight = height.toInt().coerceAtLeast(100)
                            }
                        } catch (_: Exception) {
                        }

                        webEngine.executeScript(
                            """
                            document.addEventListener('click', function(e) {
                                var target = e.target;
                                while (target && target.tagName !== 'A') {
                                    target = target.parentElement;
                                }
                                if (target && target.href && !target.href.startsWith('javascript:')) {
                                    e.preventDefault();
                                   
                                    window.__pendingLink = target.href;
                                }
                            });
                        """
                        )
                    }
                }

                webEngine.locationProperty().addListener { _, oldLocation, newLocation ->
                    if (newLocation != null &&
                        oldLocation != null &&
                        newLocation != oldLocation &&
                        !newLocation.startsWith("data:") &&
                        !newLocation.startsWith("about:")
                    ) {
                        Platform.runLater {
                            webEngine.loadContent(html, "text/html")
                        }
                        if (onLinkClick != null) {
                            onLinkClick(newLocation)
                        } else {
                            try {
                                if (Desktop.isDesktopSupported()) {
                                    Desktop.getDesktop().browse(URI(newLocation))
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                }

                webEngine.loadContent(html, "text/html")

                val scene = Scene(webView)
                scene.fill = Color.TRANSPARENT
                jfxPanel.scene = scene
            }

            panel
        },
        update = { panel ->
            val jfxPanel = panel.getComponent(0) as? JFXPanel
            jfxPanel?.let { fxPanel ->
                Platform.runLater {
                    val scene = fxPanel.scene
                    val webView = scene?.root as? WebView
                    webView?.engine?.loadContent(html, "text/html")
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
    )
}