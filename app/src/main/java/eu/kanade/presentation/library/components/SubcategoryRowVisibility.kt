package eu.kanade.presentation.library.components

// KNS -->
/**
 * The subcategory row's own visibility, independent of the selected category: tapping the selected
 * category tab shows or hides the row, tapping another tab only switches category and keeps the row
 * as it is, so a hidden row stays hidden while moving between categories.
 */
object SubcategoryRowVisibility {

    /** What a tap on tab [tapped] does: the page to scroll to (null = stay) and the row's visibility. */
    data class TabTap(val scrollTo: Int?, val rowVisible: Boolean)

    fun onTabTap(tapped: Int, currentPage: Int, rowVisible: Boolean): TabTap =
        if (tapped == currentPage) TabTap(scrollTo = null, rowVisible = !rowVisible) else TabTap(tapped, rowVisible)
}
// KNS <--
