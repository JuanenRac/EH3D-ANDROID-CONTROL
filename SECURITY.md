# Security policy

EH3D-ANDROID-CONTROL is an app that signs in to an EH3D-SERVER and can stop the machines of a workshop and confirm the steps their jobs wait for. It sends a password and keeps a session token on the phone.

What the app does about it, and what is tested on a computer:

* The password is sent only to an address of the local network (a private IPv4 address or `localhost`) or over https; any other address without https is refused before anything is sent.
* The session token is kept sealed with a key that lives in the Android Keystore and never leaves the phone; a "remember me" option, off by default, is what keeps that token between launches: it expires 30 days after the sign-in, signing out revokes it at the server, and neither the user nor the password is ever kept (an earlier version could keep them: they are deleted at the first start).
* A user who may only watch cannot stop, start or confirm: the buttons are off, and the server refuses the request anyway.
* The stop asks twice before it cancels a running job.
* The update from GitHub downloads only the file of this project over https, and checks the package name, the version and the signature before Android is asked to install it; Android makes the same check again.

What it does **not** do: it is not a safety system and does not replace the emergency stop of the machine; it needs the network and the server to be working; it has had no independent review. It has not been run on a phone against a real server yet.

## Reporting a problem

Please report a vulnerability privately to **electrohobby3d@gmail.com**, with the steps that show it. Do not open a public issue for it. You will get an answer as soon as possible; this is a one-person project.
