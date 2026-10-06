package tachiyomi.data.libraryUpdateError

import tachiyomi.data.Database
import tachiyomi.data.subscribeToList

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateErrorWithRelations
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorWithRelationsRepository

// KMK -->

class LibraryUpdateErrorWithRelationsRepositoryImpl(
    private val database: Database,
) : LibraryUpdateErrorWithRelationsRepository {

    override fun subscribeAll(): Flow<List<LibraryUpdateErrorWithRelations>> {
        return database.libraryUpdateErrorViewQueries.errors(
                libraryUpdateErrorWithRelationsMapper,
            ).subscribeToList()
    }
}
// KMK <--
