package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.core.common.extensions.toInstantOrNull
import tachiyomi.domain.history.model.History

@Serializable
data class BackupHistory(
    // SY -->
    @EncodeDefault
    @ProtoNumber(1) var url: String = "",
    @EncodeDefault
    @ProtoNumber(2) var lastRead: Long = 0,
    // SY <--
    @ProtoNumber(3) var readDuration: Long = 0,
) {
    fun getHistoryImpl(): History {
        return History.create().copy(
            readAt = lastRead.toInstantOrNull(),
            readDuration = readDuration,
        )
    }
}
