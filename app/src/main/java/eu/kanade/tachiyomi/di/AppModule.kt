package eu.kanade.tachiyomi.di

import android.app.ActivityManager
import android.app.Application
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConcurrencyModel.MultipleReadersSingleWriter
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConnectionFactory
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.DefaultAndroidxSqliteConnectionFactory
import com.eygraber.sqldelight.androidx.driver.FileProvider
import com.eygraber.sqldelight.androidx.driver.SqliteJournalMode
import eu.kanade.domain.track.store.DelayedTrackingStore
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.BackupRestoreStatus
import eu.kanade.tachiyomi.data.LibraryUpdateStatus
import eu.kanade.tachiyomi.data.SyncStatus
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.cache.PagePreviewCache
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveService
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.webhook.WebhookNotifier
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.AndroidSourceManager
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import exh.eh.EHentaiUpdateHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.core.archive.CbzCrypto
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.storage.AndroidStorageFolderProvider
import tachiyomi.core.common.storage.UniFileTempFileManager
import tachiyomi.data.Chapter
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.History
import tachiyomi.data.Manga
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.io.LocalSourceFileSystem
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.lang.ref.WeakReference

// SY -->
private const val LEGACY_DATABASE_NAME = "tachiyomi.db"
// SY <--

class AppModule(val app: Application) : InjektModule {
    // KMK -->
    private val sqlDriverLock = Any()
    private var sqlDriverRef: WeakReference<SqlDriver>? = null
    // KMK <--
    // SY -->
    private val securityPreferences: SecurityPreferences by injectLazy()
    // SY <--

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)

        addSingletonFactory<SqlDriver> {
            // SY -->
            // KMK -->
            val encryptDatabase = securityPreferences.encryptDatabase().get()
            if (encryptDatabase) {
                System.loadLibrary("sqlcipher")
            }

            synchronized(sqlDriverLock) {
                sqlDriverRef?.get()?.let { return@synchronized it }
                val isWal = !encryptDatabase && app.getSystemService<ActivityManager>()?.isLowRamDevice != true
                val driver = AndroidxSqliteDriver(
                    connectionFactory = if (encryptDatabase) {
                        // SQLCipher uses a single connection, so threads wait on it instead of hitting SQLITE_BUSY
                        DefaultAndroidxSqliteConnectionFactory(
                            SQLCipherDriver(
                                CbzCrypto.getDecryptedPasswordSql(),
                                null,
                                null,
                            ),
                        )
                    } else {
                        object : AndroidxSqliteConnectionFactory {
                            override val driver: SQLiteDriver = BundledSQLiteDriver()

                            override fun createConnection(name: String): SQLiteConnection {
                                return driver.open(name).apply {
                                    execSQL("PRAGMA busy_timeout = 3000")
                                }
                            }
                        }
                    },
                    databaseType = AndroidxSqliteDatabaseType.FileProvider(
                        app,
                        if (encryptDatabase) CbzCrypto.DATABASE_NAME else LEGACY_DATABASE_NAME,
                    ),
                    schema = Database.Schema,
                    configuration = if (encryptDatabase) {
                        AndroidxSqliteConfiguration(
                            isForeignKeyConstraintsEnabled = true,
                            // KMK -->
                            // Prevent crash when using Database Inspector
                            cacheSize = if (isDebugBuildType) 0 else 25,
                            // KMK <--
                        )
                    } else {
                        AndroidxSqliteConfiguration(
                            isForeignKeyConstraintsEnabled = true,
                            journalMode = if (isWal) SqliteJournalMode.WAL else SqliteJournalMode.Truncate,
                            concurrencyModel = MultipleReadersSingleWriter(
                                isWal = isWal,
                                nonWalCount = 1,
                                walCount = 4,
                            ),
                            // KMK -->
                            // Prevent crash when using Database Inspector
                            cacheSize = if (isDebugBuildType) 0 else 25,
                            // KMK <--
                        )
                    },
                )
                driver.also { sqlDriverRef = WeakReference(it) }
            }
            // KMK <--
            // SY <--
        }
        addSingletonFactory {
            Database(
                driver = get(),
                historyAdapter = History.Adapter(
                    read_atAdapter = DateColumnAdapter,
                ),
                mangaAdapter = Manga.Adapter(
                    remote_genreAdapter = StringListColumnAdapter,
                    // KMK -->
                    user_scanlator_priorityAdapter = StringListColumnAdapter,
                    // KMK <--
                    user_blacklisted_chaptersAdapter = StringListColumnAdapter,
                    // KMK -->
                    user_scanlator_range_rulesAdapter = StringListColumnAdapter,
                    // KMK <--
                    remote_update_strategyAdapter = UpdateStrategyColumnAdapter,
                    remote_memoAdapter = MemoColumnAdapter,
                ),
                chapterAdapter = Chapter.Adapter(
                    remote_memoAdapter = MemoColumnAdapter,
                ),
            )
        }

        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
        addSingletonFactory {
            XML {
                defaultPolicy {
                    ignoreUnknownChildren()
                }
                autoPolymorphic = true
                xmlDeclMode = XmlDeclMode.Charset
                indent = 2
                xmlVersion = XmlVersion.XML10
            }
        }
        addSingletonFactory<ProtoBuf> {
            ProtoBuf
        }

        addSingletonFactory { UniFileTempFileManager(app) }

        addSingletonFactory { ChapterCache(app, get(), get()) }
        addSingletonFactory { CoverCache(app) }

        // KMK -->
        addSingletonFactory { NetworkHelper(app, get()) }
        // KMK <--
        addSingletonFactory { JavaScriptEngine(app) }

        addSingletonFactory<SourceManager> { AndroidSourceManager(app, get(), get()) }
        addSingletonFactory { ExtensionManager(app) }

        addSingletonFactory { DownloadProvider(app) }
        addSingletonFactory { DownloadManager(app) }
        addSingletonFactory { DownloadCache(app) }

        addSingletonFactory { TrackerManager() }
        addSingletonFactory { DelayedTrackingStore(app) }

        addSingletonFactory { ImageSaver(app) }

        addSingletonFactory { AndroidStorageFolderProvider(app) }
        addSingletonFactory { LocalSourceFileSystem(get()) }
        addSingletonFactory { LocalCoverManager(app, get()) }
        addSingletonFactory { StorageManager(app, get()) }

        // SY -->
        addSingletonFactory { EHentaiUpdateHelper(app) }

        addSingletonFactory { PagePreviewCache(app) }
        // SY <--

        // KMK -->
        addSingletonFactory { BackupRestoreStatus() }
        addSingletonFactory { SyncStatus() }
        addSingletonFactory { LibraryUpdateStatus() }
        // KMK <--

        // AM (CONNECTIONS) -->
        addSingletonFactory { ConnectionsManager() }
        addSingletonFactory { WebhookNotifier() }
        // <-- AM (CONNECTIONS)

        // Asynchronously init expensive components for a faster cold start
        ContextCompat.getMainExecutor(app).execute {
            get<NetworkHelper>()

            get<SourceManager>()

            get<Database>()

            get<DownloadManager>()

            // SY -->
            get<GetCustomMangaInfo>()
            // SY <--
        }

        addSingletonFactory { GoogleDriveService(app) }
    }
}
