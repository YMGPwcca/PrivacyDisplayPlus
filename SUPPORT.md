# Compatibility and support

## Supported device software

Privacy Display+ requires Samsung's built-in Privacy Display hardware and an
LSPosed installation that can hook System Framework and System UI.

| Device | Android / One UI | Firmware |
| --- | --- | --- |
| Samsung Galaxy S26 Ultra, SM-S948B | Android 17 / One UI 9 | S948BXXS4BZIG / OXM4BZIG |

The current firmware profile checks this exact build fingerprint:

```text
samsung/m3qxxx/m3q:17/CP2A.260605.016/S948BXXS4BZIG_OXM4BZIG:user/release-keys
```

Other builds leave the hooks disabled. An OTA can change internal Samsung
methods or resources even when the device name stays the same. Installing the
app on another Android version does not make its hooks compatible.

## What is supported

- Independent floating-window and split-screen settings.
- Per-app rules for apps selected in Samsung Privacy Display.
- Independent Off/On/Maximum dropdowns for Notification Center and Quick
  Settings. Maximum protects that panel and requests the shared display level
  while open; it cannot select an independent per-panel brightness.
- A Privacy Display button in Samsung's floating-window handle menu.
- A separate Soft reboot button using KernelSU's supported `ksud soft-reboot`.

The new system-panel controls have policy, persistence, migration and layout
checks. The owner confirmed the previous Notification Center protection worked.
The newly added independent Quick Settings and per-panel Maximum routes require
the owner-controlled hook reload and device trial. Control acknowledgement alone
does not prove physical, off-axis protection. Samsung's Conditions for turning
on must be enabled.

## Limits

- Samsung's compositor supports two independent privacy regions. Notification
  pop-ups and other protected windows may compete for them.
- Window regions stop 14 physical pixels short of the outer left/right panel
  edges and 16 pixels short of the top/bottom edges to satisfy the current
  compositor bounds checks. Floating regions are clipped to this same interval
  in the native move transaction while dragging, including movement along an
  edge. Optical validation of this floating-edge correction is pending.
- Full-screen Samsung privacy conditions, including manual privacy and secured
  screens, can still protect the whole display. Turning off one window does not
  cancel those independent conditions.
- Work profiles, Secure Folder, external displays, Recents cards and
  Picture-in-Picture are not supported by the added window controls.
- Brightness compensation and panel-driver modifications are not included.
- Maximum is a shared display level. An active app or open protected panel can
  request it; individual panels cannot select independent levels. Screen-wide
  dimming or color changes may accompany Maximum.
- Rotation, scrolling handle menus, larger display scales and every combination
  of multiple protected windows have not been fully checked on a device.

## Soft reboot and temporary root

The button is available only when a compatible KernelSU daemon exposes the
`soft-reboot` command. It reloads Android userspace, closes running apps and has
no fallback to a full device reboot. Save your work before using it.

Temporary root and LSPosed must already be working. This app does not install
root, unlock the bootloader, flash partitions or make temporary root persistent.
A full device reboot can end a temporary root session.

## Troubleshooting

**Setup required:** enable Privacy Display+ in LSPosed, select System Framework
and System UI, then soft reboot using your supported method.

**Unsupported software:** compare your firmware with the table above. Changing
the package name or enabling more LSPosed scopes will not add compatibility.

**Whole screen protected:** check the System panels switches, Samsung's manual
toggle, app rules and other full-screen privacy conditions.

**Window not protected:** turn on Samsung's Conditions for turning on and check
whether another protected window or notification is using a region.

Report a reproducible problem in [Issues](https://github.com/YMGPwcca/PrivacyDisplayPlus/issues).
Include the app version, firmware, LSPosed version, window type and steps. Remove
messages, account details and other personal content from screenshots and logs.
