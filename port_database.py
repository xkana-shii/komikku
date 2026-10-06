from pathlib import Path
import re

ROOT = Path(__file__).parent

def closing(text, start, opening='{', ending='}'):
    depth = 0
    token = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/|[{}()]')
    for m in token.finditer(text, start):
        if m.group() == opening: depth += 1
        elif m.group() == ending:
            depth -= 1
            if depth == 0: return m.start()
    raise ValueError(text[start:start+100])

def imports(text, names):
    existing = set(re.findall(r'^import (.+)$', text, re.M))
    names = sorted(set(names) - existing)
    if names:
        pos = text.index('\n', text.index('package '))
        text = text[:pos] + '\n\n' + '\n'.join('import '+n for n in names) + text[pos:]
    return text

def migrate(text):
    text = text.replace('import tachiyomi.data.DatabaseHandler\n', '')
    text = text.replace('DatabaseHandler', 'Database')
    text = re.sub(r'\b(databaseHandler|handler)\b', 'database', text)
    text = text.replace('return@await', 'return@with')
    text = re.sub(r'\.executeAs(List|OneOrNull|One)\(', r'.awaitAs\1(', text)
    # The chapter-stat cache must be prepared on every invalidation, before querying.
    text = text.replace('''database.subscribeToList(
            prepare = { refillChapterStats() },
        ) { libraryViewQueries.library(MangaMapper::mapLibraryManga) }''', '''database.libraryViewQueries.library(MangaMapper::mapLibraryManga)
            .asFlow()
            .filter { !database.refillChapterStats() }
            .mapToList(EmptyCoroutineContext)''')
    if 'refillChapterStats' in text:
        text = text.replace('private fun Database.refillChapterStats()', 'private suspend fun Database.refillChapterStats()')
        text = text.replace('manga_chapter_statsQueries.refill().value', 'manga_chapter_statsQueries.refill()')
        text = imports(text, ['app.cash.sqldelight.coroutines.asFlow', 'app.cash.sqldelight.coroutines.mapToList', 'kotlinx.coroutines.flow.filter', 'kotlin.coroutines.EmptyCoroutineContext'])

    pattern = re.compile(r'database\.(awaitListExecutable|awaitOneOrNullExecutable|awaitOneExecutable|awaitOneOrNull|awaitList|awaitOne|await|subscribeToList|subscribeToOneOrNull|subscribeToOne)(\([^{}]*?\))?\s*\{')
    # Innermost first, so nested query scopes retain their receivers.
    while matches := list(pattern.finditer(text)):
        m = matches[-1]
        end = closing(text, m.end()-1)
        body = text[m.end():end]
        method, args = m.group(1), m.group(2) or ''
        suffix = {'awaitListExecutable':'awaitAsList', 'awaitList':'awaitAsList', 'awaitOneExecutable':'awaitAsOne', 'awaitOne':'awaitAsOne', 'awaitOneOrNullExecutable':'awaitAsOneOrNull', 'awaitOneOrNull':'awaitAsOneOrNull'}.get(method, method)
        if method == 'await':
            suffix = None
        if 'true' in args:
            converted = 'database.transactionWithResult { with(database) {' + body + '} }'
        else:
            converted = 'database.run {' + body + '}'
        if suffix:
            converted += '.' + suffix + '()'
        text = text[:m.start()] + converted + text[end+1:]
    text = text.replace('private fun Database.updatePartialBlocking', 'private suspend fun Database.updatePartialBlocking')
    # Collapse single-query receiver scopes to the direct-query APIs used by SY.
    pattern = re.compile(r'database\.run\s*\{')
    for m in reversed(list(pattern.finditer(text))):
        end = closing(text, m.end()-1)
        body = text[m.end():end].strip()
        if re.match(r'^\w+Queries\.', body) and not re.search(r'\n\s*(?:val |var |return |\w+Queries\.)', body) and '//' not in body:
            text = text[:m.start()] + 'database.' + body + text[end+1:]
    names = ['tachiyomi.data.Database']
    for method in ('awaitAsList', 'awaitAsOne', 'awaitAsOneOrNull'):
        if '.'+method+'(' in text: names.append('app.cash.sqldelight.async.coroutines.'+method)
    for method in ('subscribeToList', 'subscribeToOne', 'subscribeToOneOrNull'):
        if '.'+method+'(' in text: names.append('tachiyomi.data.'+method)
    text = imports(text, names)
    # Mark the migrated declaration body, retaining all original nested fork markers.
    lines = text.splitlines()
    pos = max(i for i,l in enumerate(lines) if l.startswith('import ')) + 1
    lines.insert(pos, '\n// KMK -->')
    lines.append('// KMK <--')
    return '\n'.join(lines) + '\n'

if __name__ == '__main__':
    import sys
    if len(sys.argv) > 1:
        # RETURNING queries are executed explicitly; write-only inserts retain their original API.
        for table in ('mangas','chapters','categories','merged','saved_search','feed_saved_search'):
            p = ROOT/'data/src/main/sqldelight/tachiyomi/data'/f'{table}.sq'
            text = p.read_text(encoding='utf-8')
            m = re.search(r'^insert:\n([\s\S]*?);', text, re.M)
            assert m, table
            returning = '\n\n-- KMK -->\ninsertReturningId:\n' + m.group(1) + '\nRETURNING _id;\n-- KMK <--'
            text = text[:m.end()] + returning + text[m.end():]
            text = re.sub(r'\nselectLastInsertedRowId:\nSELECT last_insert_rowid\(\);\n', '\n', text)
            text = re.sub(r'\nselectLastInsertRow:\nSELECT \*\nFROM mangas\nWHERE _id = last_insert_rowid\(\);\n', '\n', text)
            p.write_text(text, encoding='utf-8', newline='\n')
        for base in ('app/src/main/java','data/src/main/java'):
            for p in (ROOT/base).rglob('*.kt'):
                text = p.read_text(encoding='utf-8')
                if 'selectLastInsertedRowId' not in text: continue
                for m in reversed(list(re.finditer(r'(\w+Queries)\.insert\(', text))):
                    end = closing(text, m.end()-1, '(', ')')
                    rest = text[end+1:]
                    last = re.match(r'\s*'+m.group(1)+r'\.selectLastInsertedRowId\(\)', rest)
                    if last:
                        text = text[:m.start()] + text[m.start():end+1].replace('.insert(', '.insertReturningId(', 1) + rest[last.end():]
                # Chapter inserts previously read connection-global last_insert_rowid separately.
                text = re.sub(r'chaptersQueries\.insert\(([\s\S]*?)\)\s*val lastInsertId = chaptersQueries.selectLastInsertedRowId\(\).awaitAsOne\(\)', r'val lastInsertId = chaptersQueries.insertReturningId(\1).awaitAsOne()', text)
                p.write_text(text, encoding='utf-8', newline='\n')
        # Await query results within their transaction, never after it has returned a lazy query.
        for base in ('app/src/main/java','data/src/main/java'):
            for p in (ROOT/base).rglob('*.kt'):
                text = p.read_text(encoding='utf-8')
                if 'database.transactionWithResult' not in text: continue
                for m in reversed(list(re.finditer(r'database.transactionWithResult \{ with\(database\) \{', text))):
                    inner = closing(text, m.end()-1)
                    outer = closing(text, text.index('{',m.start()))
                    suffix = re.match(r'\.(awaitAs\w+\(\))', text[outer+1:])
                    if suffix:
                        body = text[m.end():inner]
                        text = text[:inner] + text[inner:outer+1] + text[outer+1+suffix.end():]
                        text = text[:m.end()] + body.rstrip() + '.'+suffix.group(1)+'\n' + text[inner:]
                p.write_text(text, encoding='utf-8', newline='\n')
        sys.exit()
    excluded = {'DatabaseHandler.kt','AndroidDatabaseHandler.kt','TransactionContext.kt','QueryPagingSource.kt','AppModule.kt'}
    for base in ('app/src/main/java','data/src/main/java'):
        for p in (ROOT/base).rglob('*.kt'):
            text = p.read_text(encoding='utf-8')
            if 'DatabaseHandler' in text and p.name not in excluded:
                p.write_text(migrate(text), encoding='utf-8', newline='\n')
