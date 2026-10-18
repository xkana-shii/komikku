package eu.kanade.presentation.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Delete
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.chapterBlacklistKeyMetadata
import tachiyomi.domain.chapter.service.resolveChapterBlacklistMatches
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->
data class ChapterBlacklistEntry(
    val key: String,
    val chapter: Chapter?,
    val chapterNumber: Double?,
    val scanlator: String?,
    val matchingChapterCount: Int,
)

fun resolveChapterBlacklistEntries(keys: List<String>, chapters: List<Chapter>): List<ChapterBlacklistEntry> =
    keys.map { key ->
        val matches = resolveChapterBlacklistMatches(key, chapters)
        val chapter = matches.firstOrNull()
        val (chapterNumber, scanlator) = chapterBlacklistKeyMetadata(key)
        ChapterBlacklistEntry(
            key = key,
            chapter = chapter,
            chapterNumber = chapter?.chapterNumber?.takeIf { it.isFinite() && it > 0.0 } ?: chapterNumber,
            scanlator = chapter?.scanlator?.trim()?.takeIf(String::isNotBlank) ?: scanlator,
            matchingChapterCount = matches.size,
        )
    }

@Composable
fun ChapterBlacklistDialog(
    entries: List<ChapterBlacklistEntry>,
    onDismissRequest: () -> Unit,
    onRemove: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(KMR.strings.blacklisted_chapters)) },
        text = {
            if (entries.isEmpty()) {
                Text(stringResource(KMR.strings.blacklist_empty))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(entries, key = ChapterBlacklistEntry::key) { entry ->
                        val chapter = entry.chapter
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                                Text(
                                    text = entry.chapterNumber?.let {
                                        stringResource(KMR.strings.chapter_number_label, it.asChapterNumber())
                                    }
                                        ?: chapter?.name?.takeIf(String::isNotBlank)
                                        ?: stringResource(KMR.strings.blacklist_chapter_unavailable),
                                )
                                entry.scanlator?.takeIf(String::isNotBlank)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                                if (entry.matchingChapterCount > 1) {
                                    Text(
                                        stringResource(KMR.strings.blacklist_matching_chapters, entry.matchingChapterCount),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            IconButton(onClick = { onRemove(entry.key) }) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Delete,
                                    contentDescription = stringResource(MR.strings.action_remove),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_close)) }
        },
    )
}

@Composable
fun ChapterBlacklistEntries(
    entries: List<ChapterBlacklistEntry>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        Text(stringResource(KMR.strings.blacklist_empty), modifier = modifier)
    } else {
        LazyColumn(modifier = modifier) {
            items(entries, key = ChapterBlacklistEntry::key) { entry ->
                val chapter = entry.chapter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                        Text(
                            text = entry.chapterNumber?.let {
                                stringResource(KMR.strings.chapter_number_label, it.asChapterNumber())
                            }
                                ?: chapter?.name?.takeIf(String::isNotBlank)
                                ?: stringResource(KMR.strings.blacklist_chapter_unavailable),
                        )
                        entry.scanlator?.takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        if (entry.matchingChapterCount > 1) {
                            Text(
                                stringResource(KMR.strings.blacklist_matching_chapters, entry.matchingChapterCount),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    IconButton(onClick = { onRemove(entry.key) }) {
                        Icon(MaterialSymbols.Rounded.Delete, stringResource(MR.strings.action_remove))
                    }
                }
            }
        }
    }
}

private fun Double.asChapterNumber(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()
// KMK <--
