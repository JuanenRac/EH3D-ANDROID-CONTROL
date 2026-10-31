# Changelog

All notable changes to this project are documented here.

## [Unreleased]

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
