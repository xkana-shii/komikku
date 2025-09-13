package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterItem
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.util.lang.toRelativeString
import exh.metadata.MetadataUtil
import exh.source.isEhBasedManga
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
fun ChapterListDialog(
    onDismissRequest: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
    chapters: ImmutableList<ReaderChapterItem>,
    onClickChapter: (Chapter) -> Unit,
    onBookmark: (Chapter) -> Unit,
    onFillermark: (Chapter) -> Unit,
    dateRelativeTime: Boolean,
) {
    val manga by screenModel.mangaFlow.collectAsState()
    val context = LocalContext.current
    val state = rememberLazyListState(chapters.indexOfFirst { it.isCurrent }.coerceAtLeast(0))
    val downloadManager: DownloadManager = remember { Injekt.get() }
    val downloadQueueState by downloadManager.queueState.collectAsState()
    val downloadStates by remember(downloadQueueState) {
        androidx.compose.runtime.derivedStateOf {
            downloadQueueState.associate { it.chapter.id to (it.status to it.progress) }
        }
    }
    val downloadProgressMap = remember { mutableStateMapOf<Long, Int>() }
    // Track manual deletions
    val manuallyDeletedMap = remember { mutableStateMapOf<Long, Boolean>() }

    // Observe download progress
    LaunchedEffect(Unit) {
        downloadManager.progressFlow()
            .collect { download ->
                downloadProgressMap[download.chapter.id] = download.progress
                // Any progress update means it's not manually deleted anymore
                manuallyDeletedMap.remove(download.chapter.id)
            }
    }
    // Observe download status to force completion state and progress=100
    LaunchedEffect(Unit) {
        downloadManager.statusFlow()
            .collect { download ->
                if (download.status == Download.State.DOWNLOADED) {
                    downloadProgressMap[download.chapter.id] = 100
                }
                // Any status update clears manual deletion flag
                manuallyDeletedMap.remove(download.chapter.id)
            }
    }

    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
    ) {
        LazyColumn(
            state = state,
            modifier = Modifier.heightIn(min = 200.dp, max = 500.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            items(
                items = chapters,
                key = { "chapter-list-${it.chapter.id}" },
            ) { chapterItem ->
                // Observe per-ID progress
                val observedProgress = downloadProgressMap[chapterItem.chapter.id] ?: 0
                val manuallyDeleted = manuallyDeletedMap[chapterItem.chapter.id] == true
                val downloaded = if (manga?.isLocal() == true) {
                    true
                } else {
                    downloadManager.isChapterDownloaded(
                        chapterItem.chapter.name,
                        chapterItem.chapter.scanlator,
                        chapterItem.manga.ogTitle,
                        chapterItem.manga.source,
                    )
                }
                val (downloadState, progress) = run {
                    val state = downloadStates[chapterItem.chapter.id]
                    val queueStatus = state?.first
                    val queueProgress = state?.second ?: 0
                    val mergedProgress = maxOf(observedProgress, queueProgress)

                    when {
                        manuallyDeleted -> Download.State.NOT_DOWNLOADED to 0
                        // Completed if manager says so, or merged progress hit 100, or disk check confirms
                        queueStatus == Download.State.ERROR -> Download.State.ERROR to 0
                        queueStatus == Download.State.DOWNLOADED || mergedProgress >= 100 || downloaded ->
                            Download.State.DOWNLOADED to 100
                        // Show ring while queued/downloading or when we have non-zero progress
                        queueStatus == Download.State.QUEUE || queueStatus == Download.State.DOWNLOADING || mergedProgress in 1..99 ->
                            Download.State.DOWNLOADING to mergedProgress

                        else -> Download.State.NOT_DOWNLOADED to 0
                    }
                }
                MangaChapterListItem(
                    title = chapterItem.chapter.name,
                    date = chapterItem.chapter.dateUpload
                        .takeIf { it > 0L }
                        ?.let {
                            // SY -->
                            if (manga?.isEhBasedManga() == true) {
                                MetadataUtil.EX_DATE_FORMAT
                                    .format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()))
                            } else {
                                LocalDate.ofInstant(
                                    Instant.ofEpochMilli(it),
                                    ZoneId.systemDefault(),
                                ).toRelativeString(context, dateRelativeTime, chapterItem.dateFormat)
                            }
                            // SY <--
                        },
                    readProgress = null,
                    scanlator = chapterItem.chapter.scanlator,
                    sourceName = null,
                    read = chapterItem.chapter.read,
                    bookmark = chapterItem.chapter.bookmark,
                    fillermark = chapterItem.chapter.fillermark,
                    selected = false,
                    downloadIndicatorEnabled = true,
                    downloadStateProvider = { downloadState },
                    downloadProgressProvider = { progress },
                    chapterSwipeStartAction = LibraryPreferences.ChapterSwipeAction.ToggleFillermark,
                    chapterSwipeEndAction = LibraryPreferences.ChapterSwipeAction.ToggleBookmark,
                    onLongClick = { /*TODO*/ },
                    onClick = { onClickChapter(chapterItem.chapter) },
                    onDownloadClick = { action ->
                        when (action) {
                            ChapterDownloadAction.START -> {
                                manuallyDeletedMap.remove(chapterItem.chapter.id)
                                downloadManager.downloadChapters(chapterItem.manga, listOf(chapterItem.chapter))
                            }
                            ChapterDownloadAction.START_NOW -> {
                                manuallyDeletedMap.remove(chapterItem.chapter.id)
                                downloadManager.startDownloadNow(chapterItem.chapter.id)
                            }
                            ChapterDownloadAction.CANCEL -> {
                                val queued = downloadQueueState.find { it.chapter.id == chapterItem.chapter.id }
                                if (queued != null) {
                                    downloadManager.cancelQueuedDownloads(listOf(queued))
                                    downloadProgressMap.remove(chapterItem.chapter.id)
                                }
                            }
                            ChapterDownloadAction.DELETE -> {
                                val sourceManager = Injekt.get<SourceManager>()
                                val source = sourceManager.get(chapterItem.manga.source)
                                if (source != null) {
                                    downloadManager.deleteChapters(listOf(chapterItem.chapter), chapterItem.manga, source)
                                    downloadProgressMap.remove(chapterItem.chapter.id)
                                    manuallyDeletedMap[chapterItem.chapter.id] = true
                                }
                            }
                        }
                    },
                    // KMK <--
                    onChapterSwipe = { action ->
                        if (action == LibraryPreferences.ChapterSwipeAction.ToggleBookmark) {
                            onBookmark(chapterItem.chapter)
                        }
                        if (action == LibraryPreferences.ChapterSwipeAction.ToggleFillermark) {
                            onFillermark(chapterItem.chapter)
                        }
                    },
                )
            }
        }
    }
}
