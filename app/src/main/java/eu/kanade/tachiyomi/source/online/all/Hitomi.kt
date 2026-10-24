package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.source.PagePreviewInfo
import eu.kanade.tachiyomi.source.PagePreviewPage
import eu.kanade.tachiyomi.source.PagePreviewSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.NamespaceSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import exh.metadata.metadata.HitomiSearchMetadata
import exh.metadata.metadata.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.util.urlImportFetchSearchMangaSuspend
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
    // KUN -->
    // The metadata accessors come from MetadataSource's defaults (resolved from the graph).
    // KUN <--
) :
    DelegatedHttpSource(delegate),
    MetadataSource<HitomiSearchMetadata, Response>,
    UrlImportableSource,
    NamespaceSource,
    PagePreviewSource {
    @AssistedFactory
    fun interface Factory {
        fun create(delegate: HttpSource): Hitomi
    }

    override val metaClass = HitomiSearchMetadata::class
    override fun newMetaInstance() = HitomiSearchMetadata()

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        // KUN -->
        // SY passes the gallery adder in; this fork's helper resolves it from the app graph.
        val mangasPage = urlImportFetchSearchMangaSuspend(context, query) {
            super<DelegatedHttpSource>.getSearchManga(page, query, filters)
        }
        // KUN <--
        return mangasPage.let {
            MangasPage(
                it.mangas.onEach { it.initialized = false },
                it.hasNextPage,
            )
        }
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

    // KUN -->
    // Read from the preview/Coil threads as well as the detail fetch.
    @Volatile private var cachedCdnDomain: String? = null

    @Volatile private var cdnDomainFetchedAt: Long = 0
    // KUN <--

    private suspend fun resolveCdnDomain(): String {
        val cached = cachedCdnDomain
        if (cached != null && (System.currentTimeMillis() - cdnDomainFetchedAt) < 3600_000L) {
            return cached
        }
        return try {
            val commonJs = client.newCall(GET("https://hitomi.la/common.js", headers)).awaitSuccess()
                .use { it.body.string() }
            val domain = Regex("domain2\\s*=\\s*'([^']+)'").find(commonJs)?.groupValues?.get(1)
                ?: DEFAULT_CDN_DOMAIN
            cachedCdnDomain = domain
            cdnDomainFetchedAt = System.currentTimeMillis()
            domain
        } catch (_: Exception) {
            cachedCdnDomain ?: DEFAULT_CDN_DOMAIN
        }
    }

    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getMangaDetails"))
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return runAsObservable {
            // KUN -->
            // parseToManga's result is post-processed below: the metadata keeps the preview carrier
            // URL (a hash on the loopback host, resolved by fetchPreviewImage), while the cover has
            // to point at the real thumbnail host, which is only known here.
            val parsed = parseToManga(manga, fetchGallery(manga))
            parsed.thumbnailUrl()
                ?.let { resolveThumbnailUrl(it) }
                ?.let { parsed.thumbnail_url = it }
            // KUN <--
            parsed
        }
    }

    // KUN -->
    private fun SManga.thumbnailUrl(): String? = thumbnail_url?.toHttpUrlOrNull()?.fragment

    /** The gallery's `galleries/<id>.js` payload, which is what [parseIntoMetadata] decodes. */
    private suspend fun fetchGallery(manga: SManga): Response {
        val cdnDomain = resolveCdnDomain()
        refreshGgScript()
        val id = manga.url.substringAfterLast("-").substringBefore(".")
        return client.newCall(GET("https://ltn.$cdnDomain/galleries/$id.js", headers)).awaitSuccess()
    }
    // KUN <--

    override suspend fun parseIntoMetadata(metadata: HitomiSearchMetadata, input: Response) {
        val gallery = jsonParser.decodeFromString<Gallery>(
            input.body.string().substringAfter("var galleryinfo = ").trim(),
        )

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

            pageHashes = gallery.files.map { it.hash }

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
        // KUN -->: the manifest also claims the www host
        "www.hitomi.la",
        // KUN <--
    )

    override suspend fun mapUrlToMangaUrl(uri: Uri): String? {
        val lcFirstPathSegment = uri.pathSegments.firstOrNull()?.lowercase() ?: return null

        // KUN -->
        // The manifest also claims /galleries/<id>.html (the canonical link form).
        if (lcFirstPathSegment !in setOf("manga", "reader", "galleries")) {
            return null
        }

        return uri.pathSegments.getOrNull(1)?.substringBefore('.')?.let { "https://hitomi.la/manga/$it.html" }
        // KUN <--
    }

    override suspend fun getPagePreviewList(manga: SManga, chapters: List<SChapter>, page: Int): PagePreviewPage {
        val metadata = fetchOrLoadMetadata(manga.id()) {
            // KUN -->
            // mangaDetailsRequest is the delegate's HTML gallery page; parseIntoMetadata decodes the
            // extension's `galleries/<id>.js` payload.
            fetchGallery(manga)
            // KUN <--
        }
        return PagePreviewPage(
            page,
            metadata.pageHashes.mapIndexed { index, hash ->
                PagePreviewInfo(
                    index + 1,
                    imageUrl = HttpUrl.Builder()
                        .scheme("https")
                        .host(IMAGE_LOOPBACK_HOST)
                        .addQueryParameter(IMAGE_THUMBNAIL, "true")
                        .fragment(hash)
                        .build()
                        .toString(),
                )
            },
            false,
            1,
        )
    }

    override suspend fun fetchPreviewImage(page: PagePreviewInfo, cacheControl: CacheControl?): Response {
        val hash = page.imageUrl.toHttpUrl().fragment
            ?: throw IllegalStateException("No hash in preview URL: ${page.imageUrl}")
        val url = resolveThumbnailUrl(hash)
        return client.newCachelessCallWithProgress(
            if (cacheControl != null) {
                GET(url, headers, cache = cacheControl)
            } else {
                GET(url, headers)
            },
            page,
        ).awaitSuccess()
    }

    // gg.js state (cached 60s, same as extension)
    @Volatile private var ggScriptLastRetrieval: Long = 0

    @Volatile private var ggSubdomainOffsetDefault = 0

    @Volatile private var ggSubdomainOffsetMap: Map<Int, Int> = emptyMap()

    private suspend fun refreshGgScript() {
        if (ggScriptLastRetrieval != 0L && (ggScriptLastRetrieval + 60_000) > System.currentTimeMillis()) return
        val cdnDomain = resolveCdnDomain()
        val ggScript = client.newCall(
            GET("https://ltn.$cdnDomain/gg.js?_=${System.currentTimeMillis()}", headers),
        ).awaitSuccess().use { it.body.string() }

        ggSubdomainOffsetDefault = Regex("var o = (\\d)").find(ggScript)?.groupValues?.get(1)?.toInt() ?: 0
        val o = Regex("o = (\\d); break;").find(ggScript)?.groupValues?.get(1)?.toInt() ?: 0
        ggSubdomainOffsetMap = Regex("case (\\d+):").findAll(ggScript).associate {
            it.groupValues[1].toInt() to o
        }
        ggScriptLastRetrieval = System.currentTimeMillis()
    }

    private fun imageIdFromHash(hash: String): Int {
        val match = Regex("(..)(.)$").find(hash) ?: return 0
        return (match.groupValues[2] + match.groupValues[1]).toInt(16)
    }

    private fun thumbPathFromHash(hash: String): String =
        hash.replace(Regex("""^.*(..)(.)$"""), "$2/$1")

    // KUN -->
    // suspend because the CDN domain and the gg.js offsets are only known after a detail fetch, and
    // previews can be opened straight from a gallery.
    private suspend fun resolveThumbnailUrl(hash: String): String {
        refreshGgScript()
        // KUN <--
        val cdnDomain = cachedCdnDomain ?: DEFAULT_CDN_DOMAIN
        val imageId = imageIdFromHash(hash)
        val offset = ggSubdomainOffsetMap[imageId] ?: ggSubdomainOffsetDefault
        val subDomain = "${'a' + offset}tn"
        return "https://$subDomain.$cdnDomain/avifsmalltn/${thumbPathFromHash(hash)}/$hash.avif"
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
        private const val DEFAULT_CDN_DOMAIN = "gold-usergeneratedcontent.net"

        private val jsonParser = Json {
            ignoreUnknownKeys = true
        }

        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        }
    }
}
