# Differences between the Android and the iPhone app

The iPhone app (EH3D-IOS-CONTROL) is a port of this one: whatever one app learns, the other learns too. This page lists what the two systems make different, so a difference is a decision and not a slip.

## What is the same

* The rules live in plain code with no screen in it: the summary before a job starts (`model/Summary.kt`), the state of the link (`model/ServerLink.kt`), what became of a stop (`model/Stop.kt`) and what is told when a machine changes (`fleetNotices`). The iPhone app has the same files in `Sources/Eh3dCore`.
* Both apps walk the same case files (`app/src/test/resources/shared-cases` here, `Tests/Eh3dCoreTests/Cases` there): the summary, the link, the failures, the stops, the notices, the addresses and the fields of the answers that are read. The files are identical in both repositories; when one changes, copy it to the other.
* The test of the fields fails when an example of the server carries a field that the app neither reads nor lists as ignored, and when a field listed as read is nowhere in the code.
* The texts are the same Spanish phrases in the same eight tables (`app/src/main/assets/i18n` here, `Sources/Eh3dCore/Resources/i18n` there).

## What the system makes different

| Subject | Android | iPhone |
|---|---|---|
| The summary before a job starts | A dialog | A sheet |
| The warning in the question of a stop when there is no connection | In red | Added to the text of the dialog (the system does not colour it) |
| The clock of the link and of the stops | `SystemClock.elapsedRealtime` | `DispatchTime.now().uptimeNanoseconds` |
| Where the setting of the summary is kept | `SharedPreferences` | The preference store of the app |
| Notices with the app closed | A periodic job (WorkManager, every 15 minutes at best) | Only while the app is in sight |
| An address with a path after the host (`http://192.168.0.5:8080/x`) | The path is dropped | The address is refused |
| Two addresses for one installation (at home and away) | Not there | `DualEndpoint` |
| Updates | Downloads the APK and hands it to the installer | Tells that a version exists and links to it |

## Not run yet

The Swift tests and the screens of the iPhone app were written without a Mac: the first run is the CI of that repository.
