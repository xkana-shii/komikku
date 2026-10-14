package tachiyomi.data.libraryUpdateErrorMessage

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.libraryUpdateErrorMessage.model.LibraryUpdateErrorMessage
import tachiyomi.domain.libraryUpdateErrorMessage.repository.LibraryUpdateErrorMessageRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class LibraryUpdateErrorMessageRepositoryImpl(
    private val database: Database,
) : LibraryUpdateErrorMessageRepository {

    override suspend fun getAll(): List<LibraryUpdateErrorMessage> {
        return database.libraryUpdateErrorMessageQueries
            .getAllErrorMessages(
                LibraryUpdateErrorMessageMapper,
            )
            .awaitAsList()
    }

    override fun getAllAsFlow(): Flow<List<LibraryUpdateErrorMessage>> {
        return database.libraryUpdateErrorMessageQueries
            .getAllErrorMessages(
                LibraryUpdateErrorMessageMapper,
            )
            .subscribeToList()
    }

    override suspend fun deleteAll() {
        database.libraryUpdateErrorMessageQueries.deleteAllErrorMessages()
    }

    override suspend fun get(message: String): Long? {
        return database.libraryUpdateErrorMessageQueries
            .getErrorMessages(message) { id, _ -> id }
            .awaitAsOneOrNull()
    }

    override suspend fun insert(libraryUpdateErrorMessage: LibraryUpdateErrorMessage): Long {
        return database.libraryUpdateErrorMessageQueries
            .insertAndGet(libraryUpdateErrorMessage.message)
            .awaitAsOne()
    }

    override suspend fun insertAll(
        libraryUpdateErrorMessages: List<LibraryUpdateErrorMessage>,
    ): List<Pair<Long, String>> {
        return database.transactionWithResult {
            libraryUpdateErrorMessages.map {
                database.libraryUpdateErrorMessageQueries
                    .insertAndGet(it.message)
                    .awaitAsOne() to it.message
            }
        }
    }
}
