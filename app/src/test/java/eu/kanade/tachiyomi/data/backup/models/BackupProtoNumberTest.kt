// KUN -->
package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Guards the fork-reserved protobuf block (`10000-10999`, see AGENTS.md "Backup protobuf field
 * numbers"). Upstream claims and shipped backups make these numbers permanent, so accidental
 * renumbering must fail the build rather than silently change the wire format.
 */
@OptIn(ExperimentalSerializationApi::class)
class BackupProtoNumberTest {

    @Test
    fun `komi-kun backup fields keep their reserved proto numbers`() {
        val numbers = protoNumbers(BackupManga.serializer().descriptor)

        assertEquals(10000, numbers["customInfoIncluded"])
        assertEquals(10001, numbers["mergedMangaReferencesIncluded"])
    }

    // KNS -->
    @Test
    fun `fork backup fields keep the previous personal fork's proto numbers`() {
        val manga = protoNumbers(BackupManga.serializer().descriptor)
        assertEquals(614, manga["scanlatorPriority"])
        assertEquals(615, manga["blacklistedChapters"])
        assertEquals(616, manga["scanlatorRangeRules"])
        assertEquals(618, manga["smartScanlatorMergeOverride"])
        assertEquals(620, manga["scanlatorSettingsPresent"])

        val chapter = protoNumbers(BackupChapter.serializer().descriptor)
        assertEquals(14, chapter["bookmarkColor"])
        assertEquals(15, chapter["fillermark"])
    }
    // KNS <--

    private fun protoNumbers(descriptor: SerialDescriptor): Map<String, Int> =
        (0 until descriptor.elementsCount)
            .mapNotNull { index ->
                val number = descriptor.getElementAnnotations(index)
                    .filterIsInstance<ProtoNumber>()
                    .singleOrNull()
                    ?.number
                number?.let { descriptor.getElementName(index) to it }
            }
            .toMap()
}
// KUN <--
