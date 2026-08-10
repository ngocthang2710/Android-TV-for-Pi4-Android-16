# Android TV for Raspberry Pi 4 (AOSP 16, `aosp_rpi4_tv`)

Patch set for **AOSP's `aosp_rpi4_tv` product** -- the stock Android TV
(Leanback) build that AOSP already ships for the Raspberry Pi 4
(`device/brcm/rpi4`), on top of Google's reference TV device tree
(`device/google/atv`). This repo does **not** carry a full device tree copy;
it tracks the local fixes/customizations applied on top of a stock
`android-16.0` checkout, as patches against the exact AOSP paths they touch.

This is a sibling project to
[Android-Auto-for-Pi4-Android-16](../Android-Auto-for-Pi4-Android-16) (the
`aosp_rpi4_car` build on the same board) -- same hardware, different AOSP
product/form-factor.

## Status

**Fixed:**
- Wi-Fi icon on the home screen showed `<unknown ssid>` even while
  genuinely connected with a good signal. See below.

## Fix: Wi-Fi network name showed `<unknown ssid>` on the home screen

### Symptom
Device connects to Wi-Fi successfully (gets an IP, browses fine), but the
network tile on the TV launcher's home screen shows the icon as connected
while the label reads `<unknown ssid>` instead of the real network name.

### Root cause
`NetworkLaunchItem` (part of AOSP's stock
`device/google/atv/TvSampleLeanbackLauncher`, the default home app used by
`aosp_rpi4_tv`) reads the connected network's name via
`WifiManager.getConnectionInfo().getSSID()`. Since Android 8, that call only
returns the real SSID to a caller that holds `ACCESS_FINE_LOCATION`
**and** has system-wide Location turned on -- otherwise the platform
redacts it to the literal string `"<unknown ssid>"`, regardless of whether
the device is actually connected.

`TvSampleLeanbackLauncher`'s manifest never requested
`ACCESS_FINE_LOCATION` (only `ACCESS_WIFI_STATE`/`ACCESS_NETWORK_STATE`),
so the SSID was unconditionally redacted for every user, on every build,
Location toggle notwithstanding.

### Fix
1. Add `ACCESS_FINE_LOCATION` to `TvSampleLeanbackLauncher`'s manifest.
2. Because this device has **no remote/keyboard by default** (a bare
   touch panel is the only input at first boot), there is no reliable way
   for the launcher to walk the user through the normal runtime-permission
   dialog. Instead, the permission is **silently pre-granted** at the
   platform level via a `default-permissions.xml` exception
   (`device/brcm/rpi4/permissions/default-permissions-rpi4-tv.xml`), the
   same mechanism AOSP itself uses to grant TV Settings its own location
   permission. Wired into the build via a small `Android.bp` +
   `PRODUCT_PACKAGES` entry.

Location itself still has to be turned on once by the user (Settings ->
Privacy -> Location) -- that part is an intentional user-facing privacy
toggle, not something this fix forces on silently.

### Result (verified on real hardware, 2026-08-10)
- Before: `dumpsys wifi` showed a fully connected session
  (`SSID: "Phong 14"`, IP `192.168.1.4`, RSSI -28) while the launcher tile
  still read `<unknown ssid>`.
- After granting `ACCESS_FINE_LOCATION` to
  `com.example.sampleleanbacklauncher` and enabling Location, the same tile
  correctly reads **"Phong 14"**.
- Confirmed the fix's `default-permissions-rpi4-tv.xml` file actually lands
  on `/vendor/etc/default-permissions/` in a flashed build.

### A gotcha worth knowing if you touch `default-permissions.xml` again
On a **local `eng`/`userdebug` build re-flashed over an existing
`userdata`** (i.e. no factory reset), a *new* `default-permissions.xml`
exception does **not** take effect just by flashing `system.img`/
`vendor.img`. `PackageManagerService.systemReady()` only re-runs
`grantDefaultPermissions()` (which reads that file) when
`Build.FINGERPRINT` differs from the value it last granted with, or when a
user is newly created. Local eng builds keep a static `BUILD_ID` across
compiles, so the fingerprint never changes between "before the fix" and
"after the fix" -- the platform never notices anything upgraded, and the
new exception sits on disk unused.

- **Verifying a fix like this without wiping the device:** grant it by
  hand once, `adb shell pm grant <pkg> android.permission.ACCESS_FINE_LOCATION`.
- **Real devices are unaffected:** a genuine first boot (or a factory
  reset) always creates a new user, which always runs
  `grantDefaultPermissions()`, so the exception applies automatically —
  no manual `pm grant` needed for an actual end user.

## Repo layout

Only the diffs/new files are kept, as patches against the exact AOSP paths
they touch -- there's no full device tree copy here.

### `device-patches/` -- against `device/brcm/rpi4/`

| In this repo | Applies to / installs at |
|---|---|
| `device-patches/aosp_rpi4_tv.mk.patch` | `git apply` inside `device/brcm/rpi4/`, patches `aosp_rpi4_tv.mk` |
| `device-patches/permissions/Android.bp` | `device/brcm/rpi4/permissions/Android.bp` (new file) |
| `device-patches/permissions/default-permissions-rpi4-tv.xml` | `device/brcm/rpi4/permissions/default-permissions-rpi4-tv.xml` (new file) |

### `device-patches/TvSampleLeanbackLauncher/` -- against `device/google/atv/TvSampleLeanbackLauncher/`

| In this repo | Applies to |
|---|---|
| `device-patches/TvSampleLeanbackLauncher/AndroidManifest.xml.patch` | `git apply` inside `device/google/atv/TvSampleLeanbackLauncher/`, patches `src/main/AndroidManifest.xml` |

## Building

```
cd <AOSP_ROOT>/device/brcm/rpi4
git apply <THIS_REPO>/device-patches/aosp_rpi4_tv.mk.patch
cp -r <THIS_REPO>/device-patches/permissions .

cd <AOSP_ROOT>/device/google/atv/TvSampleLeanbackLauncher
git apply <THIS_REPO>/device-patches/TvSampleLeanbackLauncher/AndroidManifest.xml.patch

cd <AOSP_ROOT>
source build/envsetup.sh
lunch aosp_rpi4_tv bp4a userdebug
m systemimage vendorimage   # or just `m` for a full build
```

## License

The patched files originate from AOSP (Apache License 2.0); patches here
follow the same license.
