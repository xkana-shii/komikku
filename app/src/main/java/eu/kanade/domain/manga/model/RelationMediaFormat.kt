package eu.kanade.domain.manga.model

import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.manga.model.isNovelFormat

// KMK --> The extension ABI has no dedicated format field. Keep unknown entries;
// reject only explicit novel classifications supplied as source tags.
internal fun SManga.isNovelRelation(): Boolean = getGenres().orEmpty().any(::isNovelFormat)
// KMK <--
