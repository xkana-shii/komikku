package mihon.app.di

import android.app.ActivityManager
import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConcurrencyModel.MultipleReadersSingleWriter
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConnectionFactory
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.FileProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.core.archive.CbzCrypto
import mihon.core.metro.AppCoroutineScope
import mihon.core.metro.IsDebugBuild
import mihon.data.database.DatabaseRepositoryImpl
import mihon.telemetry.TelemetryConfig
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.util.system.logcat
import tachiyomi.source.local.AllowHiddenFiles

@BindingContainer
object AppBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun providesSqlDriver(
        context: Context,
        securityPreferences: SecurityPreferences,
        // KUN -->
        // Provided by :data's DatabaseBindings, so the app does not reference the generated Database.
        schema: SqlSchema<QueryResult.AsyncValue<Unit>>,
        // KUN <--
        // KUN -->
        // Mihon fd4f73c908: exposes whether the database is being migrated after an update.
        // KUN <--
        databaseRepository: DatabaseRepositoryImpl,
    ): SqlDriver {
        // KUN -->
        // Ported from Mihon 38e93086c8 ("Configure database connections like Room") and
        // 61cd786e7f ("Use WAL on every device"): WAL everywhere, one reader on low-RAM devices.
        // KUN <--
        val isLowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true

        // SY -->
        if (securityPreferences.encryptDatabase.get()) {
            System.loadLibrary("sqlcipher")

            return AndroidxSqliteDriver(
                // KMK -->
                // SY cd52aad08 ("Swap to Room3 Encrypted Driver"): use SQLCipher's androidx.sqlite
                // driver instead of the legacy AndroidSqliteDriver + SupportOpenHelperFactory.
                // Wrapped in a connection factory so the connection still gets the fork's
                // busy_timeout, matching the unencrypted path below.
                connectionFactory = object : AndroidxSqliteConnectionFactory {
                    override val driver: SQLiteDriver = SQLCipherDriver(
                        CbzCrypto.getDecryptedPasswordSql(),
                        null,
                        null,
                    )

                    override fun createConnection(name: String): SQLiteConnection {
                        return driver.open(name).apply {
                            execSQL("PRAGMA busy_timeout = 3000")
                        }
                    }
                },
                // KMK <--
                databaseType = AndroidxSqliteDatabaseType.FileProvider(context, CbzCrypto.DATABASE_NAME),
                schema = schema,
                configuration = AndroidxSqliteConfiguration(
                    isForeignKeyConstraintsEnabled = true,
                    // KUN -->
                    // Mihon 61cd786e7f: WAL on every device (the configuration default), one reader
                    // on low-RAM devices, four otherwise. SQLCipher's driver reports its own
                    // connection pool, so this is mostly informational on the encrypted path.
                    // KUN <--
                    concurrencyModel = MultipleReadersSingleWriter(isWal = true, walCount = if (isLowRam) 1 else 4),
                ),
                // KUN -->
                // Mihon fd4f73c908: show the migrating screen while the encrypted database upgrades.
                // KUN <--
                onConfigure = {
                    val version = executePragmaQuery("user_version", { QueryResult.Value(it.apply { next() }.getLong(0)) })
                    databaseRepository.isMigrating.value = version in 1..<schema.version
                },
                onOpen = {
                    databaseRepository.isMigrating.value = false
                },
            )
        }
        // SY <--

        return AndroidxSqliteDriver(
            // KUN -->
            // Mihon 38e93086c8: set busy_timeout on every pooled connection, not once on open.
            // KUN <--
            connectionFactory = object : AndroidxSqliteConnectionFactory {
                override val driver: SQLiteDriver = BundledSQLiteDriver()

                override fun createConnection(name: String): SQLiteConnection {
                    return driver.open(name).apply {
                        execSQL("PRAGMA busy_timeout = 3000")
                    }
                }
            },
            databaseType = AndroidxSqliteDatabaseType.FileProvider(context, "tachiyomi.db"),
            schema = schema,
            configuration = AndroidxSqliteConfiguration(
                isForeignKeyConstraintsEnabled = true,
                // KUN -->
                // Mihon 61cd786e7f: WAL on every device; one reader on low-RAM, four otherwise.
                // KUN <--
                concurrencyModel = MultipleReadersSingleWriter(isWal = true, walCount = if (isLowRam) 1 else 4),
            ),
            // KUN -->
            // Mihon fd4f73c908: runs before the driver compares the schema version, and onOpen once
            // the schema is ready, so the UI can show a migrating screen instead of looking hung.
            // KUN <--
            onConfigure = {
                val version = executePragmaQuery("user_version", { QueryResult.Value(it.apply { next() }.getLong(0)) })
                databaseRepository.isMigrating.value = version in 1..<schema.version
            },
            onOpen = {
                databaseRepository.isMigrating.value = false
            },
        )
    }

    @Provides
    @SingleIn(AppScope::class)
    @AppCoroutineScope
    fun providesAppCoroutineScope(@IsDebugBuild isDebugBuild: Boolean): CoroutineScope {
        // Work here outlives the screen that started it, so a failure would crash the app on whatever screen is open
        // by then. Debug builds still crash so the bug gets noticed.
        val handler = CoroutineExceptionHandler { _, throwable ->
            if (isDebugBuild) {
                val thread = Thread.currentThread()
                thread.uncaughtExceptionHandler?.uncaughtException(thread, throwable)
            } else {
                logcat(LogPriority.ERROR, throwable) { "Uncaught exception in the app scope" }
                TelemetryConfig.recordException(throwable)
            }
        }
        return CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesXML(): XML = XML {
        defaultPolicy {
            ignoreUnknownChildren()
        }
        autoPolymorphic = true
        xmlDeclMode = XmlDeclMode.Charset
        indent = 2
        xmlVersion = XmlVersion.XML10
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesProtoBuf(): ProtoBuf = ProtoBuf

    // KMK -->
    // KUN -->
    @Provides
    @SingleIn(AppScope::class)
    fun providesAllowHiddenFiles(sourcePreferences: SourcePreferences): AllowHiddenFiles =
        AllowHiddenFiles { sourcePreferences.allowLocalSourceHiddenFolders.get() }
    // KUN <--

    @Provides
    @SingleIn(AppScope::class)
    fun providesFilterSerializer(): xyz.nulldev.ts.api.http.serializer.FilterSerializer =
        xyz.nulldev.ts.api.http.serializer.FilterSerializer()
    // KMK <--

    // SY -->
    // KMK -->
    // Metro cannot bind a function type, so DeleteCategory's sync tombstone callback is wired here.
    @Provides
    @SingleIn(AppScope::class)
    fun providesDeleteCategory(
        categoryRepository: tachiyomi.domain.category.repository.CategoryRepository,
        libraryPreferences: tachiyomi.domain.library.service.LibraryPreferences,
        downloadPreferences: tachiyomi.domain.download.service.DownloadPreferences,
        syncPreferences: eu.kanade.domain.sync.SyncPreferences,
    ): tachiyomi.domain.category.interactor.DeleteCategory = tachiyomi.domain.category.interactor.DeleteCategory(
        categoryRepository = categoryRepository,
        libraryPreferences = libraryPreferences,
        downloadPreferences = downloadPreferences,
        onCategoryDeleted = syncPreferences::rememberDeletedCategory,
    )
    // KMK <--
    // SY <--
}
