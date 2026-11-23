// KUN -->
package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.Modifier
import java.util.jar.JarFile

/**
 * Guards the fork-reserved protobuf block (`10000-10999`, see AGENTS.md "Backup protobuf field
 * numbers"). Upstream claims and shipped backups make these numbers permanent, so accidental
 * renumbering must fail the build rather than silently change the wire format.
 */
@OptIn(ExperimentalSerializationApi::class)
class BackupProtoNumberTest {

    @Test
    fun `komi-kun backup fields keep their reserved proto numbers`() {
        val numbers = protoNumbers(BackupManga.serializer().descriptor).toMap()

        assertEquals(10000, numbers["customInfoIncluded"])
        assertEquals(10001, numbers["mergedMangaReferencesIncluded"])
        // KNS --> Komi-kun #80: merge settings at 10002; 600 keeps carrying the merge entries
        assertEquals(10002, numbers["mergeSettings"])
        assertEquals(600, numbers["mergedEntries"])
        // KNS <--
    }

    // KNS -->
    @Test
    fun `fork backup fields keep the previous personal fork's proto numbers`() {
        val manga = protoNumbers(BackupManga.serializer().descriptor).toMap()
        assertEquals(614, manga["scanlatorPriority"])
        assertEquals(615, manga["blacklistedChapters"])
        assertEquals(616, manga["scanlatorRangeRules"])
        assertEquals(618, manga["smartScanlatorMergeOverride"])
        assertEquals(620, manga["scanlatorSettingsPresent"])

        val chapter = protoNumbers(BackupChapter.serializer().descriptor).toMap()
        assertEquals(14, chapter["bookmarkColor"])
        assertEquals(15, chapter["fillermark"])
    }
    // KNS <--

    @Test
    fun `the SyncYomi tombstone list stays at the server's field number`() {
        // Wire compatibility with the SyncYomi server, which writes the v2 delta's explicit
        // tombstones at this root-envelope tag; renumbering it would lose every deletion.
        val numbers = protoNumbers(Backup.serializer().descriptor).toMap()

        assertEquals(11000, numbers["deletions"])
    }

    @Test
    fun `every backup field number is unique and is the fork block or a documented upstream number`() {
        val descriptors = backupSerializers()

        assertTrue(
            descriptors.size >= 10,
            "expected every Backup* model in ${BackupManga::class.java.packageName}, found " +
                descriptors.map { it.serialName },
        )

        val upstreamNumbersInUse = mutableSetOf<Int>()

        for (descriptor in descriptors) {
            val numbers = protoNumbers(descriptor)

            val reused = numbers.groupBy { it.second }.filterValues { it.size > 1 }
            assertEquals(
                emptyMap<Int, List<Pair<String, Int>>>(),
                reused,
                "${descriptor.serialName} reuses a proto number",
            )

            val nonFork = numbers.filter { (_, number) -> number !in FORK_BLOCK }
            val unknown = nonFork.filter { (_, number) ->
                number !in UPSTREAM_NUMBERS && number !in SYNCYOMI_RESERVED &&
                    // KNS -->
                    number !in KNS_SHIPPED_NUMBERS && number !in KNS_BLOCK
                // KNS <--
            }
            assertEquals(
                emptyList<Pair<String, Int>>(),
                unknown,
                "${descriptor.serialName} has a field number outside the fork block $FORK_BLOCK " +
                    "that is neither an upstream allocation $UPSTREAM_NUMBERS nor in SyncYomi's " +
                    "reserved band $SYNCYOMI_RESERVED",
            )
            upstreamNumbersInUse += nonFork.map { it.second }.filter { it in UPSTREAM_NUMBERS }
        }

        // Both directions: a new low number fails above, and dropping or renumbering an upstream
        // field (which would break decoding of shipped backups) fails here.
        assertEquals(
            UPSTREAM_NUMBERS,
            upstreamNumbersInUse,
            "the models no longer use exactly the upstream proto numbers AGENTS.md documents",
        )
    }

    private fun protoNumbers(descriptor: SerialDescriptor): List<Pair<String, Int>> =
        (0 until descriptor.elementsCount)
            .mapNotNull { index ->
                val number = descriptor.getElementAnnotations(index)
                    .filterIsInstance<ProtoNumber>()
                    .singleOrNull()
                    ?.number
                number?.let { descriptor.getElementName(index) to it }
            }

    /**
     * Every `Backup*` model in this package, discovered from the compiled classes next to
     * [BackupManga] so a newly added model is covered without editing this test.
     */
    private fun backupSerializers(): List<SerialDescriptor> {
        val packagePath = BackupManga::class.java.packageName.replace('.', '/')
        val location = File(BackupManga::class.java.protectionDomain.codeSource.location.toURI())
        val classNames = compiledClassNames(location, packagePath)
            .filter { it.startsWith("Backup") && it.endsWith(".class") && '$' !in it }

        return classNames.sorted().mapNotNull { name ->
            val type = Class.forName(
                BackupManga::class.java.packageName + "." + name.removeSuffix(".class"),
                false,
                BackupManga::class.java.classLoader,
            )
            // The serialization plugin puts `serializer()` on the model's companion object, which is
            // what `BackupManga.serializer()` resolves to. A plain Kotlin class (file facade, value
            // holder) has no companion and is skipped.
            val companion = type.declaredFields
                .firstOrNull { it.name == "Companion" && Modifier.isStatic(it.modifiers) }
                ?.also { it.isAccessible = true }
                ?.get(null)
                ?: return@mapNotNull null
            val serializer = companion.javaClass.methods
                .firstOrNull { it.name == "serializer" && it.parameterCount == 0 }
                ?.invoke(companion)
                ?: return@mapNotNull null
            when (serializer) {
                is SerialDescriptor -> serializer
                is KSerializer<*> -> serializer.descriptor
                else -> null
            }
        }
    }

    /** Simple file names of the compiled classes of [packagePath], from a class directory or a jar. */
    private fun compiledClassNames(location: File, packagePath: String): List<String> {
        if (location.isDirectory) {
            val packageDir = File(location, packagePath)
            assertTrue(packageDir.isDirectory, "cannot locate the compiled backup models at $packageDir")
            return packageDir.listFiles().orEmpty().map { it.name }
        }
        return JarFile(location).use { jar ->
            jar.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("$packagePath/") && !it.removePrefix("$packagePath/").contains('/') }
                .map { it.removePrefix("$packagePath/") }
                .toList()
        }
    }

    private companion object {
        /** komi-kun's reserved block (AGENTS.md "Backup protobuf field numbers"). */
        val FORK_BLOCK = 10000..10999

        /**
         * Upstream numbers no current model uses: `15` is the 1.x manga `flags` field and `102` the
         * legacy history model, both kept as commented-out `// retired:` lines in `BackupManga`.
         */
        val RETIRED_UPSTREAM_NUMBERS: Set<Int> = setOf(15, 102)

        /**
         * Every upstream number the current models use (AGENTS.md "Upstream ownership"): Mihon
         * `1-18` and `100-113`, TachiyomiSY `600-603`, Komikku `610`, the J2K-via-SY `800-805` block
         * (`803` is skipped — an earlier build used a duplicate, and no model uses it) and Komikku
         * `900`, minus [RETIRED_UPSTREAM_NUMBERS]. Open upstream claims (`604`, `611`, `650`, `806`,
         * `807`, `901`) are deliberately absent: no model uses them, so a field carrying one is a
         * mistake, not an allocation.
         */
        val UPSTREAM_NUMBERS: Set<Int> =
            ((1..18).toSet() + (100..113) + (600..603) + setOf(610, 800, 801, 802, 804, 805, 900)) -
                RETIRED_UPSTREAM_NUMBERS

        /**
         * `11000-11099` belongs to SyncYomi, not to komi-kun: `Backup.deletions` is the root-envelope
         * field 11000 where a v2 delta lists its explicit tombstones, and the rest of the band is
         * SyncYomi's to allocate. It sits outside [FORK_BLOCK] on purpose, so this stays an exception
         * to komi-kun's `10000-10999` reservation rather than an extension of it: a komi-kun field
         * still may not take a number in this band, and no komi-kun allocation moves or is reused.
         *
         * These numbers are matched separately rather than added to [UPSTREAM_NUMBERS], because the
         * exact-equality assertion below is about upstream's allocations being kept, and a
         * SyncYomi-owned field is not one.
         */
        val SYNCYOMI_RESERVED = 11000..11099

        // KNS -->
        /** This fork's block for new fields (AGENTS.md "This fork's (KNS) fields"). */
        val KNS_BLOCK = 20000..20999

        /** Numbers shipped by the previous personal fork; they stay so its backups restore. */
        val KNS_SHIPPED_NUMBERS: Set<Int> = setOf(14, 15, 114, 614, 615, 616, 618, 620, 901, 902, 903, 904, 905, 906, 907)
        // KNS <--
    }
}
// KUN <--
