package tachiyomi.domain.smartCategory.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.smartCategory.model.SmartCategory

interface SmartCategoryRepository {

    suspend fun getAll(): List<SmartCategory>

    suspend fun subscribeGetAll(): Flow<List<SmartCategory>>

    suspend fun getByCategoryId(categoryId: Long): SmartCategory?

    suspend fun subscribeGetByCategoryId(categoryId: Long): Flow<SmartCategory?>

    suspend fun insert(categoryId: Long, tags: List<String>)

    suspend fun upsert(categoryId: Long, tags: List<String>)

    suspend fun update(categoryId: Long, tags: List<String>)

    suspend fun delete(categoryId: Long)
}
