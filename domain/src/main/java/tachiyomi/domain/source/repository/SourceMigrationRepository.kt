package tachiyomi.domain.source.repository

/**
 * Repoints rows that reference a source when a source's id changes (the delegated E-Hentai /
 * NHentai migrations).
 */
interface SourceMigrationRepository {

    /**
     * Moves every manga, merged entry, saved search and feed that referenced [oldId] to [newId].
     */
    suspend fun migrateSource(newId: Long, oldId: Long)
}
