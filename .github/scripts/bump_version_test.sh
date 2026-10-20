#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/bump_version.sh"

assert_version() {
    local current="$1"
    local expected="$2"
    local actual
    actual="$(calculate_next_version "$current")"
    if [[ "$actual" != "$expected" ]]; then
        echo "Expected $current to become $expected, got $actual" >&2
        exit 1
    fi
}

assert_version "1.22.0" "1.22.1"
assert_version "1.22.8" "1.22.9"
assert_version "1.22.9" "1.22.10"
assert_version "1.22.10" "1.23.0"
assert_version "1.22.11" "1.23.0"
assert_version "1.22.14" "1.23.0"
assert_version "1.23.0" "1.23.1"
assert_version "1.23.9" "1.23.10"
assert_version "1.23.10" "1.24.0"
assert_version "1.22.14-MOD" "1.23.0-MOD"

fixture_dir="$(mktemp -d)"
trap 'rm -rf "$fixture_dir"' EXIT
fixture="$fixture_dir/build.gradle.kts"

printf '%s\n' 'versionCode = 152' 'versionName = "1.22.14-MOD"' > "$fixture"

GRADLE_FILE="$fixture" "$script_dir/bump_version.sh" >/dev/null
grep -q '^versionCode = 153$' "$fixture"
grep -q '^versionName = "1.23.0-MOD"$' "$fixture"
[[ "$(grep -Ec '^[[:space:]]*versionCode[[:space:]]*=' "$fixture")" == 1 ]]
[[ "$(grep -Ec '^[[:space:]]*versionName[[:space:]]*=' "$fixture")" == 1 ]]

printf '%s\n' 'versionCode = 152' 'versionCode = 153' 'versionName = "1.22.14"' > "$fixture"
before="$(cat "$fixture")"
if GRADLE_FILE="$fixture" "$script_dir/bump_version.sh" >/dev/null 2>&1; then
    echo "Expected malformed fixture to fail" >&2
    exit 1
fi
[[ "$(cat "$fixture")" == "$before" ]]

echo "bump_version tests passed"
