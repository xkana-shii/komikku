package mihon.feature.library

import eu.kanade.tachiyomi.ui.library.LibraryItem
import exh.metadata.sql.models.SearchTag
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mihon.domain.library.model.search.AndNode
import mihon.domain.library.model.search.ComparisonField
import mihon.domain.library.model.search.ComparisonQueryNode
import mihon.domain.library.model.search.EmptyQueryNode
import mihon.domain.library.model.search.FieldQueryNode
import mihon.domain.library.model.search.GeneralQueryNode
import mihon.domain.library.model.search.MangaField
import mihon.domain.library.model.search.MetadataQueryNode
import mihon.domain.library.model.search.NotNode
import mihon.domain.library.model.search.OrNode
import mihon.domain.library.model.search.QueryNode
import tachiyomi.source.local.LocalSource
import kotlin.math.abs
import kotlin.time.Instant

// KUN -->
/**
 * The EH/NHentai flat metadata of a library entry that [MetadataQueryNode]s are matched against.
 * [uploader] comes from `search_metadata`, [tags] from `search_tags`.
 */
data class LibrarySearchMetadata(
    val uploader: String?,
    val tags: List<SearchTag>,
)

/**
 * Whether the query contains a TachiyomiSY metadata namespace term. The app layer uses this to
 * avoid loading the EH/NHentai metadata at all for ordinary queries.
 */
fun QueryNode.containsMetadataQuery(): Boolean = when (this) {
    is AndNode -> children.any { it.containsMetadataQuery() }
    is OrNode -> children.any { it.containsMetadataQuery() }
    is NotNode -> child.containsMetadataQuery()
    is MetadataQueryNode -> true
    is EmptyQueryNode, is GeneralQueryNode, is FieldQueryNode, is ComparisonQueryNode -> false
}

/**
 * Resolves a metadata namespace term against [metadata]. A non-negated term matches only when the
 * entry has metadata containing the tag (or uploader); a negated term also matches an entry without
 * any metadata, mirroring TachiyomiSY's search engine.
 */
private fun MetadataQueryNode.matchesMetadata(metadata: LibrarySearchMetadata?): Boolean {
    val match = when {
        metadata == null -> false
        namespace == "uploader" -> {
            metadata.uploader?.contains(value, ignoreCase = true) == true ||
                metadata.tags.any { it.matchesMetadataTag(namespace, value) }
        }
        else -> metadata.tags.any { it.matchesMetadataTag(namespace, value) }
    }
    return if (negated) !match else match
}

private fun SearchTag.matchesMetadataTag(namespace: String, value: String): Boolean =
    this.namespace.equals(namespace, ignoreCase = true) && name.contains(value, ignoreCase = true)
// KUN <--

fun QueryNode.matches(item: LibraryItem, metadata: LibrarySearchMetadata? = null): Boolean {
    return when (this) {
        is AndNode -> children.all { it.matches(item, metadata) }
        is OrNode -> children.any { it.matches(item, metadata) }
        is NotNode -> !child.matches(item, metadata)
        is EmptyQueryNode -> true
        is GeneralQueryNode -> matches(item)
        is FieldQueryNode -> matches(item)
        is ComparisonQueryNode -> matches(item)
        // KUN -->
        is MetadataQueryNode -> matchesMetadata(metadata)
        // KUN <--
    }
}

private fun GeneralQueryNode.matches(item: LibraryItem): Boolean {
    val manga = item.libraryManga.manga

    // Use when so each added field has to be handled explicitly
    val match = MangaField.entries.any { field ->
        if (field.fieldOnly) return@any false

        when (field) {
            MangaField.TITLE -> manga.title.matchesSearchTerm(value)
            MangaField.AUTHOR -> manga.author?.matchesSearchTerm(value) ?: false
            MangaField.ARTIST -> manga.artist?.matchesSearchTerm(value) ?: false
            MangaField.DESCRIPTION -> manga.description?.matchesSearchTerm(value) ?: false
            MangaField.GENRE -> manga.genre?.any { it.matchesSearchTerm(value) } ?: false
            MangaField.SOURCE -> {
                item.sourceName.matchesSearchTerm(value) ||
                    (value.equals("local", ignoreCase = true) && manga.source == LocalSource.ID)
            }
            MangaField.NOTES -> manga.notes.matchesSearchTerm(value)

            // field-only queries; unreachable; added here to make `when` exhaustive
            MangaField.LANGUAGE, MangaField.SOURCE_ID -> error("How did we get here?")
        }
    }
    return if (negated) !match else match
}

private fun FieldQueryNode.matches(item: LibraryItem): Boolean {
    val manga = item.libraryManga.manga

    val match = when (field) {
        MangaField.GENRE -> {
            if (value.isEmpty()) {
                manga.genre.isNullOrEmpty()
            } else {
                manga.genre?.any { it.matchesSearchTerm(value) } ?: false
            }
        }

        MangaField.SOURCE -> {
            if (value.isEmpty()) {
                item.sourceName.isEmpty()
            } else {
                item.sourceName.matchesSearchTerm(value) ||
                    (value.equals("local", ignoreCase = true) && manga.source == LocalSource.ID)
            }
        }

        MangaField.SOURCE_ID -> {
            value.toLongOrNull()?.let { it == manga.source } ?: false
        }

        else -> {
            val text = when (field) {
                MangaField.TITLE -> manga.title
                MangaField.AUTHOR -> manga.author
                MangaField.ARTIST -> manga.artist
                MangaField.DESCRIPTION -> manga.description
                MangaField.NOTES -> manga.notes
                MangaField.LANGUAGE -> item.sourceLanguage

                // unreachable; added here to make `when` exhaustive
                MangaField.GENRE, MangaField.SOURCE, MangaField.SOURCE_ID -> error("How did we get here?")
            }

            if (value.isEmpty()) {
                text.isNullOrEmpty()
            } else {
                text?.matchesSearchTerm(value) ?: false
            }
        }
    }

    return if (negated) !match else match
}

private fun ComparisonQueryNode.matches(item: LibraryItem): Boolean {
    val manga = item.libraryManga.manga

    fun compareDates(date: Instant, value: String): Boolean? {
        val inputDate = runCatching { LocalDate.parse(value) }.getOrNull() ?: return null
        val mangaDate = date.toLocalDateTime(TimeZone.currentSystemDefault()).date
        return queryComparator.apply(mangaDate, inputDate)
    }

    val match = when (field) {
        ComparisonField.ID -> value.toLongOrNull()?.let { queryComparator.apply(manga.id, it) }

        // KUN --> upstream mihon/main calls this field `favoriteAt`; the fork still names it `dateAdded`
        ComparisonField.DATE_ADDED -> manga.dateAdded?.let { compareDates(it, value) }
        // KUN <--

        ComparisonField.FETCH_INTERVAL -> value.toIntOrNull()
            ?.let { queryComparator.apply(abs(manga.fetchInterval), it) }

        ComparisonField.NEXT_UPDATE -> manga.nextUpdate?.let { compareDates(it, value) }

        ComparisonField.UNREAD -> {
            value.toLongOrNull()?.let {
                queryComparator.apply(item.unreadCount, it)
            }
        }

        ComparisonField.READ -> {
            value.toLongOrNull()?.let {
                queryComparator.apply(item.libraryManga.readCount, it)
            }
        }

        ComparisonField.TOTAL -> {
            value.toLongOrNull()?.let {
                queryComparator.apply(item.libraryManga.totalChapters, it)
            }
        }
    } ?: false

    return if (negated) !match else match
}

// KUN -->
/**
 * TachiyomiSY compatibility: restore the old wildcard matching. A term containing `*`/`%` (any
 * sequence of characters) or `?` (exactly one character) is compiled to a regex and matched
 * anywhere in the text, mirroring the old search engine's SQL `LIKE` handling. Every other term
 * keeps the plain, case-insensitive substring match. (The old engine's `_` single-character
 * wildcard is intentionally not restored: `_` is too common in real titles/URLs.)
 */
private fun String.matchesSearchTerm(term: String): Boolean =
    if (term.contains('*') || term.contains('?') || term.contains('%')) {
        matchesWildcard(term)
    } else {
        contains(term, ignoreCase = true)
    }

private fun String.matchesWildcard(term: String): Boolean {
    val pattern = buildString {
        term.forEach { char ->
            when (char) {
                '*', '%' -> append(".*")
                '?' -> append('.')
                else -> append(Regex.escape(char.toString()))
            }
        }
    }
    return Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(this)
}
// KUN <--
