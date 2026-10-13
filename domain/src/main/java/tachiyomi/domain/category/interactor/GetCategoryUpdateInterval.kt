package tachiyomi.domain.category.interactor

import tachiyomi.domain.category.model.Category

// KMK -->
/** Resolves category update cadence without doing one database query per library entry. */
class GetCategoryUpdateInterval {

    companion object {
        fun intervalDaysFor(categories: Collection<Category>, categoryIds: Set<Long>): Int? {
            if (categoryIds.isEmpty()) return null
            val byId = categories.associateBy(Category::id)
            return categoryIds.mapNotNull { categoryId ->
                var category = byId[categoryId]
                val visited = mutableSetOf<Long>()
                while (category != null && visited.add(category.id)) {
                    category.updateInterval?.let { return@mapNotNull it }
                    category = byId[category.parentId]
                }
                null
            }.minOrNull()
        }

        fun shortestConfiguredIntervalDays(categories: Collection<Category>): Int? =
            categories.mapNotNull(Category::updateInterval).minOrNull()
    }
}
// KMK <--
