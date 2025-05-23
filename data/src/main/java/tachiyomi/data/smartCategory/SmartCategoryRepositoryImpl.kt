package tachiyomi.data.smartCategory

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.domain.smartCategory.model.SmartCategory
import tachiyomi.domain.smartCategory.repository.SmartCategoryRepository

class SmartCategoryRepositoryImpl(
    private val handler: DatabaseHandler,
) : SmartCategoryRepository {
    override suspend fun getAll(): List<SmartCategory> {
        return handler.awaitList {
            smart_categoryQueries.selectAll(SmartCategoryMapper::mapSmartCategory)
        }
    }

    override suspend fun subscribeGetAll(): Flow<List<SmartCategory>> {
        return handler.subscribeToList { smart_categoryQueries.selectAll(SmartCategoryMapper::mapSmartCategory) }
    }

    override suspend fun getByCategoryId(categoryId: Long): SmartCategory? {
        return handler.awaitOneOrNull {
            smart_categoryQueries.select(categoryId, SmartCategoryMapper::mapSmartCategory)
        }
    }

    override suspend fun subscribeGetByCategoryId(categoryId: Long): Flow<SmartCategory?> {
        return handler.subscribeToOneOrNull {
            smart_categoryQueries.select(categoryId, SmartCategoryMapper::mapSmartCategory)
        }
    }

    override suspend fun insert(categoryId: Long, tags: List<String>) {
        return handler.await(inTransaction = true) {
            smart_categoryQueries.insert(
                categoryId = categoryId,
                tags = tags,
            )
        }
    }

    override suspend fun upsert(categoryId: Long, tags: List<String>) {
        return handler.await(inTransaction = true) {
            smart_categoryQueries.upsert(
                categoryId = categoryId,
                tags = tags,
            )
        }
    }

    override suspend fun update(categoryId: Long, tags: List<String>) {
        handler.await(inTransaction = true) {
            smart_categoryQueries.update(
                categoryId = categoryId,
                tags = StringListColumnAdapter.encode(tags),
            )
        }
    }

    override suspend fun delete(categoryId: Long) {
        handler.await {
            smart_categoryQueries.delete(
                categoryId = categoryId,
            )
        }
    }
}
