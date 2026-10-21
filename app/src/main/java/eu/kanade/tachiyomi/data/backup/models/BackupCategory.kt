package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.category.model.Category

@Serializable
class BackupCategory(
    // SY -->
    @ProtoNumber(1) var name: String = "",
    // SY <--
    @ProtoNumber(2) var order: Long = 0,
    @ProtoNumber(3) var id: Long = 0,
    // @ProtoNumber(3) val updateInterval: Int = 0, 1.x value not used in 0.x
    @ProtoNumber(100) var flags: Long = 0,
    // KMK -->
    @ProtoNumber(900) var hidden: Boolean = false,
    // KMK <--
    // KNS -->
    // 901–903 keep the previous personal fork's numbers and meaning so its backups restore.
    /** The parent's [id] in this backup (0 = root). */
    @ProtoNumber(901) var parentId: Long = 0L,
    /** Smart Category tracker status binding (unified `TrackStatus.int`). */
    @ProtoNumber(902) var trackStatus: Int? = null,
    /** Library update interval in days; null = global schedule. */
    @ProtoNumber(903) var updateInterval: Int? = null,
    /** The parent's sync uid, which identifies it across devices (0 = root or unknown). */
    @ProtoNumber(904) var parentUid: Long = 0L,
    @ProtoNumber(905) var dateAdded: Long = 0L,
    @ProtoNumber(906) var dateModified: Long = 0L,
    /**
     * Set by every backup this app writes. Without it (older backups, or a sync server that dropped
     * fields it does not know) a 0 parent means "unknown" rather than "root".
     */
    @ProtoNumber(907) var hierarchyKnown: Boolean = false,
    // KNS <--
    // SY specific values
    /*@ProtoNumber(600) var mangaOrder: List<Long> = emptyList(),*/
    // SY -->
    @ProtoNumber(601) var version: Long = 0,
    @ProtoNumber(602) var uid: Long = 0,
    @ProtoNumber(603) var lastModifiedAt: Long = 0,
    // SY <--
) {
    fun toCategory(id: Long) = Category(
        id = id,
        name = this@BackupCategory.name,
        flags = this@BackupCategory.flags,
        order = this@BackupCategory.order,
        // KMK -->
        hidden = this@BackupCategory.hidden,
        // KMK <--
        // SY -->
        version = this@BackupCategory.version,
        uid = this@BackupCategory.uid,
        lastModifiedAt = this@BackupCategory.lastModifiedAt,
        // SY <--
        // KNS -->
        // The parent is resolved by CategoriesRestorer; the backup's ids are not local ids.
        trackStatus = this@BackupCategory.trackStatus,
        updateInterval = this@BackupCategory.updateInterval,
        dateAdded = this@BackupCategory.dateAdded,
        dateModified = this@BackupCategory.dateModified,
        // KNS <--
        /*mangaOrder = this@BackupCategory.mangaOrder*/
    )
}

// KNS -->
/** Maps [category] including its parent's uid, looked up in [byId] (all categories). */
fun backupCategoryOf(category: Category, byId: Map<Long, Category>): BackupCategory =
    backupCategoryMapper(category).apply {
        parentId = category.parentId
        parentUid = byId[category.parentId]?.uid ?: 0L
        trackStatus = category.trackStatus
        updateInterval = category.updateInterval
        dateAdded = category.dateAdded
        dateModified = category.dateModified
        hierarchyKnown = true
    }
// KNS <--

val backupCategoryMapper = { category: Category ->
    BackupCategory(
        id = category.id,
        name = category.name,
        order = category.order,
        flags = category.flags,
        // KMK -->
        hidden = category.hidden,
        // KMK <--
        // SY -->
        version = category.version,
        uid = category.uid,
        lastModifiedAt = category.lastModifiedAt,
        // SY <--
    )
}
