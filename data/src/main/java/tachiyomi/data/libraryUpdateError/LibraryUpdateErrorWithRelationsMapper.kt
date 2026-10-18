package tachiyomi.data.libraryUpdateError

import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateErrorWithRelations
import kotlin.time.Instant

val libraryUpdateErrorWithRelationsMapper:
    (Long, String, Long, Boolean, String?, Instant?, Long, Long, Long) -> LibraryUpdateErrorWithRelations =
    { mangaId, mangaTitle, mangaSource, favorite, mangaThumbnail, coverLastModified, errorId, messageId, lastUpdate ->
        LibraryUpdateErrorWithRelations(
            mangaId = mangaId,
            mangaTitle = mangaTitle,
            mangaSource = mangaSource,
            favorite = favorite,
            mangaThumbnail = mangaThumbnail,
            coverLastModified = coverLastModified,
            errorId = errorId,
            messageId = messageId,
            lastUpdate = lastUpdate,
        )
    }
