package zed.rainxch.details.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun MarkdownWebView(
    html: String,
    modifier: Modifier = Modifier,
    onLinkClick: ((url: String) -> Unit)? = null,
)