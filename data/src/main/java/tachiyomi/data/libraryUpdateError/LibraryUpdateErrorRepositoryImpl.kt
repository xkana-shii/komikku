package tachiyomi.data.libraryUpdateError

import app.cash.sqldelight.async.coroutines.awaitAsList
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateError
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class LibraryUpdateErrorRepositoryImpl(
    private val database: Database,
) : LibraryUpdateErrorRepository {

    override suspend fun getAll(): List<LibraryUpdateError> {
        return database.libraryUpdateErrorQueries
            .getAllErrors(libraryUpdateErrorMapper)
            .awaitAsList()
    }

    override fun getAllAsFlow(): Flow<List<LibraryUpdateError>> {
        return database.libraryUpdateErrorQueries
            .getAllErrors(libraryUpdateErrorMapper)
            .subscribeToList()
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
