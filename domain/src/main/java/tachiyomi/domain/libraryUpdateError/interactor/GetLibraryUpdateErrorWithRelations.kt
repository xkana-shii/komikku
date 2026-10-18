package tachiyomi.domain.libraryUpdateError.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateErrorWithRelations
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorWithRelationsRepository

@Inject
class GetLibraryUpdateErrorWithRelations(
    private val libraryUpdateErrorWithRelationsRepository: LibraryUpdateErrorWithRelationsRepository,
) {

    fun subscribeAll(): Flow<List<LibraryUpdateErrorWithRelations>> {
        return libraryUpdateErrorWithRelationsRepository.subscribeAll()
    }
}
