# Building Privacy Display+

## Requirements

- JDK 21
- Android SDK Platform 36 and Build Tools 36.0.0

The Gradle Wrapper downloads Gradle 8.14.3 and verifies its checksum. Android
Gradle Plugin 8.13.0 and the app dependencies are downloaded on the first build.
Set `ANDROID_HOME`, or put your SDK location in an untracked `local.properties`.

## Build a debug APK

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

Linux or macOS:

```sh
./gradlew :app:assembleDebug
```

The APK is saved to `app/build/outputs/apk/debug/app-debug.apk`. Follow the
[setup instructions](README.md#setup) after installing it. A debug build uses
your local Android debug certificate; it cannot update an APK signed with a
different certificate.

## Tests and lint

```sh
./gradlew :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
python -m unittest discover -s ci/tests -v
```

The JVM tests cover window routing, per-app rules, temporary sessions, panel
boundaries, control access and the soft-reboot command. They do not measure
optical privacy. Device checks require the exact [supported firmware](SUPPORT.md).

The Xposed API is a `compileOnly` dependency and is not bundled in the APK.
Samsung APKs, framework files and firmware are not needed to build this project.

## Release APKs

Push a commit named `chore(release): bump version to X.Y.Z` to `main`, changing
`versionName` and increasing `versionCode` in `app/build.gradle.kts`. Add the
user-facing release notes in `releases/X.Y.Z.md` in the same commit. The Release
APK workflow builds that snapshot, runs release tests and lint, signs and verifies
the APK, and publishes `vX.Y.Z` with a SHA-256 checksum.

Published releases are preserved on reruns. A conflicting version tag fails the
workflow rather than replacing a release. Manual runs accept a commit or tag
already on `main`; publishing is off by default for manual runs.

Signing uses these repository Actions secrets:

- `ANDROID_SIGNING_KEY_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`

The keystore alias is `privacydisplayplus`. The expected certificate fingerprint
is pinned in the workflow. Keys are restored only to runner temporary storage
and removed after the job. APKs and checksums are also retained as workflow
artifacts for 14 days. Never commit signing keys or passwords.

## Android acceptance probe

The optional debug-only `DeviceControlProbe` is not part of the release APK.
`handle_style` renders the icon states, and `handle_thread` checks callback
delivery on the view owner thread. Operations that enable or disable protection
change a live test window; use a non-sensitive test app. The probe never confirms
or executes a soft reboot. See the source for its explicit arguments.
