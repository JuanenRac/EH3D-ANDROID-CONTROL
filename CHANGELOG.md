# Changelog

All notable changes to this project are documented here.

## [Unreleased]
- **The page of a job says who started it** as well as who stopped it, with the text the machine page already uses.
- **The case files shared with the iPhone app are checked in CI:** `tools/check_shared_cases.py` compares them with the ones of the other repository (the sibling folder, or the public one on GitHub) and fails when one differs, is missing or cannot be read; its own test runs before it.
- **Brand:** Add the application icon and the logos of the program.
- **A watchdog for the manual build:** `.github/workflows/build-watchdog.yml` looks every hour for a build that stays queued or running longer than the limit in `.github/build-watchdog.json` and opens (or updates) an issue `build-stuck`, which it closes when no build is stuck.
- **The release APK is built in GitHub Actions:** `.github/workflows/apk-build.yml` (manual, owner only) builds it with JDK 21 and Gradle without any cache, checks the application id, the version name and a version code that rises, writes `SHA256SUMS`, a build manifest and a provenance attestation, and opens an issue `build-failed` when it fails; the APK leaves unsigned and is signed on the maintainer's computer. See `docs/BUILD.md`.
- **Shared checks updated:** the validator also compares the badge and the Version row of the README with the manifest, fails when one of its checks cannot run, and the CHANGELOG step of the workflow reads the base branch from the environment.
- **More tests:** the preparation of a job, the link, the stops, the drawing and the summary from their edges, the tags and the releases of the update channel, and the addresses an operator types.
- **A version tag with a number too big for an integer is refused** instead of ending in an exception, and an address with a port that does not exist (0 or above 65535) is no longer accepted.
- **The workflows are hardened:** every action is pinned by its commit with its version in a comment, and the checkouts keep no credentials.
- **More tests of the update channel:** which tags are versions, which release is newer and which link of the APK is accepted.

## [0.0.8] - Summary before a start, an honest link and shared test cases

- **A summary before a job starts:** the dry run of the exact plan (how far each axis goes, the steps and the time) and the warnings of the server (a work that looks tiny, too big, far from the corner, stretched or past the course of an axis) are read, and Start shows what the job does, how long it takes, how far each axis goes and what looks wrong, with Start and Cancel; a setting turns it off (on by default) and then Start only asks once more.
- **The refusal `outside_course` has its own clear text** in the eight languages (the plan takes an axis past its course; move the work on the table or let the machine's tuning warn).
- **The state of the connection is told honestly:** connected, data that is getting old (with its age after five seconds), no connection only after two failures in a row (one blink of the Wi-Fi no longer shows it), and an address that answers another service; the retries slow down from 1.5 s to 10 s while the server is away and a button tries again now.
- **Pressing the stop without a connection warns** that the order may not arrive and to use the physical stop of the machine; a stop that did not reach the server says so plainly, and after a stop that did, the app says it was sent, then confirmed or that the machine still works.
- **Case files shared with the other phone app:** the summary, the link, the failures, the stops, the notices, the addresses and the fields of the answers are tested from the same files in both apps, and a field that the server adds to its examples fails the test until the app reads it or says it ignores it.
- **A job says who started it and who stopped it:** the page of a job shows who stopped it (the server now sends both names).
- **Differences between the two phone apps are written down** in `docs/PLATFORM_DIFFERENCES.md`.
- **The answers of the server are read in the tests from its own examples:** the fleet, the jobs, a job, the preview, the spindle and an error are the files that the tests of the server make, so a change in the answers that the app does not follow fails here.
- **Issue forms and a pull-request template:** the repository now has the bug report (it asks for the versions that `eh3d-updater --cli status --offline` lists), documentation, improvement and hardware validation forms (with the machine used) and the pull-request template of the suite.

## [0.0.7] - The release no longer closes when it starts

- **The app closed at once when it started (version 0.0.6, the installed release):** the periodic watch of the fleet brought WorkManager, which keeps its jobs in a database that is made by reflection through its empty constructor; the shrinker of the release removed that constructor, so the app failed before it drew anything. The constructor is kept now. The debug build is not shrunk, which is why the tests did not show it.
- **The CI builds the release too and checks it:** `assembleRelease` runs besides the debug build, and `tools/check_release_build.py` fails when the shrinker removed the empty constructor of a generated class (`*_Impl`), the failure that closed the app.

## [0.0.6] - Starting a job asks once more, the emergency stop asks the server to stop the machine and signing out closes the session

- **Starting a job from its page asks once more** (check that the machine is prepared and clear), as it does from Prepare, with the same words in every language; a touch by mistake no longer sets the machine going.
- **The emergency stop asks the server to stop the machine** (`POST /machines/:id/stop`), whether or not the app knows which job runs on it, and shows the error if it did not get there; the camera asks for a one-use ticket instead of putting the token of the session in the address.

- **Notices with the app closed:** a periodic job (WorkManager, every 15 minutes at best) asks the server with the session kept in the vault and tells a machine that waits for the operator or a job that ended; signing out stops it. The rule that decides what is told is one function used by the screen and by the job.

- **An answer that is not JSON** (another web service at the address) is an error on the screen, not a crash.
- **Signing out closes the session on the server** (the call goes with the token that is being closed), and a slow refresh of another machine no longer paints its spindle or sensors on the page of this one.
- Clearer messages for a wrong setup code, a weak password and a bad name, in the eight languages.

## [0.0.5] - Resume a stopped job and the GRBL switch in read only

- **Go on from where a job stopped, with the file the server kept:** a job that failed or was cancelled (drilling, routing or G-code) has a button that asks the server (`resume-from`) for a new job made from the saved request, with the holes, paths or lines already done left out; the file is not picked again. The new job opens on its page, ready and never started, and says which job it goes on from. Messages for the new refusal `not_stopped` and for the new texts in the seven tables of languages. Written; Gradle was not run.
- **The switch of the `$` lines of GRBL, read only:** the page of a machine that takes G-code says whether the administrator allowed the `$` lines of GRBL (`$H` and the like) for that machine; it is changed in Studio. An older server that does not say is read as not allowed.
- 2 tests more (the job that can be gone on from and the switch), written and not run.

## [0.0.4] - Prepare a job, G-code jobs and the sensors of the drill head

- **Prepare job:** the page of a free machine has a Prepare job page. Choose what is done (drilling, routing, both or a free G-code program; a machine takes only what its connection allows), pick the file with the system file picker (the server has no list of files, so the text of the file is sent in the request that plans the job; an optional tool table can be picked too), and the server plans it without creating anything. The page lists the tools of the file in the order the job does them, with the count of holes or paths, the diameter when an Excellon file declares it and the spindle speed; switch tools off (`skip_tools`) or start at one tool (the ones before it in the order of the job are left out, as in BUNGARD-GUI); the spindle warnings of the plan are shown. Start asks for a confirmation, creates the job and starts it. With both, a drilling job and a routing job are created and the first starts; the second stays ready in the Jobs tab, because a job never starts by itself.
- **G-code jobs:** pick a G-code file and create a `gcode` job on a machine whose connection is the simulator or GRBL; the page shows the lines that will be sent, the area and the estimate. A running G-code job can be paused and resumed (the held wait and the wait after a stop both say what they are), cancelled, and a failed or stopped one can be continued from the line it reached (`skip` with the `line` of the job; the same program is prepared again, asking for the file when this session does not have it, and the machine waits for the confirmation before its first move). The spindle and the coolant are started again only with the switch "start the spindle and the coolant again", which is off by default.
- **Messages and help:** new messages for the refusals of the server when preparing a job (`bad_gcode`, `bad_ncd`, `unknown_tool`, `bad_tool`, `out_of_table`, `wrong_machine`, `too_big`, `not_pausable`, `not_in_state`), a help topic for the page and 53 phrases in the seven tables of languages.

## [0.0.3] - Spindle of the machine, tools switched off in a job and the sensors of the drill head

- **What the sensors of the drill head say:** the card of the head shows how the last reading of the laser went (valid, weak signal, too much spread, out of range, sensor fault) with the distance, how long ago it was taken and the offset the laser was calibrated with; whether the pulses of the speed sensor are clean or noisy and how many were refused as noise; how many temperature sensors the node found on the wire, with a warning when it only guesses which one to read; and the version of the node. A node that does not say (an older one) shows nothing of this. 17 phrases in the seven tables of languages. The settings of the image of the camera, the calibration of the laser and the choice of the temperature sensor stay in the panel of the node: the server does not hand them on yet.
- **The spindle of the machine:** the page of a machine shows which spindle it carries (the original one or the one with a variable-frequency drive, or none), its limits and the top speed the node should be set to; an administrator chooses the spindle and what the server does with a job that asks for more (refuse it, or let it through cut down to the top of the spindle). A job shows the excesses it was let through with, the tools that were switched off for it and the holes or paths already done that it leaves out. The messages for a job refused by the spindle or left with nothing to do are in the eight languages. Choosing a drilling job, a routing job or both, and switching tools off, is not here: the app does not prepare jobs.

## [0.0.2] - The ring of LEDs and the quality check, and the first screens of the other apps

- **The ring of LEDs and the quality check:** the page of a machine has the ring of LEDs of the head (off and three levels) and a Quality check card that asks the server to look at the board with the camera of the head, judges the holes and the milled channels and shows the verdict as big as a sign (OK, NOT OK or cannot judge) with the findings, the profile (standard, fine pitch, prototype) and the calibration of the scale; the last check of the machine is shown when the page opens. 48 phrases in the seven tables of languages.
- **The first screens in the manner of the other apps of the suite:** the splash with the mark and the name, the wait while the session of the last time is restored, and the sign-in with icons, the eye to show the password, the panel with a thin edge and the note about what is kept on the phone.

## [0.0.1] - The first version: the machines, the stop, the job in 2D and 3D and the drill head

- **The machines of the workshop in real time:** the fleet as EH3D-SERVER reports it (free, working, waiting for the operator), with the job of each machine, its progress, the steps and holes done and the time left, read every second and a half while the app is in sight; a notice when a machine waits for the operator or stops.
- **The emergency stop:** a red button for one machine and another for all of them; each asks a second time before it cancels the running job through the server, and only an operator or an administrator can press it. The confirmation of the step that a job waits for (change the tool, check the spindle) is made from the app.
- **The job in 2D and in an isometric view:** the board, the holes (a ring until they are drilled), the paths (dim until they are cut) and the head, where the steps done put it; the isometric view turns with a finger.
- **The drill head:** the camera of EH3D-NODE-BUNGARD-AUX as the server relays it, the turns and the temperature of the spindle, the protect output, and one measure of the table with the laser.
- **Jobs:** the list of the jobs of the server and of each machine, the start of a job that is ready (a job never starts by itself), its cancellation and its removal.
- **Sign-in:** the address of the server (only a private network address, `localhost` or https), the first administrator of a server that has none, the session kept sealed on the phone and an optional "remember me".
- **Eight languages and the Forge theme:** English, Spanish, German, French, Italian, Japanese, Chinese and European Portuguese, with a help and an about page; the same colours as the EH3D programs on the computer.
- **Update from GitHub:** the app looks at the newest release of this project, downloads the APK, checks the package name, the version and the signature, and hands it to Android's installer; it never installs by itself.
- **Tests on the JVM:** the readers of the server's JSON, the position of the head and the geometry of the views, the address and version checks, the sealed session and the languages.
