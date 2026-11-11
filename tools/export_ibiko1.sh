#!/bin/sh
# Builds Ibiko 1 as a standalone Android library folder, ready to copy into another project or publish:
#   sh tools/export_ibiko1.sh [out-folder]        (default: build/ibiko1)
# Then, in that project: settings.gradle  include ":ibiko1"   and   app/build.gradle  implementation project(':ibiko1')
# Inside Ibiko it lives in the app's own sources (app/src/main/java/ai/ibiko/ibiko1), so every builder, CodeAssist on
# the device included, builds it with the app.
set -e
cd "$(dirname "$0")/.."
OUT=${1:-build/ibiko1}
rm -rf "$OUT"
mkdir -p "$OUT/src/main/java/ai/ibiko" "$OUT/src/main/assets/knowledge"
cp -R app/src/main/java/ai/ibiko/ibiko1 "$OUT/src/main/java/ai/ibiko/ibiko1"
mv "$OUT/src/main/java/ai/ibiko/ibiko1/README.md" "$OUT/README.md"
for f in world_facts.tsv country_more.tsv elements_more.tsv local_facts.tsv lexicon.tsv \
         WORLD_FACTS_LICENSE.txt MORE_FACTS_LICENSE.txt COMPREHENSION_LICENSE.txt; do
  cp "app/src/main/assets/knowledge/$f" "$OUT/src/main/assets/knowledge/$f"
done
cat > "$OUT/build.gradle" <<'GRADLE'
// Ibiko 1: a language brain as an Android library. Entry point: ai.ibiko.ibiko1.Ibiko1Model.
plugins {
    id "com.android.library"
}

android {
    namespace "ai.ibiko.ibiko1"
    compileSdk 35

    defaultConfig {
        minSdk 34
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}
GRADLE
cat > "$OUT/src/main/AndroidManifest.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<!-- Ibiko 1's learning job, merged into the app that uses the library. -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <service
            android:name="ai.ibiko.ibiko1.LearningJob"
            android:exported="false"
            android:permission="android.permission.BIND_JOB_SERVICE" />
    </application>
</manifest>
XML
echo "Ibiko 1 exported to $OUT"
