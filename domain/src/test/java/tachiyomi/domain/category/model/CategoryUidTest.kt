package tachiyomi.domain.category.model

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.service.chapterUrlBlacklistKey
import tachiyomi.domain.chapter.service.legacyFillermarkBlacklistKeys

// KNS -->
class CategoryUidTest {

    @Test
    fun `root categories keep the name based uid`() {
        Category.childUid(0L, "Reading") shouldBe Category.nameToUid("Reading")
    }

    @Test
    fun `same-named children under different parents get different uids`() {
        val shounen = Category.nameToUid("Shounen")
        val josei = Category.nameToUid("Josei")
        Category.childUid(shounen, "Reading") shouldNotBe Category.childUid(josei, "Reading")
        Category.childUid(shounen, "Reading") shouldNotBe Category.nameToUid("Reading")
    }

    @Test
    fun `child uids are deterministic, positive and ignore case and padding`() {
        val parent = Category.nameToUid("Shounen")
        val uid = Category.childUid(parent, "Reading")
        uid shouldBe Category.childUid(parent, "  reading ")
        (uid > 0L) shouldBe true
        Category.childUid(parent, "   ") shouldBe 0L
    }

    @Test
    fun `bookmark colors keep the previous fork's stored values`() {
        BookmarkColor.entries.map { it.value } shouldBe (0..6).toList()
        BookmarkColor.fromValue(4) shouldBe BookmarkColor.GREEN
        BookmarkColor.fromValue(99) shouldBe BookmarkColor.NONE
    }

    @Test
    fun `fillermark keys are exact url keys with escaped separators`() {
        chapterUrlBlacklistKey("/a,b%c") shouldBe "url:/a%2Cb%25c"
        legacyFillermarkBlacklistKeys(listOf("/x", "/x", "/y")) shouldBe listOf("url:/x", "url:/y")
    }
}
// KNS <--
