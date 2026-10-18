package tachiyomi.data.libraryUpdateError

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateErrorWithRelations
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorWithRelationsRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class LibraryUpdateErrorWithRelationsRepositoryImpl(
    private val database: Database,
) : LibraryUpdateErrorWithRelationsRepository {

    override fun subscribeAll(): Flow<List<LibraryUpdateErrorWithRelations>> {
        return database.libraryUpdateErrorViewQueries
            .errors(
                libraryUpdateErrorWithRelationsMapper,
            )
            .subscribeToList()
    }
}
