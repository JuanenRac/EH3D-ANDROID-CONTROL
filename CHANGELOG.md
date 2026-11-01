# Changelog

All notable changes to this project are documented here.

## [Unreleased]

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
