package zed.rainxch.details.presentation.components.sections

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import githubstore.feature.details.presentation.generated.resources.Res
import githubstore.feature.details.presentation.generated.resources.about_this_app
import io.github.fletchmckee.liquid.liquefiable
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.details.presentation.components.MarkdownWebView
import zed.rainxch.details.presentation.components.rememberGitHubHtml
import zed.rainxch.details.presentation.utils.LocalTopbarLiquidState

fun LazyListScope.about(
    readmeMarkdown: String,
    readmeLanguage: String?,
) {
    item(key = "about_header") {
        val liquidState = LocalTopbarLiquidState.current

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.about_this_app),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.liquefiable(liquidState)
            )

            readmeLanguage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.liquefiable(liquidState)
                )
            }
        }
    }

    item(key = "about_content") {
        ReadmeWebView(
            markdown = readmeMarkdown
        )
    }
}

@Composable
private fun ReadmeWebView(markdown: String) {
    val liquidState = LocalTopbarLiquidState.current

    val isDarkTheme = isSystemInDarkTheme()
    val uriHandler = LocalUriHandler.current

    val html = rememberGitHubHtml(
        markdown = markdown,
        isDarkTheme = isDarkTheme,
    )

    MarkdownWebView(
        html = html,
        modifier = Modifier
            .fillMaxWidth()
            .liquefiable(liquidState),
        onLinkClick = { url ->
            uriHandler.openUri(url)
        }
    )
}