# EH3D-ANDROID-CONTROL - rules for R8 (the release build is shrunk and obfuscated).
# The app reads its JSON by hand (org.json), has no reflection of its own and no serialisation library, so nothing of its own needs to be kept. The coroutines and Compose
# ship the rules they need with their libraries.

# A crash report of a release should still say where it happened.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
