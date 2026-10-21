package eu.kanade.tachiyomi.data.track.model

import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.source.model.SManga

// KNS -->
/**
 * Provider tag names as local manga tags: trimmed, blanks dropped and case-insensitive duplicates
 * removed (the first spelling wins); null when nothing is left, so autofill keeps the current tags.
 */
fun autofillTags(tags: Iterable<String?>): List<String>? =
    tags.mapNotNull { it?.trim()?.ifEmpty { null } }
        .distinctBy { it.lowercase() }
        .ifEmpty { null }

/**
 * The editor's tags after autofill: the current tags unchanged and in order, then the fetched tags
 * they do not already contain (compared case-insensitively).
 */
fun mergeAutofillTags(current: List<String>, fetched: List<String>?): List<String> {
    val known = current.mapTo(HashSet()) { it.trim().lowercase() }
    return current + autofillTags(fetched.orEmpty()).orEmpty().filter { known.add(it.lowercase()) }
}

/** The statuses of the editor's status spinner, in order after its "Default" entry. */
private val EDITOR_STATUSES = listOf(
    SManga.ONGOING,
    SManga.COMPLETED,
    SManga.LICENSED,
    SManga.PUBLISHING_FINISHED,
    SManga.CANCELLED,
    SManga.ON_HIATUS,
)

/** The editor's status spinner position for a fetched status; null keeps the current selection. */
fun autofillStatusSelection(status: Long?): Int? =
    status?.let { EDITOR_STATUSES.indexOf(it.toInt()) }?.takeIf { it >= 0 }?.plus(1)

/** Whether [this] has anything autofill can apply to the editor. */
fun TrackMangaMetadata.hasAutofillData(): Boolean =
    listOf(title, authors, artists, thumbnailUrl, description).any { !it.isNullOrBlank() } ||
        !tags.isNullOrEmpty() ||
        autofillStatusSelection(status) != null

/**
 * The bindings autofill can use: trackers that can fetch series metadata and are logged in. Enhanced
 * trackers (Komga, Kavita, Suwayomi) do not, so they are left out by capability, not by type.
 */
fun <T> List<Pair<T, Tracker>>.autofillEligible(): List<Pair<T, Tracker>> =
    filter { (_, tracker) -> tracker.supportsMangaMetadata && tracker.isLoggedIn }
// KNS <--
