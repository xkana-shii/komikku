package tachiyomi.data.source

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.repository.StubSourceRepository

// KMK -->

class StubSourceRepositoryImpl(
    private val database: Database,
) : StubSourceRepository {

    override fun subscribeAll(): Flow<List<StubSource>> {
        return database.sourceQueries.findAll(::mapStubSource).subscribeToList()
    }

    override suspend fun getStubSource(id: Long): StubSource? {
        return database.sourceQueries.findOne(id, ::mapStubSource).awaitAsOneOrNull()
    }

    override suspend fun upsertStubSource(id: Long, lang: String, name: String) {
        database.sourceQueries.upsert(id = id, name = name, language = lang)
    }

    private fun mapStubSource(
        id: Long,
        name: String,
        lang: String,
    ): StubSource = StubSource(id = id, lang = lang, name = name)
}
// KMK <--
