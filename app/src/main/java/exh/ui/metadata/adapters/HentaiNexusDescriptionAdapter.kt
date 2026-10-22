package exh.ui.metadata.adapters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.tachiyomi.ui.manga.MangaViewModel.State
import exh.metadata.metadata.HentaiNexusSearchMetadata
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Book
import mihon.icons.materialsymbols.rounded.CalendarMonth
import mihon.icons.materialsymbols.rounded.Domain
import mihon.icons.materialsymbols.rounded.Favorite
import mihon.icons.materialsymbols.rounded.Festival
import mihon.icons.materialsymbols.rounded.Group
import mihon.icons.materialsymbols.rounded.Newspaper
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.text.NumberFormat
import kotlin.time.Instant

/**
 * Inline metadata panel for the delegated HentaiNexus source.
 */
@Composable
fun HentaiNexusDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val meta = state.meta as? HentaiNexusSearchMetadata ?: return
    val namespaces = remember(meta.tags) {
        meta.tags.groupBy({ it.namespace }, { it.name }).mapValues { (_, names) -> names.joinToString() }
    }
    val publisher = namespaces[HentaiNexusSearchMetadata.NAMESPACE_PUBLISHER]
    val magazine = namespaces[HentaiNexusSearchMetadata.NAMESPACE_MAGAZINE]
    val circle = namespaces[HentaiNexusSearchMetadata.NAMESPACE_CIRCLE]
    val event = namespaces[HentaiNexusSearchMetadata.NAMESPACE_EVENT]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = openMetadataViewer) {
                Text(text = stringResource(SYMR.strings.more_info))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            meta.favorites?.let {
                DescriptionFact(
                    icon = MaterialSymbols.Rounded.Favorite,
                    text = pluralStringResource(
                        SYMR.plurals.num_favorites,
                        it,
                        NumberFormat.getIntegerInstance().format(it),
                    ),
                )
            }
            meta.publishedDate?.let {
                DescriptionFact(
                    icon = MaterialSymbols.Rounded.CalendarMonth,
                    text = relativeDateText(
                        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date,
                    ),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            meta.pageCount?.let {
                DescriptionFact(
                    icon = MaterialSymbols.Rounded.Book,
                    text = pluralStringResource(SYMR.plurals.num_pages, it, it),
                )
            }
            publisher?.let { DescriptionFact(icon = MaterialSymbols.Rounded.Domain, text = it) }
        }

        if (magazine != null || circle != null || event != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                magazine?.let { DescriptionFact(icon = MaterialSymbols.Rounded.Newspaper, text = it) }
                circle?.let { DescriptionFact(icon = MaterialSymbols.Rounded.Group, text = it) }
                event?.let { DescriptionFact(icon = MaterialSymbols.Rounded.Festival, text = it) }
            }
        }
    }
}
