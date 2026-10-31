# Contributing to EH3D-ANDROID-CONTROL

Thank you for looking. This app can stop a machine and confirm the steps a job waits for, so changes are small and carefully tested.

1. **Nothing here moves or stops a real machine without proof.** A change to what the app sends to EH3D-SERVER (the stop, the confirmation, the start) needs a test of the request that is made and of the refusals, and a note of what was seen against a real server.
2. **Every behaviour has a test**, including the refusals: an address that is not allowed, an answer of the server that does not parse, a missing language text.
3. **Before a pull request:** `./gradlew test` and `python -I tools/ci_validate.py` must pass.
4. **Texts exist in eight languages** (English, Spanish, French, Italian, German, Chinese, Japanese and European Portuguese) in the tables of `app/src/main/assets/i18n`; a test keeps them complete. The README files keep the same structure.
5. **No files of other people's software or accounts.** Nothing secret goes in the repository: no keys, no passwords, no tokens.
6. Commits are written in English, small and about one thing.
