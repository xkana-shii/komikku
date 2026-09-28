package exh.md.dto

import tachiyomi.domain.manga.model.isNovelFormat

// KMK --> Resource type is usually manga. Only explicit format tags classify the medium;
// a comic adaptation of a novel is still a comic.
internal fun MangaDataDto.isNovelRelation(): Boolean = isNovelFormat(type) || attributes.tags.any { tag ->
    tag.attributes.group == "format" && tag.attributes.name.values.any(::isNovelFormat)
}
// KMK <--
