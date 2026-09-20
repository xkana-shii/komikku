package eu.kanade.tachiyomi.extension.model

// KMK --> Keep repository metadata fallback separate from installable updates.
fun Iterable<Extension.Available>.findMatchingExtension(
    installed: Extension.Installed,
    allowSignatureFallback: Boolean = false,
): Extension.Available? {
    val candidates = filter { it.pkgName == installed.pkgName }
    return candidates.filter { it.signatureHash == installed.signatureHash }
        .maxByOrNull { it.versionCode }
        ?: if (allowSignatureFallback) {
            candidates.filter { installed.store != null && it.store == installed.store }.singleOrNull()
                ?: candidates.singleOrNull()
        } else {
            null
        }
}

fun Extension.Installed.hasUpdateFrom(available: Extension.Available): Boolean =
    pkgName == available.pkgName && signatureHash == available.signatureHash &&
        (available.versionCode > versionCode || available.libVersion > libVersion)
// KMK <--
