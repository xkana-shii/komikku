package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * One explicit tombstone a SyncYomi v2 delta carries in `Backup.deletions` (field `11000`, the band
 * SyncYomi reserves for its own root-envelope fields): the kind of sync item, its merge key, the
 * `seq` at which the server recorded the deletion and, for a `history` entry, when it happened.
 *
 * Ported from TachiyomiSY (jobobby04/TachiyomiSY#1685).
 */
@Serializable
data class BackupDeletedItem(
    @ProtoNumber(1) val kind: String = "",
    @ProtoNumber(2) val key: String = "",
    @ProtoNumber(3) val seq: Long = 0,
    @ProtoNumber(4) val at: Long = 0,
)
