package tachiyomi.domain.manga.model

/**
 * SY-specific file
 *
 * The user's edited details for a single manga, used to update the manga row in one batch.
 */
data class MangaUserInfoUpdate(
    val id: Long,
    val title: String?,
    val author: String?,
    val artist: String?,
    val thumbnailUrl: String?,
    val description: String?,
    val genre: List<String>?,
    val status: Long?,
)

// KUN -->
/** The edited details a [Manga] carries, as the value the edit paths compare and write. */
fun Manga.toMangaUserInfoUpdate(): MangaUserInfoUpdate = MangaUserInfoUpdate(
    id = id,
    title = userTitle,
    author = userAuthor,
    artist = userArtist,
    thumbnailUrl = userThumbnailUrl,
    description = userDescription,
    genre = userGenre,
    status = userStatus,
)
// KUN <--
