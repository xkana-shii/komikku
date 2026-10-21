package eu.kanade.tachiyomi.data.track.anilist

import mihon.graphql.anilist.AniListGetMangaRelationsQuery
import tachiyomi.domain.manga.model.SequelPrequelEntry
import tachiyomi.domain.manga.model.SequelPrequelRelation

// KNS -->
/** One AniList relation edge, independent of the generated GraphQL classes. */
internal data class AnilistRelationEdge(
    val relationType: String?,
    val id: Int,
    val type: String?,
    val format: String?,
    val titles: List<String?>,
    val siteUrl: String?,
    val coverUrl: String?,
)

/**
 * AniList's MANGA media type includes light novels; their format tells them apart, so novels are left
 * out without guessing from titles. Entries without a usable title are dropped.
 */
internal fun List<AnilistRelationEdge>.toRelatedEntries(trackerId: Long): List<SequelPrequelEntry> =
    mapNotNull { edge ->
        if (edge.type != "MANGA" || edge.format == "NOVEL") return@mapNotNull null
        val title = edge.titles.firstOrNull { !it.isNullOrBlank() } ?: return@mapNotNull null
        SequelPrequelEntry(
            title = title,
            url = edge.siteUrl ?: "https://anilist.co/manga/${edge.id}",
            relation = SequelPrequelRelation.from(edge.relationType.orEmpty()),
            trackerId = trackerId,
            remoteId = edge.id.toLong(),
            coverUrl = edge.coverUrl,
        )
    }

internal fun AniListGetMangaRelationsQuery.Data.toRelationEdges(): List<AnilistRelationEdge> =
    Media?.relations?.edges.orEmpty().mapNotNull { edge ->
        val node = edge?.node ?: return@mapNotNull null
        AnilistRelationEdge(
            relationType = edge.relationType?.rawValue,
            id = node.id,
            type = node.type?.rawValue,
            format = node.format?.rawValue,
            titles = listOf(node.title?.userPreferred, node.title?.english, node.title?.romaji),
            siteUrl = node.siteUrl,
            coverUrl = node.coverImage?.large,
        )
    }
// KNS <--
