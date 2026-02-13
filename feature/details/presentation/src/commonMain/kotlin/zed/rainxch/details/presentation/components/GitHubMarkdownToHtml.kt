package zed.rainxch.details.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

/**
 * Converts raw GitHub markdown (which may contain embedded HTML) into a fully styled
 * HTML document that visually matches GitHub's rendering.
 *
 * Handles: GFM tables, code blocks, task lists, strikethrough, raw HTML passthrough
 * (centered divs, badges, image grids, details/summary, etc.), dark/light themes.
 */
object GitHubMarkdownToHtml {

    /**
     * Convert markdown string to a complete HTML document with GitHub-style CSS.
     *
     * @param markdown Raw markdown content (may include embedded HTML)
     * @param isDarkTheme Whether to use dark theme colors
     * @param textColor Primary text color (ARGB)
     * @param backgroundColor Page background color (ARGB)
     * @param linkColor Accent/link color (ARGB)
     * @param codeBgColor Code block background color (ARGB)
     * @param borderColor Border/divider color (ARGB)
     * @param cardBgColor Card/surface background (ARGB)
     */
    fun convert(
        markdown: String,
        isDarkTheme: Boolean,
        textColor: Int,
        backgroundColor: Int,
        linkColor: Int,
        codeBgColor: Int,
        borderColor: Int,
        cardBgColor: Int,
    ): String {
        val htmlBody = markdownToHtmlBody(markdown)
        return wrapInDocument(
            body = htmlBody,
            isDarkTheme = isDarkTheme,
            textColor = textColor.toCssRgba(),
            backgroundColor = backgroundColor.toCssRgba(),
            linkColor = linkColor.toCssRgba(),
            codeBgColor = codeBgColor.toCssRgba(),
            borderColor = borderColor.toCssRgba(),
            cardBgColor = cardBgColor.toCssRgba(),
        )
    }

    /**
     * Converts the markdown body to raw HTML using JetBrains' markdown parser.
     * The GFM flavor handles tables, strikethrough, task lists, and passes
     * through raw HTML (divs, imgs, details, etc.) untouched.
     */
    private fun markdownToHtmlBody(markdown: String): String {
        val flavour = GFMFlavourDescriptor()
        val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        var html = HtmlGenerator(markdown, parsedTree, flavour).generateHtml()

        // The JetBrains generator wraps output in <body> tags — strip them
        html = html
            .removePrefix("<body>")
            .removeSuffix("</body>")

        // Fix common GitHub README patterns the parser may escape or break:
        // 1. Shield.io badge images often have complex URLs with pipes
        // 2. HTML comments
        // 3. Align attributes on divs/paragraphs
        // The parser's HTML passthrough handles most of these, but we do
        // minor post-processing for edge cases:

        // Ensure <br> and <br/> are self-closing
        html = html.replace(Regex("<br\\s*/?>"), "<br/>")

        // Fix GitHub-style alert/note callouts (> [!NOTE], > [!WARNING], etc.)
        html = fixGitHubCallouts(html)

        return html
    }

    /**
     * Transform GitHub-style callout blocks into styled HTML.
     * GitHub renders `> [!NOTE]`, `> [!WARNING]`, `> [!IMPORTANT]`, `> [!TIP]`, `> [!CAUTION]`
     * as special styled blockquotes.
     */
    private fun fixGitHubCallouts(html: String): String {
        val calloutPattern = Regex(
            """<blockquote>\s*<p>\s*\[!(NOTE|WARNING|IMPORTANT|TIP|CAUTION)]\s*</p>([\s\S]*?)</blockquote>""",
            RegexOption.IGNORE_CASE
        )

        return calloutPattern.replace(html) { match ->
            val type = match.groupValues[1].uppercase()
            val content = match.groupValues[2].trim()
            val (emoji, cssClass) = when (type) {
                "NOTE" -> "ℹ️" to "callout-note"
                "WARNING" -> "⚠️" to "callout-warning"
                "IMPORTANT" -> "❗" to "callout-important"
                "TIP" -> "💡" to "callout-tip"
                "CAUTION" -> "🔴" to "callout-caution"
                else -> "📌" to "callout-note"
            }
            """
            <div class="callout $cssClass">
                <p class="callout-title">$emoji <strong>$type</strong></p>
                $content
            </div>
            """.trimIndent()
        }
    }

    /**
     * Wraps the converted HTML body in a full HTML document with GitHub-flavored CSS.
     */
    private fun wrapInDocument(
        body: String,
        isDarkTheme: Boolean,
        textColor: String,
        backgroundColor: String,
        linkColor: String,
        codeBgColor: String,
        borderColor: String,
        cardBgColor: String,
    ): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<style>
/* ========== RESET & BASE ========== */
*, *::before, *::after {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
}

html, body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans", Helvetica, Arial, sans-serif;
    font-size: 15px;
    line-height: 1.6;
    color: $textColor;
    background-color: $backgroundColor;
    word-wrap: break-word;
    overflow-wrap: break-word;
    -webkit-text-size-adjust: 100%;
}

body {
    padding: 0;
    margin: 0;
}

/* ========== TYPOGRAPHY ========== */
h1, h2, h3, h4, h5, h6 {
    margin-top: 24px;
    margin-bottom: 16px;
    font-weight: 600;
    line-height: 1.25;
    color: $textColor;
}

h1 { font-size: 1.8em; padding-bottom: 0.3em; border-bottom: 1px solid $borderColor; }
h2 { font-size: 1.5em; padding-bottom: 0.3em; border-bottom: 1px solid $borderColor; }
h3 { font-size: 1.25em; }
h4 { font-size: 1em; }
h5 { font-size: 0.875em; }
h6 { font-size: 0.85em; color: ${if (isDarkTheme) "rgba(255,255,255,0.5)" else "rgba(0,0,0,0.45)"}; }

h1:first-child, h2:first-child, h3:first-child {
    margin-top: 0;
}

p {
    margin-top: 0;
    margin-bottom: 16px;
}

/* ========== LINKS ========== */
a {
    color: $linkColor;
    text-decoration: none;
}
a:hover {
    text-decoration: underline;
}

/* ========== IMAGES (badges, screenshots, logos) ========== */
img {
    max-width: 100%;
    height: auto;
    border: 0;
    vertical-align: middle;
}

/* Badge-style images (shields.io etc.) — inline and small */
a > img[src*="shields.io"],
a > img[src*="img.shields.io"],
img[src*="shields.io"],
img[src*="img.shields.io"],
img[src*="badge"],
img[alt*="badge"],
a > img[src*="badge"] {
    display: inline-block;
    vertical-align: middle;
    height: auto;
    max-height: 36px;
}

/* ========== LISTS ========== */
ul, ol {
    margin-top: 0;
    margin-bottom: 16px;
    padding-left: 2em;
}

ul ul, ul ol, ol ul, ol ol {
    margin-bottom: 0;
}

li {
    margin-top: 0.25em;
}

li + li {
    margin-top: 0.25em;
}

/* Task lists (GFM) */
ul.contains-task-list {
    list-style-type: none;
    padding-left: 0;
}

li.task-list-item {
    padding-left: 0;
}

input[type="checkbox"] {
    margin-right: 0.5em;
    vertical-align: middle;
}

/* ========== CODE ========== */
code {
    font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
    font-size: 0.85em;
    padding: 0.2em 0.4em;
    margin: 0;
    background-color: $codeBgColor;
    border-radius: 6px;
}

pre {
    margin-top: 0;
    margin-bottom: 16px;
    padding: 16px;
    overflow: auto;
    font-size: 0.85em;
    line-height: 1.45;
    background-color: $codeBgColor;
    border-radius: 6px;
    border: 1px solid $borderColor;
}

pre code {
    padding: 0;
    margin: 0;
    background-color: transparent;
    border: 0;
    font-size: 100%;
    word-break: normal;
    white-space: pre;
    overflow-x: auto;
}

/* ========== TABLES ========== */
table {
    border-collapse: collapse;
    border-spacing: 0;
    width: 100%;
    max-width: 100%;
    overflow: auto;
    margin-top: 0;
    margin-bottom: 16px;
    display: block;
}

table th, table td {
    padding: 6px 13px;
    border: 1px solid $borderColor;
    text-align: left;
}

table th {
    font-weight: 600;
    background-color: $codeBgColor;
}

table tr:nth-child(2n) {
    background-color: ${if (isDarkTheme) "rgba(255,255,255,0.03)" else "rgba(0,0,0,0.02)"};
}

/* ========== BLOCKQUOTES ========== */
blockquote {
    margin: 0 0 16px 0;
    padding: 0 1em;
    color: ${if (isDarkTheme) "rgba(255,255,255,0.55)" else "rgba(0,0,0,0.5)"};
    border-left: 0.25em solid $borderColor;
}

blockquote > :first-child { margin-top: 0; }
blockquote > :last-child { margin-bottom: 0; }

/* ========== HORIZONTAL RULES ========== */
hr {
    height: 0.25em;
    padding: 0;
    margin: 24px 0;
    background-color: $borderColor;
    border: 0;
    border-radius: 2px;
}

/* ========== DETAILS / SUMMARY ========== */
details {
    margin-bottom: 16px;
    border: 1px solid $borderColor;
    border-radius: 6px;
    padding: 8px 12px;
    background-color: $cardBgColor;
}

summary {
    cursor: pointer;
    font-weight: 600;
    padding: 4px 0;
    user-select: none;
    -webkit-user-select: none;
}

details[open] summary {
    margin-bottom: 8px;
    border-bottom: 1px solid $borderColor;
    padding-bottom: 8px;
}

/* ========== GITHUB CALLOUTS (NOTE, WARNING, etc.) ========== */
.callout {
    margin-bottom: 16px;
    padding: 12px 16px;
    border-radius: 6px;
    border-left: 4px solid;
}

.callout-title {
    margin-bottom: 8px !important;
    font-size: 0.9em;
}

.callout-note {
    background-color: ${if (isDarkTheme) "rgba(56,139,253,0.1)" else "rgba(56,139,253,0.08)"};
    border-left-color: #388bfd;
}

.callout-warning {
    background-color: ${if (isDarkTheme) "rgba(210,153,34,0.1)" else "rgba(210,153,34,0.08)"};
    border-left-color: #d29922;
}

.callout-important {
    background-color: ${if (isDarkTheme) "rgba(160,80,255,0.1)" else "rgba(160,80,255,0.08)"};
    border-left-color: #a050ff;
}

.callout-tip {
    background-color: ${if (isDarkTheme) "rgba(56,203,137,0.1)" else "rgba(56,203,137,0.08)"};
    border-left-color: #38cb89;
}

.callout-caution {
    background-color: ${if (isDarkTheme) "rgba(248,81,73,0.1)" else "rgba(248,81,73,0.08)"};
    border-left-color: #f85149;
}

/* ========== ALIGNMENT (GitHub README centering) ========== */
div[align="center"], p[align="center"] {
    text-align: center;
}
div[align="right"], p[align="right"] {
    text-align: right;
}
div[align="left"], p[align="left"] {
    text-align: left;
}
p[align="middle"], div[align="middle"] {
    text-align: center;
}

/* ========== KEYBOARD SHORTCUTS ========== */
kbd {
    display: inline-block;
    padding: 3px 5px;
    font-size: 0.75em;
    line-height: 1;
    color: $textColor;
    vertical-align: middle;
    background-color: $codeBgColor;
    border: 1px solid $borderColor;
    border-radius: 6px;
    box-shadow: inset 0 -1px 0 $borderColor;
}

/* ========== STRIKETHROUGH ========== */
del, s {
    text-decoration: line-through;
    color: ${if (isDarkTheme) "rgba(255,255,255,0.4)" else "rgba(0,0,0,0.4)"};
}

/* ========== FOOTNOTES ========== */
sup {
    font-size: 0.75em;
    line-height: 0;
    vertical-align: super;
}

/* ========== MISC ========== */
.emoji {
    display: inline-block;
    vertical-align: text-top;
}

strong { font-weight: 600; }

/* Ensure centered image containers work */
div[align="center"] img,
p[align="center"] img,
p[align="middle"] img {
    display: inline-block;
    margin: 4px;
}

/* Picture element (GitHub dark/light mode images) */
picture {
    display: inline-block;
}

picture source, picture img {
    max-width: 100%;
}

/* ========== SCROLLBAR (desktop) ========== */
::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb {
    background: ${if (isDarkTheme) "rgba(255,255,255,0.2)" else "rgba(0,0,0,0.15)"};
    border-radius: 3px;
}

</style>
</head>
<body>
$body
<script>
// Report content height to native layer for auto-sizing
function reportHeight() {
    var height = Math.max(
        document.body.scrollHeight,
        document.body.offsetHeight,
        document.documentElement.scrollHeight,
        document.documentElement.offsetHeight
    );
    // Android: uses JavascriptInterface
    if (window.AndroidBridge && window.AndroidBridge.onContentHeight) {
        window.AndroidBridge.onContentHeight(height);
    }
    // Desktop JavaFX: uses alert with special prefix
    if (window.DesktopBridge) {
        window.DesktopBridge = height;
    }
    // Generic: store on window for polling
    window.__contentHeight = height;
}

// Run after images load too
window.addEventListener('load', function() {
    setTimeout(reportHeight, 100);
    setTimeout(reportHeight, 500);
    setTimeout(reportHeight, 1500);
});

// Also observe DOM changes (for lazy-loaded images)
if (window.MutationObserver) {
    new MutationObserver(reportHeight).observe(document.body, {
        childList: true, subtree: true, attributes: true
    });
}

// Report immediately for fast initial render
document.addEventListener('DOMContentLoaded', function() {
    setTimeout(reportHeight, 50);
});

// Intercept link clicks to open externally
document.addEventListener('click', function(e) {
    var target = e.target;
    while (target && target.tagName !== 'A') {
        target = target.parentElement;
    }
    if (target && target.href && !target.href.startsWith('javascript:')) {
        e.preventDefault();
        if (window.AndroidBridge && window.AndroidBridge.onLinkClick) {
            window.AndroidBridge.onLinkClick(target.href);
        }
    }
});
</script>
</body>
</html>
        """.trimIndent()
    }
}

/**
 * Converts an ARGB Int color to a CSS rgba() string.
 */
fun Int.toCssRgba(): String {
    val a = (this shr 24 and 0xFF) / 255f
    val r = this shr 16 and 0xFF
    val g = this shr 8 and 0xFF
    val b = this and 0xFF
    return "rgba($r, $g, $b, ${"%.2f".format(a)})"
}

/**
 * Composable helper that builds the themed HTML string from markdown,
 * automatically pulling colors from the current MaterialTheme.
 */
@Composable
fun rememberGitHubHtml(
    markdown: String,
    isDarkTheme: Boolean,
): String {
    val textColor = MaterialTheme.colorScheme.onBackground.toArgb()
    val backgroundColor = Color.Transparent.toArgb() // transparent so Compose bg shows through
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val codeBgColor = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
    val borderColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
    val cardBgColor = MaterialTheme.colorScheme.surfaceContainerLow.toArgb()

    return remember(markdown, isDarkTheme, textColor, linkColor) {
        GitHubMarkdownToHtml.convert(
            markdown = markdown,
            isDarkTheme = isDarkTheme,
            textColor = textColor,
            backgroundColor = backgroundColor,
            linkColor = linkColor,
            codeBgColor = codeBgColor,
            borderColor = borderColor,
            cardBgColor = cardBgColor,
        )
    }
}