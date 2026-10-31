#!/usr/bin/env bash
# EH3D-ANDROID-CONTROL build launcher: the tests and the debug APK. GPL-3.0-or-later.
cd "$(dirname "$0")" && ./gradlew test assembleDebug
