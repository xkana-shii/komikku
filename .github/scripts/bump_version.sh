#!/usr/bin/env bash
set -euo pipefail

GRADLE_FILE="${GRADLE_FILE:-app/build.gradle.kts}"

calculate_next_version() {
    local current_version="$1"
    local suffix=""

    if [[ "$current_version" == *-MOD ]]; then
        suffix="-MOD"
        current_version="${current_version%-MOD}"
    fi

    if [[ ! "$current_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo "Invalid semantic version: $1" >&2
        return 1
    fi

    local major minor patch
    IFS='.' read -r major minor patch <<< "$current_version"

    if (( 10#$patch >= 10 )); then
        minor=$((10#$minor + 1))
        patch=0
    else
        patch=$((10#$patch + 1))
    fi

    printf '%d.%d.%d%s\n' "$((10#$major))" "$minor" "$patch" "$suffix"
}

bump_version() {
    if [[ ! -f "$GRADLE_FILE" ]]; then
        echo "Missing file: $GRADLE_FILE" >&2
        return 1
    fi

    mapfile -t version_codes < <(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+)[[:space:]]*$/\1/p' "$GRADLE_FILE")
    mapfile -t version_names < <(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([0-9]+\.[0-9]+\.[0-9]+)(-MOD)?"[[:space:]]*$/\1\2/p' "$GRADLE_FILE")

    if (( ${#version_codes[@]} != 1 || ${#version_names[@]} != 1 )); then
        echo "Expected exactly one versionCode and versionName declaration in $GRADLE_FILE" >&2
        return 1
    fi

    local current_vc current_vn new_vc new_vn temp_file
    current_vc="${version_codes[0]}"
    current_vn="${version_names[0]}"
    new_vc=$((10#$current_vc + 1))
    new_vn="$(calculate_next_version "$current_vn")"

    temp_file="$(mktemp "${GRADLE_FILE}.tmp.XXXXXX")"
    trap 'rm -f "$temp_file"' RETURN
    sed -E \
        -e "s/^([[:space:]]*versionCode[[:space:]]*=[[:space:]]*)${current_vc}([[:space:]]*)$/\1${new_vc}\2/" \
        -e "s/^([[:space:]]*versionName[[:space:]]*=[[:space:]]*)\"${current_vn}\"([[:space:]]*)$/\1\"${new_vn}\"\2/" \
        "$GRADLE_FILE" > "$temp_file"

    mapfile -t updated_version_codes < <(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+)[[:space:]]*$/\1/p' "$temp_file")
    mapfile -t updated_version_names < <(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([0-9]+\.[0-9]+\.[0-9]+)(-MOD)?"[[:space:]]*$/\1\2/p' "$temp_file")
    if (( ${#updated_version_codes[@]} != 1 || ${#updated_version_names[@]} != 1 )); then
        echo "Version update did not leave exactly one versionCode and versionName declaration" >&2
        return 1
    fi

    mv "$temp_file" "$GRADLE_FILE"
    trap - RETURN

    echo "versionCode $current_vc → $new_vc"
    echo "versionName $current_vn → $new_vn"

    if [[ -n "${GITHUB_ENV:-}" ]]; then
        echo "NEW_VN=$new_vn" >> "$GITHUB_ENV"
    fi
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    bump_version
fi
