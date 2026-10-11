package eu.kanade.presentation.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
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
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.isChapterBlacklisted
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
) {
    val isAvailable: Boolean
        get() = chapter != null
}

fun resolveChapterBlacklistEntries(
    keys: List<String>,
    chapters: List<Chapter>,
): List<ChapterBlacklistEntry> {
    return keys.map { key ->
        val matches = chapters.filter { isChapterBlacklisted(it, listOf(key)) }
        val chapter = matches.firstOrNull()
        val keyNumber = key.substringBefore('@').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
        val keyScanlator = key.substringAfter('@', "").trim().takeIf(String::isNotEmpty)
        ChapterBlacklistEntry(
            key = key,
            chapter = chapter,
            chapterNumber = chapter?.chapterNumber?.takeIf { it.isFinite() && it > 0.0 } ?: keyNumber,
            scanlator = chapter?.scanlator?.trim()?.takeIf(String::isNotEmpty) ?: keyScanlator,
            matchingChapterCount = matches.size,
        )
    }
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
            ChapterBlacklistEntries(
                entries = entries,
                onRemove = onRemove,
                modifier = Modifier.heightIn(max = 360.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_close))
            }
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
        Text(stringResource(KMR.strings.blacklist_empty))
    } else {
        LazyColumn(modifier = modifier) {
            items(entries, key = ChapterBlacklistEntry::key) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                    ) {
                        val chapter = entry.chapter
                        val number = entry.chapterNumber
                        if (number != null) {
                            Text(
                                text = stringResource(KMR.strings.chapter_number_label, number.asChapterNumber()),
                            )
                            chapter?.name?.takeIf { it.isNotBlank() }?.let { name ->
                                Text(text = name, style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text(
                                text = chapter?.name?.takeIf { it.isNotBlank() }
                                    ?: stringResource(KMR.strings.blacklist_chapter_unavailable),
                            )
                        }
                        entry.scanlator?.let { scanlator ->
                            Text(
                                text = scanlator,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (entry.matchingChapterCount > 1) {
                            Text(
                                text = stringResource(
                                    KMR.strings.blacklist_matching_chapters,
                                    entry.matchingChapterCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    IconButton(onClick = { onRemove(entry.key) }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(MR.strings.action_remove),
                        )
                    }
                }
            }
        }
    }
}

private fun Double.asChapterNumber(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()
// KMK <--
