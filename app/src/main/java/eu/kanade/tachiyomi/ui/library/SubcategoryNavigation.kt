package eu.kanade.tachiyomi.ui.library

import androidx.compose.runtime.Immutable
import tachiyomi.domain.category.model.Category

// KNS -->
/** Subcategory chips of a top-level library tab. */
@Immutable
data class SubcategoryNavigation(
    val root: Category,
    /** Selected categories below [root], outermost first; empty = the whole tab is shown. */
    val path: List<Category>,
    /** Children of the deepest selected category (or of [root]). */
    val children: List<Category>,
)
// KNS <--
