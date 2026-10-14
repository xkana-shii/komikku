import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.diffplug.spotless")
}

val libs = the<LibrariesForLibs>()

val xmlFormatExclude = buildList(2) {
    add("**/build/**/*.xml")

    projectDir
        .resolve("src/commonMain/moko-resources")
        .takeIf { it.isDirectory }
        ?.let(::fileTree)
        ?.matching { exclude("/base/**") }
        ?.let(::add)
}
    .toTypedArray()

spotless {
    kotlin {
        target("**/*.kt", "**/*.kts")
        targetExclude("**/build/**/*.kt")
        ktlint(libs.ktlint.core.get().version)
        trimTrailingWhitespace()
        endWithNewline()
        // KUN -->
        // The icons modules' generated R classes must be imported under an alias so
        // they are not mistaken for the consuming module's own R class.
        forbidRegex(
            "materialSymbolsRImport",
            "(?m)^import mihon\\.icons\\.materialsymbols\\.R(?! as MaterialSymbolsR$)\\b.*$",
            "Import mihon.icons.materialsymbols.R as MaterialSymbolsR",
        )
        forbidRegex(
            "materialSymbolsRQualified",
            "(?m)^(?!import ).*\\bmihon\\.icons\\.materialsymbols\\.R\\.",
            "Use the MaterialSymbolsR import alias instead of the qualified name",
        )
        forbidRegex(
            "customMaterialSymbolsRImport",
            "(?m)^import mihon\\.icons\\.custommaterialsymbols\\.R(?! as CustomMaterialSymbolsR$)\\b.*$",
            "Import mihon.icons.custommaterialsymbols.R as CustomMaterialSymbolsR",
        )
        forbidRegex(
            "customMaterialSymbolsRQualified",
            "(?m)^(?!import ).*\\bmihon\\.icons\\.custommaterialsymbols\\.R\\.",
            "Use the CustomMaterialSymbolsR import alias instead of the qualified name",
        )
        // KUN <--
    }
    format("xml") {
        target("**/*.xml")
        targetExclude(*xmlFormatExclude)
        trimTrailingWhitespace()
        endWithNewline()
    }
}
