package tachiyomi.domain.library.model

// KNS -->
/** What a library refresh started while a subcategory is selected covers. */
enum class SubcategoryUpdateScope {
    /** The whole top-level category with all its subcategories. */
    WHOLE_CATEGORY,

    /** Only the selected subcategory and the subcategories below it. */
    SUBCATEGORY_ONLY,
}
// KNS <--
