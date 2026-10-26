package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupPreference(
    // SY -->
    @EncodeDefault
    @ProtoNumber(1) val key: String = "",
    // SY <--
    @ProtoNumber(2) val value: PreferenceValue,
)

@Serializable
data class BackupSourcePreferences(
    // SY -->
    @EncodeDefault
    @ProtoNumber(1) val sourceKey: String = "",
    @EncodeDefault
    @ProtoNumber(2) val prefs: List<BackupPreference> = emptyList(),
    // SY <--
)

@Serializable
sealed class PreferenceValue

// SY -->
@Serializable
data class IntPreferenceValue(@EncodeDefault val value: Int = 0) : PreferenceValue()

@Serializable
data class LongPreferenceValue(@EncodeDefault val value: Long = 0) : PreferenceValue()

@Serializable
data class FloatPreferenceValue(@EncodeDefault val value: Float = 0F) : PreferenceValue()

@Serializable
data class StringPreferenceValue(@EncodeDefault val value: String = "") : PreferenceValue()

@Serializable
data class BooleanPreferenceValue(@EncodeDefault val value: Boolean = false) : PreferenceValue()

@Serializable
data class StringSetPreferenceValue(@EncodeDefault val value: Set<String> = emptySet()) : PreferenceValue()
// SY <--
