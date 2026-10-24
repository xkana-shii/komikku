package exh.ui.metadata.adapters

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import eu.kanade.tachiyomi.ui.manga.MangaViewModel.State
import exh.metadata.MetadataUtil
import exh.metadata.metadata.HitomiSearchMetadata
import exh.util.SourceTagsUtil.genreTextColor
import exh.util.capitalize
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Book
import mihon.icons.materialsymbols.rounded.CalendarMonth
import mihon.icons.materialsymbols.rounded.Person
import mihon.icons.materialsymbols.rounded.Translate
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * Inline metadata panel for the delegated Hitomi source.
 */
@Composable
fun HitomiDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val meta = state.meta as? HitomiSearchMetadata ?: return
    val context = LocalContext.current
    val unknown = stringResource(MR.strings.unknown)
    val genre = remember(meta.genre) { meta.genre?.let { MetadataUIUtil.getGenreAndColour(context, it) } }

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
            Text(
                text = genre?.second ?: meta.genre ?: unknown,
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
            GenreChip(text = meta.language?.capitalize() ?: unknown, icon = MaterialSymbols.Rounded.Translate)
            meta.hlId?.let { GenreChip(text = "#$it") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = openMetadataViewer) {
                Text(text = stringResource(SYMR.strings.more_info))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val pages = meta.pages ?: 0
            DescriptionFact(
                icon = MaterialSymbols.Rounded.Book,
                text = pluralStringResource(SYMR.plurals.num_pages, pages, pages),
            )
            meta.uploadDate?.let { uploadedAt ->
                val whenPosted = remember(uploadedAt) {
                    MetadataUtil.EX_DATE_FORMAT.format(
                        ZonedDateTime.ofInstant(
                            Instant.fromEpochSeconds(uploadedAt).toJavaInstant(),
                            ZoneId.systemDefault(),
                        ),
                    )
                }
                DescriptionFact(icon = MaterialSymbols.Rounded.CalendarMonth, text = whenPosted)
            }
        }

        meta.uploader?.takeIf { it.isNotEmpty() }?.let { uploader ->
            DescriptionFact(icon = MaterialSymbols.Rounded.Person, text = uploader)
        }
    }
}

@Composable
private fun GenreChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .padding(vertical = 4.dp, horizontal = 8.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = text, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
    }
}
