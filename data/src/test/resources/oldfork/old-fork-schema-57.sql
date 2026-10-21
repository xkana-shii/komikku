-- Fresh schema of the previous personal fork (xkana-shii/komikku main @ 0ff967c77f, user_version 57),
-- extracted from its data/src/main/sqldelight/tachiyomi/**/*.sq files with the SQLDelight type
-- adapters removed. Used by OldForkDatabaseConverterTest to build an old-lineage database.
-- Statements are separated by a line containing only ';;'.

CREATE TABLE category(
    id INTEGER NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    `order` INTEGER NOT NULL UNIQUE,
    flags INTEGER NOT NULL,
    manga_order TEXT NOT NULL,
    -- KMK -->
    hidden INTEGER DEFAULT 0 NOT NULL,
    parent_id INTEGER NOT NULL DEFAULT 0,
    date_added INTEGER NOT NULL DEFAULT 0,
    date_modified INTEGER NOT NULL DEFAULT 0,
    track_status INTEGER,
    update_interval INTEGER
    -- KMK <--
);
;;
CREATE TABLE chapter(
    id INTEGER NOT NULL PRIMARY KEY,
    manga_id INTEGER NOT NULL,
    remote_url TEXT NOT NULL,
    remote_name TEXT NOT NULL,
    remote_scanlator TEXT,
    user_read INTEGER NOT NULL,
    user_bookmark INTEGER NOT NULL,
    -- KMK -->
    user_fillermark INTEGER NOT NULL,
    -- KMK <--
    user_last_page_read INTEGER NOT NULL,
    remote_chapter_number REAL NOT NULL,
    remote_order INTEGER NOT NULL,
    state_date_fetch INTEGER NOT NULL,
    remote_date_upload INTEGER NOT NULL,
    -- KMK -->
    state_last_modified_at INTEGER NOT NULL DEFAULT 0,
    state_version INTEGER NOT NULL DEFAULT 0,
    state_is_syncing INTEGER NOT NULL DEFAULT 0,
    -- KMK <--
    remote_memo BLOB NOT NULL DEFAULT (CAST('{}' AS BLOB)),
    -- KMK -->
    user_bookmark_color INTEGER NOT NULL DEFAULT 0,
    -- KMK <--
    UNIQUE(manga_id, remote_url),
    FOREIGN KEY(manga_id) REFERENCES manga (id)
    ON DELETE CASCADE
);
;;
CREATE TABLE eh_favorites (
    gid TEXT NOT NULL,
    token TEXT NOT NULL,
    title TEXT NOT NULL,
    category INTEGER NOT NULL,
    PRIMARY KEY (gid, token)
);
;;
CREATE TABLE eh_favorites_alternatives (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    gid TEXT NOT NULL,
    token TEXT NOT NULL,
    otherGid TEXT NOT NULL,
    otherToken TEXT NOT NULL,
    UNIQUE (gid, token, otherGid, otherToken)
);
;;
CREATE TABLE excluded_scanlator(
    manga_id INTEGER NOT NULL,
    scanlator TEXT NOT NULL,
    UNIQUE(manga_id, scanlator),
    FOREIGN KEY(manga_id) REFERENCES manga (id)
    ON DELETE CASCADE
);
;;
CREATE TABLE extension_store(
    index_url TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    badge_label TEXT NOT NULL,
    signing_key TEXT NOT NULL,
    contact_website TEXT NOT NULL,
    contact_discord TEXT,
    is_legacy INTEGER NOT NULL,
    extension_list_url TEXT
);
;;
CREATE TABLE feed_saved_search (
    _id INTEGER NOT NULL PRIMARY KEY,
    source INTEGER NOT NULL,
    saved_search INTEGER,
    global INTEGER NOT NULL,
    feed_order INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(saved_search) REFERENCES saved_search (_id)
    ON DELETE CASCADE
);
;;
CREATE TABLE history(
    id INTEGER NOT NULL PRIMARY KEY,
    manga_id INTEGER NOT NULL,
    chapter_id INTEGER NOT NULL UNIQUE,
    read_at INTEGER,
    read_duration INTEGER NOT NULL,
    FOREIGN KEY(manga_id) REFERENCES manga (id)
    ON DELETE CASCADE,
    FOREIGN KEY(chapter_id) REFERENCES chapter (id)
    ON DELETE CASCADE
);
;;
CREATE TABLE libraryUpdateError (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    manga_id INTEGER NOT NULL UNIQUE,
    message_id INTEGER NOT NULL,
    last_update INTEGER NOT NULL DEFAULT 0
);
;;
CREATE TABLE libraryUpdateErrorMessage (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    message TEXT NOT NULL UNIQUE
);
;;
CREATE TABLE library_visibility_generation(
    id INTEGER NOT NULL PRIMARY KEY CHECK(id = 1),
    revision INTEGER NOT NULL
);
;;
CREATE TABLE library_visibility_revision(
    manga_id INTEGER NOT NULL PRIMARY KEY,
    revision INTEGER NOT NULL,
    FOREIGN KEY(manga_id) REFERENCES manga(id) ON DELETE CASCADE
);
;;
CREATE TABLE manga(
    id INTEGER NOT NULL PRIMARY KEY,
    source_id INTEGER NOT NULL,
    remote_url TEXT NOT NULL,
    remote_artist TEXT,
    remote_author TEXT,
    remote_description TEXT,
    remote_genre TEXT,
    remote_title TEXT NOT NULL,
    remote_status INTEGER NOT NULL,
    remote_cover TEXT,
    user_favorite_at INTEGER,
    state_chapter_last_update INTEGER,
    state_chapter_next_update INTEGER,
    state_initialized INTEGER NOT NULL,
    user_reader_flags INTEGER NOT NULL,
    user_chapter_flags INTEGER NOT NULL,
    state_cover_last_modified INTEGER NOT NULL,
    -- KMK -->
    state_date_added INTEGER NOT NULL,
    user_filtered_scanlators TEXT,
    remote_update_strategy INTEGER NOT NULL DEFAULT 0,
    state_chapter_fetch_interval INTEGER DEFAULT 0 NOT NULL,
    -- KMK -->
    state_last_modified_at INTEGER NOT NULL DEFAULT 0,
    state_version INTEGER NOT NULL DEFAULT 0,
    state_is_syncing INTEGER NOT NULL DEFAULT 0,
    -- KMK <--
    user_notes TEXT NOT NULL DEFAULT "",
    remote_memo BLOB NOT NULL DEFAULT (CAST('{}' AS BLOB)),
    -- KMK -->
    user_blacklisted_chapters TEXT NOT NULL DEFAULT '',
    user_scanlator_priority TEXT NOT NULL DEFAULT '',
    user_scanlator_range_rules TEXT NOT NULL DEFAULT '',
    -- KMK <--
    UNIQUE(source_id, remote_url)
);
;;
CREATE TABLE manga_category(
    manga_id INTEGER NOT NULL,
    category_id INTEGER NOT NULL,
    -- KMK -->
    state_last_modified_at INTEGER NOT NULL DEFAULT 0,
    -- KMK <--
    UNIQUE(manga_id, category_id),
    FOREIGN KEY(category_id) REFERENCES category (id)
    ON DELETE CASCADE,
    FOREIGN KEY(manga_id) REFERENCES manga (id)
    ON DELETE CASCADE
);
;;
CREATE TABLE manga_chapter_stats(
    manga_id INTEGER NOT NULL PRIMARY KEY,
    total_count INTEGER NOT NULL,
    read_count INTEGER NOT NULL,
    bookmark_count INTEGER NOT NULL,
    bookmark_read_count INTEGER NOT NULL,
    latest_upload INTEGER NOT NULL,
    chapter_fetched_at INTEGER NOT NULL,
    last_read INTEGER NOT NULL,
    fillermark_count INTEGER NOT NULL DEFAULT 0,
    fillermark_read_count INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(manga_id) REFERENCES manga (id)
    ON DELETE CASCADE
);
;;
CREATE TABLE manga_track(
    id INTEGER NOT NULL PRIMARY KEY,
    manga_id INTEGER NOT NULL,
    tracker_id INTEGER NOT NULL,
    remote_id INTEGER NOT NULL,
    library_id INTEGER,
    title TEXT NOT NULL,
    last_chapter_read REAL NOT NULL,
    total_chapters INTEGER NOT NULL,
    status INTEGER NOT NULL,
    score REAL NOT NULL,
    remote_url TEXT NOT NULL,
    start_date INTEGER NOT NULL,
    finish_date INTEGER NOT NULL,
    private INTEGER DEFAULT 0 NOT NULL,
    UNIQUE (manga_id, tracker_id),
    FOREIGN KEY(manga_id) REFERENCES manga (id)
    ON DELETE CASCADE
);
;;
CREATE TABLE merged(
    _id INTEGER NOT NULL PRIMARY KEY,
    info_manga INTEGER NOT NULL,
    get_chapter_updates INTEGER NOT NULL,
    chapter_sort_mode INTEGER NOT NULL,
    chapter_priority INTEGER NOT NULL,
    download_chapters INTEGER NOT NULL,
    merge_id INTEGER NOT NULL, -- Point to the fake manga which was created as a merge entry
    merge_url TEXT NOT NULL, -- URL of the merge entry, usually is the original first manga used to create a merge entry
    manga_id INTEGER, -- Point to actual children manga entry which is a part of this merge entry
    manga_url TEXT NOT NULL, -- URL of the actual children manga entry which is a part of this merge entry
    manga_source INTEGER NOT NULL,
    FOREIGN KEY(manga_id) REFERENCES manga (id)
    ON DELETE SET NULL,
    FOREIGN KEY(merge_id) REFERENCES manga (id)
    ON DELETE CASCADE
);
;;
CREATE TABLE saved_search(
    _id INTEGER NOT NULL PRIMARY KEY,
    source INTEGER NOT NULL,
    name TEXT NOT NULL,
    query TEXT,
    filters_json TEXT
);
;;
CREATE TABLE search_metadata (
    manga_id INTEGER NOT NULL PRIMARY KEY,
    uploader TEXT,
    extra TEXT NOT NULL,
    indexed_extra TEXT,
    extra_version INTEGER NOT NULL,
    FOREIGN KEY(manga_id) REFERENCES manga (id)
    ON DELETE CASCADE
);
;;
CREATE TABLE search_tags (
    _id INTEGER NOT NULL PRIMARY KEY,
    manga_id INTEGER NOT NULL,
    namespace TEXT,
    name TEXT NOT NULL,
    type INTEGER NOT NULL,
    FOREIGN KEY(manga_id) REFERENCES manga (id)
    ON DELETE CASCADE
);
;;
CREATE TABLE search_titles (
    _id INTEGER NOT NULL PRIMARY KEY,
    manga_id INTEGER NOT NULL,
    title TEXT NOT NULL,
    type INTEGER NOT NULL,
    FOREIGN KEY(manga_id) REFERENCES manga (id)
    ON DELETE CASCADE
);
;;
CREATE TABLE source(
    id INTEGER NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    language TEXT NOT NULL
);
;;
CREATE INDEX chapter_state_date_fetch_index ON chapter(state_date_fetch);
;;
CREATE INDEX chapter_unread_state_date_fetch_index ON chapter(state_date_fetch) WHERE user_read = 0;
;;
CREATE INDEX eh_favorites_alternatives_gid_token_index ON eh_favorites_alternatives(gid, token);
;;
CREATE INDEX eh_favorites_alternatives_other_gid_token_index ON eh_favorites_alternatives(otherGid, otherToken);
;;
CREATE INDEX feed_saved_search_saved_search_index ON feed_saved_search(saved_search);
;;
CREATE INDEX history_manga_id_index ON history(manga_id);
;;
CREATE INDEX history_read_at_index ON history(read_at);
;;
CREATE INDEX idx_chapters_url ON chapter(remote_url);
;;
CREATE INDEX idx_excluded_scanlators_manga_id_scanlator ON excluded_scanlator(manga_id, scanlator);
;;
CREATE INDEX manga_category_category_id_index ON manga_category(category_id);
;;
CREATE INDEX manga_track_tracker_id_remote_id_manga_id_index ON manga_track(tracker_id, remote_id, manga_id);
;;
CREATE INDEX manga_user_favorite_at_index ON manga(user_favorite_at) WHERE user_favorite_at IS NOT NULL;
;;
CREATE INDEX merged_merge_id_index ON merged(merge_id);
;;
CREATE INDEX search_metadata_indexed_extra_index ON search_metadata(indexed_extra);
;;
CREATE INDEX search_metadata_uploader_index ON search_metadata(uploader);
;;
CREATE INDEX search_tags_manga_id_index ON search_tags(manga_id);
;;
CREATE INDEX search_tags_namespace_name_index ON search_tags(namespace, name);
;;
CREATE INDEX search_titles_manga_id_index ON search_titles(manga_id);
;;
CREATE INDEX search_titles_title_index ON search_titles(title);
;;
CREATE VIEW historyView AS
SELECT
    history.id AS id,
    manga.id AS mangaId,
    chapter.id AS chapterId,
    manga.remote_title AS title,
    manga.remote_cover AS thumbnailUrl,
    manga.source_id AS source,
    (manga.user_favorite_at IS NOT NULL) AS favorite,
    manga.state_cover_last_modified AS cover_last_modified,
    chapter.remote_chapter_number AS chapterNumber,
    -- KMK -->
    manga.user_chapter_flags AS chapterFlags,
    chapter.user_read AS read,
    chapter.user_last_page_read AS lastPageRead,
    coalesce(C.total, 0) AS totalCount,
    coalesce(C.readCount, 0) AS readCount,
    coalesce(C.bookmarkCount, 0) AS bookmarkCount,
    coalesce(C.bookmarkReadCount, 0) AS bookmarkReadCount,
    -- KMK <--
    history.read_at AS readAt,
    history.read_duration AS readDuration,
    max_last_read.read_at AS maxReadAt,
    max_last_read.chapter_id AS maxReadAtChapterId
FROM manga
JOIN chapter
ON manga.id = chapter.manga_id
JOIN history
ON chapter.id = history.chapter_id
JOIN (
    SELECT chapter.manga_id,chapter.id AS chapter_id, MAX(history.read_at) AS read_at
    FROM chapter JOIN history
    ON chapter.id = history.chapter_id
    GROUP BY chapter.manga_id
) AS max_last_read
ON chapter.manga_id = max_last_read.manga_id
-- KMK -->
LEFT JOIN(
    SELECT
        chapter.manga_id,
        count(*) AS total,
        sum(user_read) AS readCount,
        sum(user_bookmark) AS bookmarkCount,
        sum(CASE WHEN user_bookmark = 1 AND user_read = 1 THEN 1 ELSE 0 END) AS bookmarkReadCount,
        excluded_scanlator.scanlator AS ex_scanlator
    FROM chapter
    LEFT JOIN excluded_scanlator
    ON chapter.manga_id = excluded_scanlator.manga_id
    AND chapter.remote_scanlator = excluded_scanlator.scanlator
    WHERE ex_scanlator IS NULL
    GROUP BY chapter.manga_id
) AS C
ON manga.id = C.manga_id;
;;
CREATE VIEW libraryUpdateErrorView AS
SELECT
    manga.id AS mangaId,
    manga.remote_title AS mangaTitle,
    manga.source_id AS source,
    (manga.user_favorite_at IS NOT NULL) AS favorite,
    manga.remote_cover AS thumbnailUrl,
    manga.state_cover_last_modified AS coverLastModified,
    libraryUpdateError._id AS errorId,
    libraryUpdateError.message_id AS messageId,
    libraryUpdateError.last_update AS lastUpdate
FROM manga JOIN libraryUpdateError
ON manga.id = libraryUpdateError.manga_id
WHERE manga.user_favorite_at IS NOT NULL
ORDER BY lastUpdate DESC;
;;
CREATE VIEW libraryView AS
SELECT
    M.*,
    coalesce(C.total, 0) AS totalCount,
    coalesce(C.readCount, 0) AS readCount,
    coalesce(C.latestUpload, 0) AS latestUpload,
    coalesce(C.fetchedAt, 0) AS chapterFetchedAt,
    coalesce(C.lastRead, 0) AS lastRead,
    coalesce(C.bookmarkCount, 0) AS bookmarkCount,
    -- KMK -->
    coalesce(C.bookmarkReadCount, 0) AS bookmarkedReadCount,
    -- KMK <--
    coalesce(MC.categories, '0') AS categories
FROM manga M
LEFT JOIN (
    SELECT
        chapter.manga_id,
        count(*) AS total,
        sum(user_read) AS readCount,
        coalesce(max(chapter.remote_date_upload), 0) AS latestUpload,
        coalesce(max(history.read_at), 0) AS lastRead,
        coalesce(max(chapter.state_date_fetch), 0) AS fetchedAt,
        sum(chapter.user_bookmark) AS bookmarkCount,
        -- KMK -->
        sum(CASE WHEN chapter.user_bookmark = 1 AND chapter.user_read = 1 THEN 1 ELSE 0 END) AS bookmarkReadCount,
        -- KMK <--
        excluded_scanlator.scanlator AS ex_scanlator
    FROM chapter
    LEFT JOIN excluded_scanlator
    ON chapter.manga_id = excluded_scanlator.manga_id
    AND chapter.remote_scanlator = excluded_scanlator.scanlator
    LEFT JOIN history
    ON chapter.id = history.chapter_id
    WHERE ex_scanlator IS NULL
    GROUP BY chapter.manga_id
) AS C
ON M.id = C.manga_id
LEFT JOIN (
    SELECT manga_id, group_concat(category_id) AS categories
    FROM manga_category
    GROUP BY manga_id
) AS MC
ON MC.manga_id = M.id
WHERE M.source_id <> 6969
UNION
SELECT
    M.*,
    coalesce(C.total, 0) AS totalCount,
    coalesce(C.readCount, 0) AS readCount,
    coalesce(C.latestUpload, 0) AS latestUpload,
    coalesce(C.fetchedAt, 0) AS chapterFetchedAt,
    coalesce(C.lastRead, 0) AS lastRead,
    coalesce(C.bookmarkCount, 0) AS bookmarkCount,
    -- KMK -->
    coalesce(C.bookmarkReadCount, 0) AS bookmarkedReadCount,
    -- KMK <--
    coalesce(MC.categories, '0') AS categories
FROM manga M
LEFT JOIN (
    SELECT merged.manga_id,merged.merge_id
    FROM merged
    GROUP BY merged.merge_id
) AS ME
ON ME.merge_id = M.id
LEFT JOIN(
    SELECT
        ME.merge_id,
        count(*) AS total,
        sum(user_read) AS readCount,
        coalesce(max(chapter.remote_date_upload), 0) AS latestUpload,
        coalesce(max(history.read_at), 0) AS lastRead,
        coalesce(max(chapter.state_date_fetch), 0) AS fetchedAt,
        sum(chapter.user_bookmark) AS bookmarkCount,
        -- KMK -->
        sum(CASE WHEN chapter.user_bookmark = 1 AND chapter.user_read = 1 THEN 1 ELSE 0 END) AS bookmarkReadCount,
        -- KMK <--
        excluded_scanlator.scanlator AS ex_scanlator
    FROM chapter
    LEFT JOIN excluded_scanlator
    ON chapter.manga_id = excluded_scanlator.manga_id
    AND chapter.remote_scanlator = excluded_scanlator.scanlator
    LEFT JOIN history
    ON chapter.id = history.chapter_id
    LEFT JOIN merged ME
    ON ME.manga_id = chapter.manga_id
    WHERE ex_scanlator IS NULL
    GROUP BY ME.merge_id
) AS C
ON M.id = C.merge_id  -- ON ME.merge_id = C.merge_id
LEFT JOIN (
    SELECT manga_id, group_concat(category_id) AS categories
    FROM manga_category
    GROUP BY manga_id
) AS MC
ON MC.manga_id = M.id
WHERE M.source_id = 6969;
;;
CREATE VIEW updatesView AS
SELECT
    manga.id AS mangaId,
    manga.remote_title AS mangaTitle,
    chapter.id AS chapterId,
    chapter.remote_name AS chapterName,
    chapter.remote_scanlator AS scanlator,
    chapter.remote_url AS chapterUrl,
    chapter.user_read AS read,
    chapter.user_bookmark AS bookmark,
    chapter.user_bookmark_color AS bookmarkColor,
    chapter.user_last_page_read AS lastPageRead,
    manga.source_id AS sourceId,
    (manga.user_favorite_at IS NOT NULL) AS favorite,
    manga.remote_cover AS thumbnailUrl,
    manga.state_cover_last_modified AS coverLastModified,
    chapter.remote_date_upload AS dateUpload,
    chapter.state_date_fetch AS dateFetch,
    excluded_scanlator.scanlator AS excludedScanlator
FROM manga JOIN chapter
ON manga.id = chapter.manga_id
LEFT JOIN excluded_scanlator
ON manga.id = excluded_scanlator.manga_id
AND chapter.remote_scanlator = excluded_scanlator.scanlator
WHERE manga.user_favorite_at IS NOT NULL AND manga.source_id <> 6969
AND chapter.state_date_fetch > manga.state_date_added
UNION
SELECT
    manga.id AS mangaId,
    manga.remote_title AS mangaTitle,
    chapter.id AS chapterId,
    chapter.remote_name AS chapterName,
    chapter.remote_scanlator AS scanlator,
    chapter.remote_url AS chapterUrl,
    chapter.user_read AS read,
    chapter.user_bookmark AS bookmark,
    chapter.user_bookmark_color AS bookmarkColor,
    chapter.user_last_page_read AS lastPageRead,
    manga.source_id AS sourceId,
    (manga.user_favorite_at IS NOT NULL) AS favorite,
    manga.remote_cover AS thumbnailUrl,
    manga.state_cover_last_modified AS coverLastModified,
    chapter.remote_date_upload AS dateUpload,
    chapter.state_date_fetch AS dateFetch,
    excluded_scanlator.scanlator AS excludedScanlator
FROM manga
LEFT JOIN (
    SELECT merged.manga_id,merged.merge_id
    FROM merged
    GROUP BY merged.merge_id
) AS ME
ON ME.merge_id = manga.id
JOIN chapter
ON ME.manga_id = chapter.manga_id
LEFT JOIN excluded_scanlator
ON manga.id = excluded_scanlator.manga_id
AND chapter.remote_scanlator = excluded_scanlator.scanlator
WHERE manga.user_favorite_at IS NOT NULL AND manga.source_id = 6969
AND chapter.state_date_fetch > manga.state_date_added
ORDER BY dateFetch DESC;
;;
CREATE TRIGGER history_update_manga_id_after_chapter_move
AFTER UPDATE OF manga_id ON chapter
WHEN old.manga_id IS NOT new.manga_id
BEGIN
    UPDATE history SET manga_id = new.manga_id WHERE chapter_id = new.id;
END;
;;
CREATE TRIGGER insert_manga_category_update_version AFTER INSERT ON manga_category
BEGIN
    UPDATE manga
    SET state_version = state_version + 1
    WHERE id = new.manga_id AND (SELECT state_is_syncing FROM manga WHERE id = new.manga_id) = 0;
END;
;;
CREATE TRIGGER manga_chapter_stats_delete_chapter
AFTER DELETE ON chapter
BEGIN
    DELETE FROM manga_chapter_stats WHERE manga_id = old.manga_id;
END;
;;
CREATE TRIGGER manga_chapter_stats_delete_excluded_scanlator
AFTER DELETE ON excluded_scanlator
BEGIN
    DELETE FROM manga_chapter_stats WHERE manga_id = old.manga_id;
END;
;;
CREATE TRIGGER manga_chapter_stats_delete_history
AFTER DELETE ON history
BEGIN
    DELETE FROM manga_chapter_stats
    WHERE manga_id = old.manga_id;
END;
;;
CREATE TRIGGER manga_chapter_stats_insert_chapter
AFTER INSERT ON chapter
BEGIN
    DELETE FROM manga_chapter_stats WHERE manga_id = new.manga_id;
END;
;;
CREATE TRIGGER manga_chapter_stats_insert_excluded_scanlator
AFTER INSERT ON excluded_scanlator
BEGIN
    DELETE FROM manga_chapter_stats WHERE manga_id = new.manga_id;
END;
;;
CREATE TRIGGER manga_chapter_stats_insert_history
AFTER INSERT ON history
BEGIN
    DELETE FROM manga_chapter_stats
    WHERE manga_id = (SELECT manga_id FROM chapter WHERE id = new.chapter_id);
END;
;;
CREATE TRIGGER manga_chapter_stats_update_chapter
AFTER UPDATE OF manga_id, remote_scanlator, user_read, user_bookmark, remote_date_upload, state_date_fetch ON chapter
WHEN old.manga_id IS NOT new.manga_id
OR old.remote_scanlator IS NOT new.remote_scanlator
OR old.user_read IS NOT new.user_read
OR old.user_bookmark IS NOT new.user_bookmark
OR old.remote_date_upload IS NOT new.remote_date_upload
OR old.state_date_fetch IS NOT new.state_date_fetch
BEGIN
    DELETE FROM manga_chapter_stats WHERE manga_id IN (old.manga_id, new.manga_id);
END;
;;
CREATE TRIGGER manga_chapter_stats_update_history
AFTER UPDATE OF read_at ON history
WHEN old.read_at IS NOT new.read_at
BEGIN
    DELETE FROM manga_chapter_stats
    WHERE manga_id = (SELECT manga_id FROM chapter WHERE id = new.chapter_id);
END;
;;
CREATE TRIGGER system_category_delete_trigger BEFORE DELETE
ON category
BEGIN SELECT CASE
    WHEN old.id <= 0 THEN
        RAISE(ABORT, "System category can't be deleted")
    END;
END;
;;
CREATE TRIGGER update_chapter_and_manga_version AFTER UPDATE ON chapter
WHEN new.state_is_syncing = 0 AND (
    new.user_read != old.user_read OR
    new.user_bookmark != old.user_bookmark OR
    new.user_bookmark_color != old.user_bookmark_color OR
    new.user_fillermark != old.user_fillermark OR
    new.user_last_page_read != old.user_last_page_read
)
BEGIN
    -- Update the chapter state_version
    UPDATE chapter SET state_version = state_version + 1
    WHERE id = new.id;

    -- Update the manga state_version
    UPDATE manga SET state_version = state_version + 1
    WHERE id = new.manga_id AND (SELECT state_is_syncing FROM manga WHERE id = new.manga_id) = 0;
END;
;;
CREATE TRIGGER update_last_modified_at_chapters
AFTER UPDATE ON chapter
FOR EACH ROW
BEGIN
  UPDATE chapter
  SET state_last_modified_at = strftime('%s', 'now')
  WHERE id = new.id;
END;
;;
CREATE TRIGGER update_last_modified_at_mangas
AFTER UPDATE ON manga
FOR EACH ROW
BEGIN
  UPDATE manga
  SET state_last_modified_at = strftime('%s', 'now')
  WHERE id = new.id;
END;
;;
CREATE TRIGGER update_last_modified_at_mangas_categories
AFTER UPDATE ON manga_category
FOR EACH ROW
BEGIN
  UPDATE manga_category
  SET state_last_modified_at = strftime('%s', 'now')
  WHERE manga_id = new.manga_id AND category_id = new.category_id;
END;
;;
CREATE TRIGGER update_manga_version AFTER UPDATE ON manga
BEGIN
    UPDATE manga SET state_version = state_version + 1
    WHERE id = new.id AND new.state_is_syncing = 0 AND (
        new.remote_url != old.remote_url OR
        new.remote_description != old.remote_description OR
        new.user_favorite_at IS NOT old.user_favorite_at
    );
END;
;;
CREATE TRIGGER visibility_chapter_delete
AFTER DELETE ON chapter
BEGIN
    UPDATE library_visibility_generation SET revision = revision + 1 WHERE id = 1;
    INSERT INTO library_visibility_revision(manga_id, revision)
    SELECT id, (SELECT revision FROM library_visibility_generation WHERE id = 1) FROM manga WHERE id IN (old.manga_id)
    OR id IN (SELECT merge_id FROM merged WHERE manga_id IN (old.manga_id))
    ON CONFLICT(manga_id) DO UPDATE SET revision = excluded.revision;
END;
;;
CREATE TRIGGER visibility_chapter_insert
AFTER INSERT ON chapter
BEGIN
    UPDATE library_visibility_generation SET revision = revision + 1 WHERE id = 1;
    INSERT INTO library_visibility_revision(manga_id, revision)
    SELECT id, (SELECT revision FROM library_visibility_generation WHERE id = 1) FROM manga WHERE id IN (new.manga_id)
    OR id IN (SELECT merge_id FROM merged WHERE manga_id IN (new.manga_id))
    ON CONFLICT(manga_id) DO UPDATE SET revision = excluded.revision;
END;
;;
CREATE TRIGGER visibility_chapter_update
AFTER UPDATE OF manga_id, remote_url, remote_name, remote_scanlator, remote_chapter_number, remote_order, user_read, user_bookmark, user_bookmark_color, state_date_fetch, remote_date_upload ON chapter
WHEN old.manga_id IS NOT new.manga_id OR old.remote_url IS NOT new.remote_url OR old.remote_name IS NOT new.remote_name OR old.remote_scanlator IS NOT new.remote_scanlator OR old.remote_chapter_number IS NOT new.remote_chapter_number OR old.remote_order IS NOT new.remote_order OR old.user_read IS NOT new.user_read OR old.user_bookmark IS NOT new.user_bookmark OR old.user_bookmark_color IS NOT new.user_bookmark_color OR old.state_date_fetch IS NOT new.state_date_fetch OR old.remote_date_upload IS NOT new.remote_date_upload
BEGIN
    UPDATE library_visibility_generation SET revision = revision + 1 WHERE id = 1;
    INSERT INTO library_visibility_revision(manga_id, revision)
    SELECT id, (SELECT revision FROM library_visibility_generation WHERE id = 1) FROM manga WHERE id IN (old.manga_id, new.manga_id)
    OR id IN (SELECT merge_id FROM merged WHERE manga_id IN (old.manga_id, new.manga_id))
    ON CONFLICT(manga_id) DO UPDATE SET revision = excluded.revision;
END;
;;
CREATE TRIGGER visibility_excluded_delete
AFTER DELETE ON excluded_scanlator
BEGIN
    UPDATE library_visibility_generation SET revision = revision + 1 WHERE id = 1;
    INSERT INTO library_visibility_revision(manga_id, revision)
    SELECT id, (SELECT revision FROM library_visibility_generation WHERE id = 1) FROM manga WHERE id IN (old.manga_id)
    OR id IN (SELECT merge_id FROM merged WHERE manga_id IN (old.manga_id))
    ON CONFLICT(manga_id) DO UPDATE SET revision = excluded.revision;
END;
;;
CREATE TRIGGER visibility_excluded_insert
AFTER INSERT ON excluded_scanlator
BEGIN
    UPDATE library_visibility_generation SET revision = revision + 1 WHERE id = 1;
    INSERT INTO library_visibility_revision(manga_id, revision)
    SELECT id, (SELECT revision FROM library_visibility_generation WHERE id = 1) FROM manga WHERE id IN (new.manga_id)
    OR id IN (SELECT merge_id FROM merged WHERE manga_id IN (new.manga_id))
    ON CONFLICT(manga_id) DO UPDATE SET revision = excluded.revision;
END;
;;
CREATE TRIGGER visibility_excluded_update
AFTER UPDATE ON excluded_scanlator
BEGIN
    UPDATE library_visibility_generation SET revision = revision + 1 WHERE id = 1;
    INSERT INTO library_visibility_revision(manga_id, revision)
    SELECT id, (SELECT revision FROM library_visibility_generation WHERE id = 1) FROM manga WHERE id IN (old.manga_id, new.manga_id)
    OR id IN (SELECT merge_id FROM merged WHERE manga_id IN (old.manga_id, new.manga_id))
    ON CONFLICT(manga_id) DO UPDATE SET revision = excluded.revision;
END;
;;
CREATE TRIGGER visibility_manga_insert
AFTER INSERT ON manga
BEGIN
    UPDATE library_visibility_generation SET revision = revision + 1 WHERE id = 1;
    INSERT INTO library_visibility_revision(manga_id, revision)
    SELECT id, (SELECT revision FROM library_visibility_generation WHERE id = 1) FROM manga WHERE id IN (new.id)
    OR id IN (SELECT merge_id FROM merged WHERE manga_id IN (new.id))
    ON CONFLICT(manga_id) DO UPDATE SET revision = excluded.revision;
END;
;;
CREATE TRIGGER visibility_manga_update
AFTER UPDATE OF source_id, remote_url, user_chapter_flags, user_filtered_scanlators, user_scanlator_priority, user_blacklisted_chapters, user_scanlator_range_rules ON manga
WHEN old.source_id IS NOT new.source_id OR old.remote_url IS NOT new.remote_url OR old.user_chapter_flags IS NOT new.user_chapter_flags OR old.user_filtered_scanlators IS NOT new.user_filtered_scanlators OR old.user_scanlator_priority IS NOT new.user_scanlator_priority OR old.user_blacklisted_chapters IS NOT new.user_blacklisted_chapters OR old.user_scanlator_range_rules IS NOT new.user_scanlator_range_rules
BEGIN
    UPDATE library_visibility_generation SET revision = revision + 1 WHERE id = 1;
    INSERT INTO library_visibility_revision(manga_id, revision)
    SELECT id, (SELECT revision FROM library_visibility_generation WHERE id = 1) FROM manga WHERE id IN (new.id)
    OR id IN (SELECT merge_id FROM merged WHERE manga_id IN (new.id))
    ON CONFLICT(manga_id) DO UPDATE SET revision = excluded.revision;
END;
;;
CREATE TRIGGER visibility_merged_delete
AFTER DELETE ON merged
BEGIN
    UPDATE library_visibility_generation SET revision = revision + 1 WHERE id = 1;
    INSERT INTO library_visibility_revision(manga_id, revision)
    SELECT id, (SELECT revision FROM library_visibility_generation WHERE id = 1) FROM manga WHERE id IN (old.merge_id)
    ON CONFLICT(manga_id) DO UPDATE SET revision = excluded.revision;
END;
;;
CREATE TRIGGER visibility_merged_insert
AFTER INSERT ON merged
BEGIN
    UPDATE library_visibility_generation SET revision = revision + 1 WHERE id = 1;
    INSERT INTO library_visibility_revision(manga_id, revision)
    SELECT id, (SELECT revision FROM library_visibility_generation WHERE id = 1) FROM manga WHERE id IN (new.merge_id)
    ON CONFLICT(manga_id) DO UPDATE SET revision = excluded.revision;
END;
;;
CREATE TRIGGER visibility_merged_member_title
AFTER UPDATE OF remote_title ON manga
WHEN old.remote_title IS NOT new.remote_title
BEGIN
    UPDATE library_visibility_generation SET revision = revision + 1 WHERE id = 1;
    INSERT INTO library_visibility_revision(manga_id, revision)
    SELECT id, (SELECT revision FROM library_visibility_generation WHERE id = 1) FROM manga WHERE id IN (SELECT merge_id FROM merged WHERE manga_id = new.id)
    ON CONFLICT(manga_id) DO UPDATE SET revision = excluded.revision;
END;
;;
CREATE TRIGGER visibility_merged_update
AFTER UPDATE ON merged
BEGIN
    UPDATE library_visibility_generation SET revision = revision + 1 WHERE id = 1;
    INSERT INTO library_visibility_revision(manga_id, revision)
    SELECT id, (SELECT revision FROM library_visibility_generation WHERE id = 1) FROM manga WHERE id IN (old.merge_id, new.merge_id)
    ON CONFLICT(manga_id) DO UPDATE SET revision = excluded.revision;
END;
;;
INSERT OR IGNORE INTO category(id, name, `order`, flags, manga_order, hidden) VALUES (0, '', -1, 0, '', 0);
;;
INSERT INTO library_visibility_generation(id, revision) VALUES(1, 0);
;;
PRAGMA user_version = 57;
