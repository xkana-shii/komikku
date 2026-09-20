package tachiyomi.data.manga

import android.content.Context
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.manga.model.CustomMangaInfo
import java.io.File

class CustomMangaRepositoryTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `custom tags and status survive reload and clearing last entry persists`() {
        val context = mockk<Context> { every { getExternalFilesDir(null) } returns directory }
        val edits = CustomMangaInfo(id = 7, genre = emptyList(), status = 2, title = "Custom title")
        CustomMangaRepositoryImpl(context).set(edits)
        val reloaded = CustomMangaRepositoryImpl(context)
        reloaded.get(7) shouldBe edits
        reloaded.set(CustomMangaInfo(id = 7, title = null))
        CustomMangaRepositoryImpl(context).get(7) shouldBe null
    }
}
