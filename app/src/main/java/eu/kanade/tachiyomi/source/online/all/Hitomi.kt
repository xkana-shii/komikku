package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.NamespaceSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import exh.GalleryAdder
import exh.metadata.metadata.HitomiSearchMetadata
import exh.metadata.metadata.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.util.urlImportFetchSearchManga
import exh.util.urlImportFetchSearchMangaSuspend
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.Response
import rx.Observable
import tachiyomi.core.common.util.lang.runAsObservable
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * SY-specific file
 */
@AssistedInject
class Hitomi(
    @Assisted delegate: HttpSource,
    val context: Context,
    override val getMangaId: MetadataSource.GetMangaId,
    override val insertFlatMetadata: MetadataSource.InsertFlatMetadata,
    override val getFlatMetadataById: MetadataSource.GetFlatMetadataById,
    private val galleryAdder: GalleryAdder,
) :
    DelegatedHttpSource(delegate),
    MetadataSource<HitomiSearchMetadata, Response>,
    UrlImportableSource,
    NamespaceSource {
    @AssistedFactory
    fun interface Factory {
        fun create(delegate: HttpSource): Hitomi
    }

    override val metaClass = HitomiSearchMetadata::class
    override fun newMetaInstance() = HitomiSearchMetadata()

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        urlImportFetchSearchMangaSuspend(context, galleryAdder, query) {
            super<DelegatedHttpSource>.getSearchManga(page, query, filters)
        }.let {
            MangasPage(
                it.mangas.onEach { it.initialized = false },
                it.hasNextPage,
            )
        }

    override suspend fun getPopularManga(page: Int): MangasPage = super.getPopularManga(page).let {
        MangasPage(
            it.mangas.onEach { it.initialized = false },
            it.hasNextPage,
        )
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = super.getLatestUpdates(page).let {
        MangasPage(
            it.mangas.onEach { it.initialized = false },
            it.hasNextPage,
        )
    }

    override val overridesMangaDetails = true

    private var cachedCdnDomain: String? = null
    private var cdnDomainFetchedAt: Long = 0

    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getMangaDetails"))
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return runAsObservable {
            val id = manga.url.substringAfterLast("-").substringBefore(".")
            val response = client.newCall(GET("https://ltn.gold-usergeneratedcontent.net/galleries/$id.js", headers)).awaitSuccess()
            parseToManga(manga, response)
        }
    }

    override suspend fun parseIntoMetadata(metadata: HitomiSearchMetadata, input: Response) {
        val gallery = jsonParser.decodeFromString<Gallery>(
            input.body.string().substringAfter("var galleryinfo = ").trim(),
        )
        println(Json.encodeToString(gallery))

        with(metadata) {
            hlId = gallery.id?.toString() ?: gallery.galleryurl.let { HitomiSearchMetadata.hlIdFromUrl(it) }

            tags.clear()

            title = gallery.title

            artists = gallery.artists?.map { it.artist } ?: emptyList()
            tags += artists.map { RaisedTag("artist", it, HitomiSearchMetadata.TAG_TYPE_DEFAULT) }

            gallery.groups?.forEach { group ->
                tags += RaisedTag("group", group.group, HitomiSearchMetadata.TAG_TYPE_DEFAULT)
            }

            genre = gallery.type
            genre?.let {
                tags += RaisedTag("genre", it, RaisedSearchMetadata.TAG_TYPE_VIRTUAL)
            }

            gallery.parodys?.forEach { parody ->
                tags += RaisedTag("series", parody.parody, HitomiSearchMetadata.TAG_TYPE_DEFAULT)
            }

            language = gallery.language
            language?.let {
                tags += RaisedTag("language", it, RaisedSearchMetadata.TAG_TYPE_VIRTUAL)
            }

            gallery.characters?.forEach { character ->
                tags += RaisedTag("character", character.character, HitomiSearchMetadata.TAG_TYPE_DEFAULT)
            }

            gallery.tags?.forEach { tag ->
                val ns = when {
                    tag.female?.content == "1" -> "female"
                    tag.male?.content == "1" -> "male"
                    else -> "misc"
                }
                tags += RaisedTag(ns, tag.tag, HitomiSearchMetadata.TAG_TYPE_DEFAULT)
            }

            pages = gallery.files.size

            thumbnailUrl = gallery.files.firstOrNull()?.let {
                HttpUrl.Builder()
                    .scheme("https")
                    .host(IMAGE_LOOPBACK_HOST)
                    .addQueryParameter(IMAGE_THUMBNAIL, "true")
                    .addQueryParameter(IMAGE_GIF, it.isGif.toString())
                    .fragment(it.hash)
                    .build()
                    .toString()
            }

            uploadDate = try {
                DATE_FORMAT.parse(gallery.date.substringBeforeLast("-"))?.time?.let { it / 1000 }
            } catch (_: Exception) {
                null
            }
        }
    }

    @Serializable
    data class Gallery(
        val id: Int? = null,
        val galleryurl: String,
        val title: String,
        val date: String,
        val type: String? = null,
        val language: String? = null,
        val tags: List<Tag>? = null,
        val artists: List<Artist>? = null,
        val groups: List<Group>? = null,
        val characters: List<Character>? = null,
        val parodys: List<Parody>? = null,
        val files: List<ImageFile>,
    )

    @Serializable
    data class Tag(
        val female: JsonPrimitive? = null,
        val male: JsonPrimitive? = null,
        val tag: String,
    )

    @Serializable
    data class Artist(val artist: String)

    @Serializable
    data class Group(val group: String)

    @Serializable
    data class Character(val character: String)

    @Serializable
    data class Parody(val parody: String)

    @Serializable
    class ImageFile(
        val hash: String,
        private val name: String,
    ) {
        val isGif get() = name.endsWith(".gif") || name.endsWith(".webp")
    }

    override val matchingHosts = listOf(
        "hitomi.la",
    )

    override suspend fun mapUrlToMangaUrl(uri: Uri): String? {
        val lcFirstPathSegment = uri.pathSegments.firstOrNull()?.lowercase() ?: return null

        if (lcFirstPathSegment != "manga" && lcFirstPathSegment != "reader") {
            return null
        }

        return "https://hitomi.la/manga/${uri.pathSegments[1].substringBefore('.')}.html"
    }

    companion object {
        val HITOMI_SOURCE_IDS = listOf(
            690123758188633713L, // all
            8758194323514078879L, // en
            1151065481637272954L, // id
            6508906416202704914L, // jv
            6511868096744396893L, // ca
            4174246577531268343L, // ceb
            6252839110189358321L, // cs
            846229581721942637L, // da
            3527959698638681794L, // de
            3137387913459348191L, // et
            4675241818028365305L, // es
            2806629479137588090L, // eo
            2485667029890093714L, // fr
            6889006204051558344L, // it
            1934919967422980093L, // hi
            5925216506653833737L, // hu
            721961972020678864L, // pl
            5342308195627947507L, // pt
            3818599797217502124L, // vi
            5379755699214980223L, // tr
            3909280091773603050L, // ru
            4074034220834832549L, // uk
            6776904750010591906L, // ar
            9053782715781498920L, // ko
            1859528116737393976L, // zh
            7152495211541141858L, // ja
        )

        private const val IMAGE_LOOPBACK_HOST = "127.0.0.1"
        private const val IMAGE_THUMBNAIL = "is_thumbnail"
        private const val IMAGE_GIF = "is_gif"

        private val jsonParser = Json {
            ignoreUnknownKeys = true
        }

        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        }
    }
}
