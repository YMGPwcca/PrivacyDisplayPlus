# Privacy Display+

An LSPosed module for window-level Samsung Privacy Display.

## Features

- Protect selected apps inside their floating windows or split-screen panes.
- Control floating windows and split screen independently.
- Set per-app rules: Default, Window only or Full screen for each window type.
- Request Maximum privacy protection automatically for individual selected apps.
- Toggle a floating window’s protection from its native handle menu.
- Use the separate Soft reboot button on supported KernelSU setups.

Samsung keeps its own app selection. Full-screen apps and other Samsung privacy
conditions keep their usual behavior. Recents protection and brightness controls
are not included.

## Requirements

Samsung Galaxy S26 Ultra **SM-S948B**, **Android 17 / One UI 9**, firmware
**S948BXXS4BZIG / OXM4BZIG**, and working LSPosed. Hooks are disabled on other
firmware. See [compatibility and limitations](SUPPORT.md).

## Download

Get the signed APK from [Releases](https://github.com/YMGPwcca/PrivacyDisplayPlus/releases/latest).

## Setup

1. Install the Privacy Display+ APK.
2. Enable **Privacy Display+** in LSPosed and select **System Framework**
   (`android`) and **System UI** (`com.android.systemui`).
3. Soft reboot using your supported method. If KernelSU exposes its soft-reboot
   command, you can use the button in Privacy Display+.
4. Open **Settings → Display → Privacy display → Conditions for turning on**.
   Turn on conditions and choose the apps you want to protect.
5. Open Privacy Display+. When it shows **Active**, choose your floating-window
   and split-screen settings. Changes apply immediately.

## Using the controls

**Floating windows / Split screen:** keep privacy within that window type.
Turning a switch off restores Samsung’s default behavior for that type.

**App rules:** choose Default, Window only or Full screen for a selected app.
Default follows the switches on the home screen.

**Maximum privacy protection:** in an app's rule dialog, enable the switch to
request Maximum while that app requests privacy protection. This also works for
full-screen apps. One level is shared by all protected windows: any active
Maximum request wins. When the last request ends, the current Samsung level
applies again. The switch does not select or enable protection for an app, and
screen-wide color or dimming changes can accompany Maximum. Rapid Maximum
switching has been checked with Discord on the supported firmware. Other app
and multi-window combinations still need device validation.

**Popup handle:** tap the handle of a floating window, then the phone-and-shield
button before Close. A filled background means protection is on. The choice
lasts for the current floating session.

**Soft reboot:** save your work first; running apps will close. The button uses
KernelSU’s supported userspace reload command and never falls back to a full
reboot. It does not make temporary root persistent.

Samsung’s manual privacy toggle and secured-screen conditions can still protect
the whole display. The panel supports two independent privacy regions, and split
protection leaves a small margin at the outer panel edges. See [SUPPORT.md](SUPPORT.md)
for details and troubleshooting.

## Build

```sh
./gradlew :app:assembleDebug
```

The APK is saved to `app/build/outputs/apk/debug/app-debug.apk`. See
[BUILDING.md](BUILDING.md) for requirements, Windows commands, tests and releases.

## License

[Apache-2.0](LICENSE). See [third-party notices](THIRD-PARTY-NOTICES.md).
