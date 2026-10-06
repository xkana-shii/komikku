# Komikku profiles and startup benchmarks

This module replaces `macrobenchmark`. It reads the tested application's ID from
Gradle, including fork IDs and variant suffixes. The app's existing `benchmark`
build type remains available to `.github/workflows/build_benchmark.yml`.

Generate profiles on the configured Pixel 6/API 34 managed device:

```sh
./gradlew :app:generateBaselineProfile
```

Use an already connected Android device or emulator instead:

```sh
./gradlew :app:generateBaselineProfile '-PbaselineProfile.useConnectedDevices=true'
```

Use an English device locale for the navigation selectors. Generation covers
cold startup and Library, Updates, History, Browse/Extensions, and More navigation.
Benchmark/non-minified builds suppress updater, onboarding, and changelog dialogs.
Generated output belongs in `app/src/main/baselineProfiles`; do not copy profiles
from TachiyomiSY or fabricate entries.

On Windows, use `gradlew.bat` and keep the dotted Gradle property quoted in
PowerShell. The app build resolves the Kotlin source directories copied by the
profile plugin explicitly to avoid an invalid literal `provider(?)` path.

Run timing comparisons on a physical device:

```sh
./gradlew :baseline-profile:connectedBenchmarkReleaseAndroidTest
```

The cold, warm, and hot benchmarks compare no compilation, partial compilation
with/without baseline profiles, and full compilation.
