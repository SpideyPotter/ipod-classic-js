#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/workspace/android-tools/jdk-21.0.6+7}"
export ANDROID_HOME="${ANDROID_HOME:-/workspace/android-sdk}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-/workspace/android-user}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/workspace/.gradle-android}"
exec ./gradlew "${@:-:app:assembleDebug}" --console=plain
