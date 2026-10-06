from pathlib import Path
import sqlite3

fixture = Path('data/src/main/sqldelight/28.db')
with sqlite3.connect(fixture) as db:
    assert db.execute('SELECT count(*) FROM chapters').fetchone()[0] == 0
    schema = '\n'.join(db.iterdump())
    assert 'fillermark INTEGER' not in schema
    schema = schema.replace('bookmark INTEGER NOT NULL,', 'bookmark INTEGER NOT NULL,\n    fillermark INTEGER NOT NULL,')
    assert 'fillermark INTEGER' in schema
db.close()
temporary = fixture.with_suffix('.corrected.db')
temporary.unlink(missing_ok=True)
with sqlite3.connect(temporary) as db:
    db.executescript(schema)
db.close()
temporary.replace(fixture)
