package tachiyomi.domain.libraryUpdateError.model

import kotlin.time.Instant

data class LibraryUpdateErrorWithRelations(
    val mangaId: Long,
    val mangaTitle: String,
    val mangaSource: Long,
    val favorite: Boolean,
    val mangaThumbnail: String?,
    val coverLastModified: Instant?,
    val errorId: Long,
    val messageId: Long,
    val lastUpdate: Long,
)
