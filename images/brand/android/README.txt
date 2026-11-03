Adaptive launcher icon for the Android app.

To use it, copy the mipmap-* folders of res/ into app/src/main/res/ (they
replace the launcher icons that are there) and make sure the manifest points to
android:icon="@mipmap/ic_launcher" and android:roundIcon="@mipmap/ic_launcher_round".
mipmap-anydpi-v26 holds the adaptive icon (background, foreground and the
monochrome layer for themed icons); the PNG files in the other folders serve
Android 7 and older. Nothing in the app is changed by this folder.
