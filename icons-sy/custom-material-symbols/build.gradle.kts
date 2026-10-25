plugins {
    id("mihon.library")
    id("mihon.library.compose")

    alias(libs.plugins.valkyrie)
}

android {
    namespace = "mihon.icons.sy.custommaterialsymbols"
}

spotless {
    format("svg") {
        target("src/**/*.svg")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

valkyrie {
    packageName = "mihon.icons.sy.custommaterialsymbols"
    generateAtSync = true

    imageVector {
        suppressUnusedReceiverWarning = true
    }

    iconPack {
        name = "SYCustomMaterialSymbols"
        targetSourceSet = "main"

        nested {
            name = "Rounded"
            sourceFolder = "rounded"
            autoMirror = false
        }
    }
}

dependencies {
    api(compose.ui)
}
