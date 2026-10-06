package tachiyomi.data.libraryUpdateError

import app.cash.sqldelight.async.coroutines.awaitAsList
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateError
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorRepository

// KMK -->

class LibraryUpdateErrorRepositoryImpl(
    private val database: Database,
) : LibraryUpdateErrorRepository {

    override suspend fun getAll(): List<LibraryUpdateError> {
        return database.libraryUpdateErrorQueries.getAllErrors(libraryUpdateErrorMapper).awaitAsList()
    }

    override fun getAllAsFlow(): Flow<List<LibraryUpdateError>> {
        return database.libraryUpdateErrorQueries.getAllErrors(libraryUpdateErrorMapper).subscribeToList()
    }

    override suspend fun deleteAll() {
        database.libraryUpdateErrorQueries.deleteAllErrors()
    }

    override suspend fun delete(errorIds: List<Long>) {
        database.libraryUpdateErrorQueries.deleteErrors(_ids = errorIds)
    }

    override suspend fun deleteMangaError(mangaIds: List<Long>) {
        database.libraryUpdateErrorQueries.deleteMangaErrors(mangaIds = mangaIds)
    }

    override suspend fun cleanUnrelevantMangaErrors() {
        database.libraryUpdateErrorQueries.cleanUnrelevantMangaErrors()
    }

    override suspend fun upsert(libraryUpdateError: LibraryUpdateError) {
        database.transaction {
            database.libraryUpdateErrorQueries.upsert(
                mangaId = libraryUpdateError.mangaId,
                messageId = libraryUpdateError.messageId,
            )
        }
    }

    override suspend fun insert(libraryUpdateError: LibraryUpdateError) {
        database.transaction {
            database.libraryUpdateErrorQueries.insert(
                mangaId = libraryUpdateError.mangaId,
                messageId = libraryUpdateError.messageId,
            )
        }
    }

    override suspend fun insertAll(libraryUpdateErrors: List<LibraryUpdateError>) {
        database.transaction {
            libraryUpdateErrors.forEach {
                database.libraryUpdateErrorQueries.insert(
                    mangaId = it.mangaId,
                    messageId = it.messageId,
                )
            }
        }
    }
}
// KMK <--
