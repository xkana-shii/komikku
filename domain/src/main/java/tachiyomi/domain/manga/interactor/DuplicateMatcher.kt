package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.model.DuplicateKey

// KNS -->
/**
 * Decides which library entries are likely the same series as an incoming one, using more than the title text:
 *
 * - titles are compared normalized (case, accents and punctuation ignored), under every name an entry has;
 * - an identical normalized title always matches;
 * - one title containing the other only matches when the authors do not disagree, so that a short title like
 *   "Love" no longer matches every other title that contains it when it is by someone else.
 *
 * Matches through a shared tracker entry are decided by the caller.
 */
object DuplicateMatcher {

    /** Shorter titles than this are only matched when identical. */
    private const val MIN_CONTAINED_LENGTH = 3

    private val diacritics = Regex("""\p{Mn}+""")
    private val nonAlphanumeric = Regex("""[^\p{L}\p{N}]+""")

    fun normalize(text: String): String {
        return java.text.Normalizer.normalize(text.trim(), java.text.Normalizer.Form.NFKD)
            .replace(diacritics, "")
            .lowercase()
            .replace(nonAlphanumeric, "")
    }

    /** Splits "A, B & C" style author fields into normalized names. */
    fun normalizedAuthors(authors: Collection<String>): Set<String> {
        return authors
            .flatMap { it.split(',', '&', ';', '/') }
            .map(::normalize)
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun matchLevel(
        incomingTitles: Collection<String>,
        incomingAuthors: Collection<String>,
        candidate: DuplicateKey,
    ): MatchLevel {
        val incoming = incomingTitles.map(::normalize).filter { it.isNotEmpty() }.toSet()
        val known = candidate.titles.map(::normalize).filter { it.isNotEmpty() }.toSet()
        if (incoming.isEmpty() || known.isEmpty()) return MatchLevel.NONE

        val incomingNames = normalizedAuthors(incomingAuthors)
        val knownNames = normalizedAuthors(candidate.authors)
        val authorsAgree = incomingNames.isNotEmpty() && knownNames.any { it in incomingNames }
        val authorsDisagree = incomingNames.isNotEmpty() && knownNames.isNotEmpty() && !authorsAgree

        if (incoming.any { it in known }) {
            return if (authorsAgree) MatchLevel.EXACT_TITLE_AND_AUTHOR else MatchLevel.EXACT_TITLE
        }
        val contained = incoming.any { a ->
            known.any { b ->
                val (short, long) = if (a.length <= b.length) a to b else b to a
                short.length >= MIN_CONTAINED_LENGTH && long.contains(short)
            }
        }
        return when {
            !contained || authorsDisagree -> MatchLevel.NONE
            authorsAgree -> MatchLevel.SIMILAR_TITLE_AND_AUTHOR
            else -> MatchLevel.SIMILAR_TITLE
        }
    }

    /** Higher is a stronger match; used to put the most likely entry first. */
    enum class MatchLevel(val rank: Int) {
        NONE(0),
        SIMILAR_TITLE(1),
        SIMILAR_TITLE_AND_AUTHOR(2),
        EXACT_TITLE(3),
        EXACT_TITLE_AND_AUTHOR(4),
        TRACKER(5),
    }
}
// KNS <--
