package eu.kanade.tachiyomi.data.backup.create.creators

import app.cash.sqldelight.async.coroutines.awaitAsList
import tachiyomi.data.Database

import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.backupFeedMapper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

class FeedBackupCreator(
    private val database: Database = Injekt.get(),
) {

    /**
     * Backup:
     * - Global Popular/Latest feeds
     * - Global feeds from saved searches
     * - Source's feeds from saved searches
     */
    suspend operator fun invoke(): List<BackupFeed> {
        return database.feed_saved_searchQueries.selectAllFeedWithSavedSearch(backupFeedMapper).awaitAsList()
    }
}
// KMK <--
