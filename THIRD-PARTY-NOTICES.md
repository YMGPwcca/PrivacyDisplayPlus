# Third-party components

## Runtime libraries

- Material Components for Android 1.14.0 — Apache License 2.0.
  [Project](https://github.com/material-components/material-components-android)
- AndroidX libraries and their transitive dependencies — the licenses supplied
  by their upstream projects. [AndroidX](https://android.googlesource.com/platform/frameworks/support/)

## Build and test components

- Xposed Framework API 82, `de.robv.android.xposed:api:82` — Apache License 2.0.
  This is a compile-only dependency and is not bundled in the app.
  [Project](https://github.com/rovo89/XposedBridge)
- Gradle Wrapper — Apache License 2.0. Its license is also included in
  [gradle/wrapper/LICENSE](gradle/wrapper/LICENSE).
- JUnit 4.13.2 — Eclipse Public License 1.0. Test-only dependency.
  [Project](https://github.com/junit-team/junit4)

## Artwork

The phone/shield/eye/plus app artwork was supplied for this project and adapted
for Android's adaptive icon format. See [artwork/icon](artwork/icon).
The Help glyph is Google Material Icons `help_outline`, licensed under Apache
License 2.0. [Source](https://github.com/google/material-design-icons/blob/master/src/action/help_outline/materialicons/24px.svg)

## Samsung integration

Samsung Privacy Display and the native popup controls belong to Samsung. The
module uses resources from the installed System UI at runtime; Samsung's APKs,
firmware, decompiled sources and native icon files are not distributed here.
This project is independent of Samsung and is not endorsed by Samsung.

## Release tooling

The release workflow and planner are adapted from
[FacebookGoodies](https://github.com/YMGPwcca/FacebookGoodies), Apache License 2.0.
