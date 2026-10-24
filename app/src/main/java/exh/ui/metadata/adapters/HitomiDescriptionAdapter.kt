package exh.ui.metadata.adapters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.tachiyomi.ui.manga.MangaViewModel.State
import exh.metadata.MetadataUtil
import exh.metadata.metadata.HitomiSearchMetadata
import exh.util.capitalize
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.CalendarMonth
import mihon.icons.sy.materialsymbols.SYMaterialSymbols
import mihon.icons.sy.materialsymbols.rounded.Translate
import mihon.icons.sy.materialsymbols.roundedfilled.MenuBook
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * SY-specific file
 */
@Composable
fun HitomiDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val meta = state.meta as? HitomiSearchMetadata ?: return
    val context = LocalContext.current
    val unknown = stringResource(MR.strings.unknown)

    MetadataDescription {
        MetadataRow(openMetadataViewer = openMetadataViewer) {
            val genre = remember(meta.genre) { meta.genre?.let { MetadataUIUtil.getGenreAndColour(context, it) } }
            MetadataGenre(
                text = genre?.second ?: meta.genre ?: unknown,
                color = genre?.first,
            )
            MetadataChip(
                text = meta.language?.capitalize() ?: unknown,
                icon = SYMaterialSymbols.Rounded.Translate,
            )
            meta.hlId?.let {
                MetadataChip(text = "#$it")
            }
        }

        MetadataFacts {
            fact {
                val pages = meta.pages ?: 0
                MetadataText(
                    text = pluralStringResource(SYMR.plurals.num_pages, pages, pages),
                    icon = SYMaterialSymbols.RoundedFilled.MenuBook,
                )
            }
            // KUN -->
            // An unknown upload date has no meaningful placeholder, so drop the fact entirely
            // instead of rendering 1970.
            meta.uploadDate?.let { uploadedAt ->
                fact {
                    val whenPosted = remember(uploadedAt) {
                        MetadataUtil.EX_DATE_FORMAT.format(
                            ZonedDateTime.ofInstant(
                                Instant.fromEpochSeconds(uploadedAt).toJavaInstant(),
                                ZoneId.systemDefault(),
                            ),
                        )
                    }
                    MetadataText(
                        text = whenPosted,
                        icon = MaterialSymbols.Rounded.CalendarMonth,
                    )
                }
            }
            // KUN <--
        }

        meta.uploader?.takeIf { it.isNotEmpty() }?.let { uploader ->
            MetadataFacts {
                fact {
                    MetadataText(text = uploader)
                }
            }
        }
    }
}
