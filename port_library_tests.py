from port_database import ROOT, closing, imports
import re

for name in ('libraryUpdateError/LibraryUpdateErrorRepositoryImpl.kt', 'libraryUpdateErrorMessage/LibraryUpdateErrorMessageRepositoryImpl.kt'):
    p = ROOT/'data/src/main/java/tachiyomi/data'/name
    s = p.read_text(encoding='utf-8')
    s = re.sub(r'return (database\.libraryUpdateError\w*Queries\.(?:delete\w*|clean\w*)\([^\n]*\))', r'\1', s)
    if 'libraryUpdateError/' in name: s = s.replace('return database.transactionWithResult', 'database.transaction')
    p.write_text(s, encoding='utf-8', newline='\n')

p = ROOT/'app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryScreenModel.kt'
s = p.read_text(encoding='utf-8')
start = s.index('        val filterFnDownloaded:')
end = s.index('\n\n        val filterFnUnread', start)
s = s[:start] + '''        // KMK -->
        val filterFnDownloaded: (LibraryItem) -> Boolean = {
            applyFilter(filterDownloaded) { it.isLocal || it.downloadCount > 0 }
        }
        // KMK <--''' + s[end:]
start = s.index('                LibraryItem(', s.index('private fun getFavoritesFlow'))
end = s.index('                    // KMK -->\n                    useLangIcon', start)
s = s[:start] + '''                // KMK -->
                val downloadCount = if (manga.manga.source == MERGED_SOURCE_ID) {
                    getMergedMangaById.await(manga.manga.id)
                        .sumOf { downloadManager.getDownloadCount(it) }.toLong()
                } else {
                    downloadManager.getDownloadCount(manga.manga).toLong()
                }
                LibraryItem(
                    libraryManga = manga,
                    downloadCount = downloadCount,
                    unreadCount = manga.unreadCount,
                    isLocal = manga.manga.isLocal(),
                    badges = LibraryItem.Badges(
                        downloadCount = if (preferences.downloadBadge) downloadCount else 0,
                        unreadCount = if (preferences.unreadBadge) manga.unreadCount else 0,
                        isLocal = preferences.localBadge && manga.manga.isLocal(),
                        sourceLanguage = if (preferences.languageBadge) source.lang else "",
                    ),
                    // KMK <--
''' + s[end:]
p.write_text(s, encoding='utf-8', newline='\n')
for name in ('LibraryList.kt','LibraryComfortableGrid.kt','LibraryCompactGrid.kt'):
    p = ROOT/'app/src/main/java/eu/kanade/presentation/library/components'/name
    s = p.read_text(encoding='utf-8')
    for field in ('downloadCount','unreadCount','isLocal','sourceLanguage'):
        s = s.replace('= libraryItem.'+field, '= libraryItem.badges.'+field)
    s = s.replace('                    DownloadsBadge(', '                    // KMK -->\n                    DownloadsBadge(')
    s = s.replace('                    UnreadBadge(count = libraryItem.badges.unreadCount)', '                    UnreadBadge(count = libraryItem.badges.unreadCount)\n                    // KMK <--')
    s = s.replace('                        isLocal = libraryItem.badges.isLocal,', '                        // KMK -->\n                        isLocal = libraryItem.badges.isLocal,')
    s = s.replace('                        sourceLanguage = libraryItem.badges.sourceLanguage,', '                        sourceLanguage = libraryItem.badges.sourceLanguage,\n                        // KMK <--')
    p.write_text(s, encoding='utf-8', newline='\n')

for p in (ROOT/'data/src/test').rglob('*.kt'):
    s = p.read_text(encoding='utf-8')
    if 'AndroidDatabaseHandler' not in s: continue
    s = s.replace('import tachiyomi.data.AndroidDatabaseHandler\n', '').replace('import io.mockk.coVerify\n','').replace('import io.mockk.spyk\n','')
    s = re.sub(r'^.*(?:lateinit var handler:|handler = (?:spyk\()?AndroidDatabaseHandler|coVerify\(exactly = 1\).*handler.await).*\n', '', s, flags=re.M)
    s = s.replace('RepositoryImpl(handler)', 'RepositoryImpl(db)').replace('Database.Schema.create(driver).value', 'Database.Schema.create(driver).await()')
    s = re.sub(r'\.executeAs(List|OneOrNull|One)\(', r'.awaitAs\1(', s)
    s = imports(s, ['app.cash.sqldelight.async.coroutines.awaitAsList','app.cash.sqldelight.async.coroutines.awaitAsOne','kotlinx.coroutines.runBlocking'])
    # Test lifecycle methods use runBlocking; helpers that query the async schema suspend.
    s = re.sub(r'(?m)^(    fun (?:setUp|setup|`[^`]+`)\([^\n]*\)) \{', r'\1 = runBlocking<Unit> {', s)
    if p.name == 'FeedSavedSearchRepositoryTest.kt': s = s.replace('private fun open(', 'private suspend fun open(')
    if p.name == 'MangaChapterStatsTest.kt':
        s = re.sub(r'private fun (assertAggregatesMatch|sourceMangaIdsFor|recompute|applyRandomWrite|seedLibrary|insertManga|insertChapter|moveChapter|deleteChapters|chaptersOf|libraryRow|refill|hasMissing)', r'private suspend fun \1', s)
        s = s.replace('private fun <T> withClue', 'private inline fun <T> withClue')
        s = s.replace('        db.mangasQueries.insert(', '        return db.mangasQueries.insertReturningId(')
        s = s.replace('        )\n        return db.mangasQueries.selectLastInsertedRowId().awaitAsOne()', '        ).awaitAsOne()')
    pos = s.index('\nclass ')
    s = s[:pos] + '\n// KMK -->' + s[pos:] + '// KMK <--\n'
    p.write_text(s, encoding='utf-8', newline='\n')
