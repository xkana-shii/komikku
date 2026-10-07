
#!/usr/bin/env bash
set -euo pipefail

GRADLE_FILE="app/build.gradle.kts"

if [[ ! -f "$GRADLE_FILE" ]]; then
    echo "Missing file: $GRADLE_FILE" >&2
    exit 1
fi

mapfile -t version_codes < <(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+)[[:space:]]*$/\1/p' "$GRADLE_FILE")
mapfile -t version_names < <(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([0-9]+\.[0-9]+\.[0-9]+)(-MOD)?"[[:space:]]*$/\1/p' "$GRADLE_FILE")

if (( ${#version_codes[@]} != 1 || ${#version_names[@]} != 1 )); then
    echo "Expected exactly one versionCode and versionName declaration in $GRADLE_FILE" >&2
    exit 1
fi

current_vc="${version_codes[0]}"
current_vn="${version_names[0]}"

IFS='.' read -r major minor patch <<< "$current_vn"

new_vc=$((10#$current_vc + 1))
new_vn="${major}.${minor}.$((10#$patch + 1))"

sed -i -E "s/^([[:space:]]*versionCode[[:space:]]*=[[:space:]]*)${current_vc}([[:space:]]*)$/\1${new_vc}\2/" "$GRADLE_FILE"
sed -i -E "s/^([[:space:]]*versionName[[:space:]]*=[[:space:]]*)\"${current_vn}(-MOD)?\"([[:space:]]*)$/\1\"${new_vn}\"\3/" "$GRADLE_FILE"

echo "versionCode $current_vc → $new_vc"
echo "versionName $current_vn → $new_vn"

if [[ -n "${GITHUB_ENV:-}" ]]; then
    echo "NEW_VN=$new_vn" >> "$GITHUB_ENV"
fi
