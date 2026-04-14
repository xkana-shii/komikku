#!/bin/bash
set -e

GRADLE_FILE="app/build.gradle.kts"

current_vc=$(grep "versionCode =" "$GRADLE_FILE" | grep -o '[0-9]*')
current_vn=$(grep "versionName =" "$GRADLE_FILE" | sed 's/.*"\(.*\)".*/\1/' | sed 's/-MOD//')

IFS='.' read -r major minor patch <<< "$current_vn"

new_vc=$((current_vc + 1))
new_vn="${major}.${minor}.$((patch + 1))"

sed -i "s/versionCode = $current_vc/versionCode = $new_vc/" "$GRADLE_FILE"
sed -i "s/versionName = \"$current_vn\"/versionName = \"$new_vn\"/" "$GRADLE_FILE"

echo "versionCode $current_vc → $new_vc"
echo "versionName $current_vn → $new_vn"

[ -n "$GITHUB_ENV" ] && echo "NEW_VN=$new_vn" >> "$GITHUB_ENV"