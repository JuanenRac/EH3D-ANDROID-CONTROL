# Differences between the Android and the iPhone app

The iPhone app (EH3D-IOS-CONTROL) is a port of this one: whatever one app learns, the other learns too. This page lists what the two systems make different, so a difference is a decision and not a slip.

## What is the same

* The rules live in plain code with no screen in it: the summary before a job starts (`model/Summary.kt`), the state of the link (`model/ServerLink.kt`), what became of a stop (`model/Stop.kt`) and what is told when a machine changes (`fleetNotices`). The iPhone app has the same files in `Sources/Eh3dCore`.
* Both apps walk the same case files (`app/src/test/resources/shared-cases` here, `Tests/Eh3dCoreTests/Cases` there): the summary, the link, the failures, the stops, the notices, the addresses and the fields of the answers that are read. The files are identical in both repositories; when one changes, copy it to the other.
* The test of the fields fails when an example of the server carries a field that the app neither reads nor lists as ignored, and when a field listed as read is nowhere in the code.
* The safety of the machines of the plugins is the same logic in both apps (`model/WorkSafety.kt` here, the same rules in `Sources/Eh3dCore` there): the SIMULATOR ONLY badge, the interlocks, who may stop, release, start and cancel, the queue of a machine and the switches of the server. Nothing in them depends on the system.
* The 3D printers the server watches are read the same way in both apps (`model/Printers.kt` on Android, `Sources/Eh3dCore/Printers.swift` on the iPhone) and walk the same file of cases (`printer_cases.json`): read only, with `GET /printers` each time the home page is refreshed; what a printer did not say is unknown, never zero; a list that is switched off (501), missing (404) or not allowed (403) shows nothing; a server that does not answer keeps the last list, marked as old; the access code, the address and the serial number are never read.
* The texts are the same Spanish phrases in the same tables of the eight languages (7 files: Spanish is the source and has none) (`app/src/main/assets/i18n` here, `Sources/Eh3dCore/Resources/i18n` there).
* The authorization of a job of the generic queue works the same in both apps: each job of a machine shows the first twelve characters of its `toolpath_sha256` with a badge (not authorized, authorized, expired when the authorized hash is not the present one); the button *Authorize* (an operator, a job that waits, scope `jobs:authorize` checked by the server) asks a second time and then sends `POST /work/jobs/{id}/authorize` with the hash that was shown; the start asks twice and sends the same hash. A `stale_authorization` or `authorization_required` makes the app read the queue again, so the job is shown with its present hash and has to be authorized again.

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
| The search for the server on the local network (`_eh3d._tcp`, off by default) | `NsdManager` (`ServerDiscovery.kt`), IPv4 addresses only; the person chooses what it found | Bonjour (`NWBrowser`) needs the permission of the local network (`NSLocalNetworkUsageDescription`) and is to be tried on an iPhone: not there until then |
| Updates | Downloads the APK and hands it to the installer | Tells that a version exists and links to it |
| Certificate of a secure server | Not pinned; the phrase is not in its tables | Pinned the first time it is seen; one extra phrase in the 7 tables says so |
| The stream of the head camera | Also stops while the app is out of sight and can decode at a smaller size for the grid | Read with `URLSession` and stops with its screen |

## Not there in either app yet

* Anything about the 3D printers but reading them: registering, changing and removing them is done on the server, nothing can be sent to a printer, there is no notice when a print finishes or fails, and the list is not updated by the `printer` event of the websocket.
* Registering, changing and removing the nodes of the pick-and-place machines, asking a node now, and the update of the list by the `pnp_node` event of the websocket: the apps only read `GET /pnp/nodes` (read only, no token) each time the home page is refreshed, and show nothing while the server has the nodes switched off.
* A stop of a machine of a plugin that the server refused because the session had ended is not sent again after signing in (the stops of the drilling and routing machines are): the person is told at once that it did not arrive and to use the physical stop.
* Jog (moving an axis by hand) and a live progress stream of a job: the server has no route for jog and sends progress only in the job (`percent`, `items_done`, `holes_done`, `estimate_s`), which both apps already show; they will follow when the server offers the route.

## Not run yet

The Swift tests and the screens of the iPhone app were written without a Mac: the first run is the CI of that repository.
