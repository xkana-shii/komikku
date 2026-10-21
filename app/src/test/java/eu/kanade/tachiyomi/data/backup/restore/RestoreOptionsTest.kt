// KUN -->
package eu.kanade.tachiyomi.data.backup.restore

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RestoreOptionsTest {

    @Test
    fun `pinned source options are off by default`() {
        val options = RestoreOptions()

        assertFalse(options.skipPinnedSources)
        assertFalse(options.replacePinnedSources)
    }

    @Test
    fun `pinned source options survive the worker boolean array`() {
        val replace = RestoreOptions(replacePinnedSources = true)
        val skip = RestoreOptions(skipPinnedSources = true)

        val restoredReplace = RestoreOptions.fromBooleanArray(replace.asBooleanArray())
        val restoredSkip = RestoreOptions.fromBooleanArray(skip.asBooleanArray())

        assertTrue(restoredReplace.replacePinnedSources)
        assertFalse(restoredReplace.skipPinnedSources)
        assertTrue(restoredSkip.skipPinnedSources)
        assertFalse(restoredSkip.replacePinnedSources)
    }

    @Test
    fun `older worker arrays default pinned source options to off`() {
        val legacyArray = booleanArrayOf(true, true, true, true, true, true, true, true, true)

        val restored = RestoreOptions.fromBooleanArray(legacyArray)

        assertFalse(restored.skipPinnedSources)
        assertFalse(restored.replacePinnedSources)
        assertEquals(RestoreOptions(), restored)
    }

    @Test
    fun `replace keeps its index so arrays from the previous revision stay valid`() {
        val previousRevisionArray = booleanArrayOf(
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true, // replacePinnedSources at index 9
        )

        val restored = RestoreOptions.fromBooleanArray(previousRevisionArray)

        assertTrue(restored.replacePinnedSources)
        assertFalse(restored.skipPinnedSources)
        assertArrayEquals(
            previousRevisionArray + booleanArrayOf(false),
            restored.asBooleanArray(),
        )
    }
}
// KUN <--
