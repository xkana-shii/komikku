from pathlib import Path
import re
from port_database import ROOT, imports, closing

p = ROOT/'app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt'
s = p.read_text(encoding='utf-8').replace('launch(dispatcher)', 'launch').replace('restoreProgress.incrementAndGet()', 'restoreProgress.incrementAndFetch()').replace('restoreProgress.get()', 'restoreProgress.load()')
# Categories must finish before manga-category associations and category preferences are restored.
s = s.replace('restoreCategories(backup.backupCategories)', 'restoreCategories(backup.backupCategories).join()')
p.write_text(s, encoding='utf-8', newline='\n')

for name in ('SyncManager.kt', 'service/SyncService.kt'):
    p = ROOT/'app/src/main/java/eu/kanade/tachiyomi/data/sync'/name
    s = p.read_text(encoding='utf-8')
    s = re.sub(r'Triple\((\w+)\.source, \1\.url, \1\.title\)', r'Pair(\1.source, \1.url)', s)
    s = s.replace('return "${manga.source}|${manga.url}|${manga.title.lowercase().trim()}|${manga.author?.lowercase()?.trim()}"', '// KMK -->\n            return "${manga.source}|${manga.url}"\n            // KMK <--')
    s = s.replace('return "${chapter.url}|${chapter.name}|${chapter.chapterNumber}"', '// KMK -->\n            return chapter.url\n            // KMK <--')
    p.write_text(s, encoding='utf-8', newline='\n')

# Use direct database receivers and transactions instead of redundant scope wrappers.
for base in ('app/src/main/java','data/src/main/java'):
    for p in (ROOT/base).rglob('*.kt'):
        s = p.read_text(encoding='utf-8')
        if 'database.transactionWithResult { with(database)' not in s and 'database.run {' not in s: continue
        # Prefix query objects inside former handler scopes; keep extension receiver methods explicit.
        s = re.sub(r'(?<![\w.])(\w+Queries)\.', r'database.\1.', s)
        s = s.replace('private suspend fun Database.updatePartialBlocking', 'private suspend fun updatePartialBlocking')
        s = s.replace('private suspend fun Database.refillChapterStats', 'private suspend fun refillChapterStats')
        s = s.replace('database.refillChapterStats()', 'refillChapterStats()')
        pattern = re.compile(r'database\.run\s*\{')
        for m in reversed(list(pattern.finditer(s))):
            end = closing(s, m.end()-1)
            body = s[m.end():end].strip()
            # Blocks containing declarations/control flow still need Kotlin's inline run scope.
            if re.search(r'\b(val|var|if|for|while|return)\b', body):
                s = s[:m.start()] + 'run {' + s[m.end():]
            else:
                s = s[:m.start()] + body + s[end+1:]
        pattern = re.compile(r'database.transactionWithResult \{ with\(database\) \{')
        for m in reversed(list(pattern.finditer(s))):
            inner = closing(s, m.end()-1)
            outer = closing(s, s.index('{',m.start()))
            method = 'transactionWithResult' if s[max(0,m.start()-30):m.start()].rstrip().endswith(('return', '=')) else 'transaction'
            body = s[m.end():inner].replace('return@with', 'return@'+method)
            s = s[:m.start()] + 'database.'+method+' {' + body + '}' + s[outer+1:]
        p.write_text(s, encoding='utf-8', newline='\n')
