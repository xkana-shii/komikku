package exh.ui.metadata.adapters

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.tachiyomi.ui.manga.MangaViewModel.State
import exh.metadata.metadata.NineHentaiSearchMetadata
import exh.util.SourceTagsUtil.genreTextColor
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Book
import mihon.icons.materialsymbols.rounded.Favorite
import mihon.icons.materialsymbols.rounded.Schedule
import mihon.icons.materialsymbols.roundedfilled.Visibility
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.text.NumberFormat
import kotlin.time.Instant

/**
 * Inline metadata panel for the delegated NineHentai source.
 *
 * Unlike the other description panels (which host the legacy XML layouts through an [androidx.compose.ui.viewinterop.AndroidView]),
 * this one is plain Compose: it is new code and only shows a handful of facts.
 */
@Composable
fun NineHentaiDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val meta = state.meta as? NineHentaiSearchMetadata ?: return
    val context = LocalContext.current
    val category = remember(meta.tags) {
        meta.tags.find { it.namespace == NineHentaiSearchMetadata.NAMESPACE_CATEGORY }?.name
    }
    val genre = remember(category) {
        category?.let { MetadataUIUtil.getGenreAndColour(context, it.lowercase()) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            category?.let {
                Text(
                    text = genre?.second?.let { res -> stringResource(res) } ?: it,
                    color = genre?.first?.let { color -> Color(genreTextColor(color)) } ?: Color.Unspecified,
                    modifier = Modifier
                        .background(
                            color = genre?.first?.let { color -> Color(color.color) }
                                ?: MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = openMetadataViewer) {
                Text(text = stringResource(SYMR.strings.more_info))
            }
        }

        val favorites = meta.favorites
        val views = meta.views
        if (favorites != null || views != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                favorites?.let {
                    DescriptionFact(
                        icon = MaterialSymbols.Rounded.Favorite,
                        text = pluralStringResource(
                            SYMR.plurals.num_favorites,
                            it,
                            NumberFormat.getIntegerInstance().format(it),
                        ),
                    )
                }
                views?.let {
                    DescriptionFact(
                        icon = MaterialSymbols.RoundedFilled.Visibility,
                        text = pluralStringResource(
                            SYMR.plurals.num_views,
                            it,
                            NumberFormat.getIntegerInstance().format(it),
                        ),
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            meta.pageCount?.let {
                DescriptionFact(
                    icon = MaterialSymbols.Rounded.Book,
                    text = pluralStringResource(SYMR.plurals.num_pages, it, it),
                )
            }
            meta.uploadDate?.let {
                DescriptionFact(
                    icon = MaterialSymbols.Rounded.Schedule,
                    text = relativeDateText(
                        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date,
                    ),
                )
            }
        }
    }
}

