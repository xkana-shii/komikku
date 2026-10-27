package eu.kanade.tachiyomi.ui.manga.merged

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.tachiyomi.source.Source
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.DeleteMergeById
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.interactor.UpdateMergedSettings
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergeMangaSettingsUpdate
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.sy.SYMR

/**
 * SY-specific file
 */
@AssistedInject
class EditMergedSettingsViewModel(
    @Assisted private val mangaId: Long,
    private val getMergedMangaById: GetMergedMangaById,
    private val getMergedReferencesById: GetMergedReferencesById,
    private val sourceManager: SourceManager,
    private val updateMergedSettings: UpdateMergedSettings,
    private val deleteMergeById: DeleteMergeById,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long): EditMergedSettingsViewModel
    }

    sealed interface State {
        data object Loading : State

        data class Ready(
            val selfReference: MergedMangaReference?,
            val entries: List<Entry>,
            val pendingConfirm: PendingConfirm?,
        ) : State {
            val sortMode: Int
                get() = selfReference?.chapterSortMode ?: MergedMangaReference.CHAPTER_SORT_NONE

            val isPriorityOrder: Boolean
                get() = sortMode == MergedMangaReference.CHAPTER_SORT_PRIORITY

            val infoIndex: Int
                get() = entries.indexOfFirst { it.reference.isInfoManga }.coerceAtLeast(0)

            // A merge needs at least one underlying entry in addition to the self-reference
            val isInvalid: Boolean
                get() = (entries.size + if (selfReference != null) 1 else 0) <= 1
        }
    }

    data class Entry(
        val manga: Manga?,
        val reference: MergedMangaReference,
        val source: Source,
    )

    sealed class PendingConfirm(val title: StringResource, val text: StringResource) {
        data class Delete(val reference: MergedMangaReference) :
            PendingConfirm(SYMR.strings.delete_merged_entry, SYMR.strings.delete_merged_entry_desc)

        data class Downloads(val id: Long) :
            PendingConfirm(SYMR.strings.download_merged_entry, SYMR.strings.download_merged_entry_desc)

        data class Updates(val id: Long) :
            PendingConfirm(SYMR.strings.chapter_updates_merged_entry, SYMR.strings.chapter_updates_merged_entry_desc)
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Loads the merge's references and underlying entries. Triggered by the dialog while
     * [state] is [State.Loading] — on first show and after a dismiss/save.
     */
    fun reload() {
        viewModelScope.launch {
            val manga = getMergedMangaById.await(mangaId)
            val references = getMergedReferencesById.await(mangaId)
            _state.value = buildReady(manga, references)
        }
    }

    /**
     * Marks the cached data as stale so the next show reloads it.
     */
    fun markStale() {
        _state.value = State.Loading
    }

    private suspend fun buildReady(manga: List<Manga>, references: List<MergedMangaReference>): State.Ready {
        val selfReference = references.firstOrNull { it.mangaSourceId == MERGED_SOURCE_ID }
        val entries = references
            .filter { it.mangaSourceId != MERGED_SOURCE_ID }
            .sortedBy { it.chapterPriority }
            .let { sorted ->
                // The info manga defaults to the first entry when none is set yet
                if (sorted.none { it.isInfoManga }) {
                    sorted.mapIndexed { index, reference -> reference.copy(isInfoManga = index == 0) }
                } else {
                    sorted
                }
            }
            .map { reference ->
                val manga = manga.firstOrNull { it.id == reference.mangaId }
                Entry(
                    manga = manga,
                    reference = reference,
                    source = sourceManager.getOrStub(manga?.source ?: -1),
                )
            }
        return State.Ready(selfReference, entries, null)
    }

    fun onSortModeChange(mode: Int) {
        _state.update { current ->
            val ready = current as? State.Ready ?: return@update current
            ready.copy(selfReference = ready.selfReference?.copy(chapterSortMode = mode))
        }
    }

    fun onInfoIndexChange(index: Int) {
        _state.update { current ->
            val ready = current as? State.Ready ?: return@update current
            val infoId = ready.entries.getOrNull(index)?.reference?.id
            ready.copy(
                entries = ready.entries.map {
                    it.copy(reference = it.reference.copy(isInfoManga = it.reference.id == infoId))
                },
            )
        }
    }

    /**
     * Moves an entry and rewrites the priority of every entry. [from] and [to] are indices into
     * [State.Ready.entries] (the list header is already excluded by the caller).
     */
    fun reorder(from: Int, to: Int) {
        _state.update { current ->
            val ready = current as? State.Ready ?: return@update current
            val moved = ready.entries.toMutableList()
            moved.add(to, moved.removeAt(from))
            ready.copy(
                entries = moved.mapIndexed { index, entry ->
                    entry.copy(reference = entry.reference.copy(chapterPriority = index))
                },
            )
        }
    }

    fun requestDelete(reference: MergedMangaReference) {
        setPendingConfirm(PendingConfirm.Delete(reference))
    }

    fun requestToggleDownloads(id: Long) {
        setPendingConfirm(PendingConfirm.Downloads(id))
    }

    fun requestToggleUpdates(id: Long) {
        setPendingConfirm(PendingConfirm.Updates(id))
    }

    fun dismissPending() {
        setPendingConfirm(null)
    }

    fun confirmPending() {
        val ready = _state.value as? State.Ready ?: return
        val confirm = ready.pendingConfirm ?: return
        when (confirm) {
            is PendingConfirm.Delete -> {
                setPendingConfirm(null)
                viewModelScope.launch {
                    deleteMergeById.await(confirm.reference.id)
                }
            }
            is PendingConfirm.Downloads -> {
                setPendingConfirm(null)
                updateEntry(confirm.id) { it.copy(downloadChapters = !it.downloadChapters) }
            }
            is PendingConfirm.Updates -> {
                setPendingConfirm(null)
                updateEntry(confirm.id) { it.copy(getChapterUpdates = !it.getChapterUpdates) }
            }
        }
    }

    fun save() {
        val ready = _state.value as? State.Ready ?: return
        val references = listOfNotNull(ready.selfReference) + ready.entries.map { it.reference }
        viewModelScope.launch {
            if (references.isNotEmpty()) {
                updateMergedSettings.awaitAll(
                    references.map {
                        MergeMangaSettingsUpdate(
                            id = it.id,
                            isInfoManga = it.isInfoManga,
                            getChapterUpdates = it.getChapterUpdates,
                            chapterPriority = it.chapterPriority,
                            downloadChapters = it.downloadChapters,
                            chapterSortMode = it.chapterSortMode,
                        )
                    },
                )
            }
            _state.value = State.Loading
        }
    }

    private fun setPendingConfirm(confirm: PendingConfirm?) {
        _state.update { current ->
            val ready = current as? State.Ready ?: return@update current
            ready.copy(pendingConfirm = confirm)
        }
    }

    private fun updateEntry(id: Long, transform: (MergedMangaReference) -> MergedMangaReference) {
        _state.update { current ->
            val ready = current as? State.Ready ?: return@update current
            ready.copy(
                entries = ready.entries.map {
                    if (it.reference.id == id) it.copy(reference = transform(it.reference)) else it
                },
            )
        }
    }
}
