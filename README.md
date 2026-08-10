# Android TV for Raspberry Pi 4 (AOSP 16, `aosp_rpi4_tv`)

Patch set for **AOSP's `aosp_rpi4_tv` product** -- the stock Android TV
(Leanback) build that AOSP already ships for the Raspberry Pi 4
(`device/brcm/rpi4`), on top of Google's reference TV device tree
(`device/google/atv`). This repo does **not** carry a full device tree copy;
it tracks the local fixes/customizations applied on top of a stock
`android-16.0` checkout, as patches against the exact AOSP paths they touch,
plus one wholly new app (`packages/apps/VtvTvInput`) kept as a plain file
copy since there's no upstream version of it to diff against.

This is a sibling project to
[Android-Auto-for-Pi4-Android-16](../Android-Auto-for-Pi4-Android-16) (the
`aosp_rpi4_car` build on the same board) -- same hardware, different AOSP
product/form-factor.

## Status

**Fixed:**
- Wi-Fi icon on the home screen showed `<unknown ssid>` even while
  genuinely connected with a good signal. See below.
- The stock launcher (`TvSampleLeanbackLauncher`) never showed newly
  installed launchable apps (Live Channels, this repo's own VTV app) on the
  home screen at all. See below.

**Added:**
- Live TV: the free-to-air VTV1-9 national channels, watchable two ways --
  as regular channels inside AOSP's stock Live Channels app, and via a
  small standalone channel-picker app. See below.

## Feature: VTV live TV channels

### What it does
Plays the free-to-air VTV1-9 HLS (m3u8) live streams. Two ways to watch:

1. **Inside Live Channels** (`packages/apps/TV`, built as
   `LiveTvNonPassthrough`, not part of this repo -- it's stock AOSP, just
   not enabled by default on this product): a `TvInputService`
   (`VtvTvInput`) registers VTV1-9 as channels; Live Channels' own "add
   channel source" flow makes them browsable, then they behave like any
   other channel (EPG row, channel-surf, etc).
2. **Standalone**: the `VtvTvInput` app itself has a launcher icon. Opening
   it shows a 3x3 grid of channel tiles; picking one opens a fullscreen
   player. `CHANNEL_UP`/`DOWN` or D-pad left/right switch channels without
   leaving the player; back returns to the grid.

Playback uses plain `android.media.MediaPlayer` (the framework's NuPlayer
already speaks HLS) -- no ExoPlayer/media3 dependency pulled into the
build. Each channel lists two mirror URLs (a primary and a fallback); on a
`MediaPlayer` error it walks to the next one automatically.

Stream URLs come from the public, community-maintained
[iptv-org/iptv](https://github.com/iptv-org/iptv) playlist (`streams/vn.m3u`,
CC0), which aggregates freely available, non-DRM live streams of these
terrestrial free-to-air channels. CDN mirror endpoints for IPTV drift over
time; if a channel stops loading, that's the file to check
(`packages/apps/VtvTvInput/src/com/rpi4tv/vtvinput/VtvChannels.java`).

### Real bugs hit building this, and their fixes

**1. `TvProvider` `SecurityException: Not allowed to access
Channels.COLUMN_BROWSABLE`.** `TvInputService`s are only allowed to insert
their own channel rows; only a caller holding the privileged
`ACCESS_ALL_EPG_DATA` permission (i.e. Live Channels itself) may set
`browsable`. `VtvSetupActivity` used to set it on insert and crashed. Fixed
by not setting it at all -- the channels land `browsable=0`, and Live
Channels' own "select channels" UI is what's supposed to flip it, which is
also how any real `TvInputService` is meant to behave.

**2. Newly added apps never appeared on the Home screen at all**, Live
Channels included -- not specific to this app, a pre-existing gap in the
stock `TvSampleLeanbackLauncher`. Root cause: Android's package-visibility
filtering (API 30+). This AOSP version has **no automatic exemption for the
default Home app** (checked: no HOME-role carve-out in
`frameworks/base/services/core/java/com/android/server/pm/AppsFilter*`);
`TvSampleLeanbackLauncher`'s manifest had no `<queries>` block, so
`PackageManager.queryIntentActivities(MAIN + LEANBACK_LAUNCHER)` in its own
`LaunchItemsManager.updateAppList()` silently returned nothing for any app
the launcher hadn't already "interacted with". Confirmed via
`adb shell dumpsys package com.example.sampleleanbacklauncher` (see its
`Queries:` section) both before and after the fix. Fixed by adding a
`<queries>` block declaring the exact intents `LaunchItemsManager` queries
(also folded into this repo's `AndroidManifest.xml.patch`, alongside the
older Wi-Fi SSID fix below).

**3. `GridView` + `ArrayAdapter` never fired clicks on `DPAD_CENTER`/`ENTER`**
for the channel-picker grid, even with the item view's own
`OnClickListener` *and* `OnKeyListener` both wired up -- confirmed via
logcat that neither was ever invoked for the key event, while touch taps on
the same tiles worked fine and the D-pad focus ring rendered correctly.
Root cause not fully pinned down (suspected `AbsListView`'s own
`DPAD_CENTER` handling for its internal selection model, which only calls
`performItemClick()` -- a no-op without `setOnItemClickListener`, which was
intentionally not used here in favor of per-tile listeners). Fixed by
dropping `GridView` entirely for a plain `GridLayout` with the 9 tiles
added as direct children in code; D-pad OK then follows the standard
View-focus-to-`performClick()` path with no special handling needed.
Lesson for next time: avoid `GridView`/`AbsListView` for custom D-pad item
UIs on this tree -- a plain `ViewGroup` with real child `View`s is the
reliable pattern.

### Result (verified on real hardware, 2026-08-10)
- `dumpsys tv_input` shows the input registered; `VtvSetupActivity` inserts
  all 9 channels with no crash (checked directly against
  `/data/user/0/com.android.providers.tv/databases/tv.db` over adb root).
- Live Channels lists a "VTV" channel source with all 9 channels by name;
  after marking them browsable, VTV1/2/3/5/9 were each tuned and confirmed
  playing real video+audio, channel-surfing between them promptly.
- The standalone grid: D-pad navigates correctly, `DPAD_CENTER` opens the
  player and starts real playback, `DPAD_RIGHT` mid-episode steps to the
  next channel correctly, `BACK` returns to the grid.
- The primary CDN mirror worked on the first try for every channel tested
  from a Vietnam IP; no `Referer`/`User-Agent` gating was observed.

### Known constraint
`system.img` for this product lands at **exactly** the device's
`SYSTEM_PARTITION_SIZE` (3221225472 bytes, from `rpi4-wrimg.sh`) after
adding `LiveTvNonPassthrough` + `VtvTvInput`. There is currently zero
headroom left in the `system`/`product` partition -- adding anything else
to `PRODUCT_PACKAGES` for this product will need something else removed
first, or the image won't fit.

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
they touch -- there's no full device tree copy here. The one exception is
`packages/apps/VtvTvInput`, a wholly new app with nothing upstream to diff
against, so it's kept as a plain file copy, at the exact path it installs
to in the AOSP tree (unlike `device-patches/`, which is organized by patch
target rather than mirroring real paths -- see the table below for where
each of those actually applies).

### `device-patches/` -- against `device/brcm/rpi4/`

| In this repo | Applies to / installs at |
|---|---|
| `device-patches/aosp_rpi4_tv.mk.patch` | `git apply` inside `device/brcm/rpi4/`, patches `aosp_rpi4_tv.mk` |
| `device-patches/permissions/Android.bp` | `device/brcm/rpi4/permissions/Android.bp` (new file) |
| `device-patches/permissions/default-permissions-rpi4-tv.xml` | `device/brcm/rpi4/permissions/default-permissions-rpi4-tv.xml` (new file) |

### `device-patches/TvSampleLeanbackLauncher/` -- against `device/google/atv/TvSampleLeanbackLauncher/`

| In this repo | Applies to |
|---|---|
| `device-patches/TvSampleLeanbackLauncher/AndroidManifest.xml.patch` | `git apply` inside `device/google/atv/TvSampleLeanbackLauncher/`, patches `src/main/AndroidManifest.xml` (Wi-Fi SSID fix + the launcher `<queries>` fix, both folded into one patch) |

### `packages/apps/VtvTvInput/` -- new app; the path in this repo *is* the path it installs to

Plain file copy (`cp -r`, not a patch) of the whole `VtvTvInput` Soong
module: `Android.bp`, `AndroidManifest.xml`, `res/`, `src/`.

## Building

```
cd <AOSP_ROOT>/device/brcm/rpi4
git apply <THIS_REPO>/device-patches/aosp_rpi4_tv.mk.patch
cp -r <THIS_REPO>/device-patches/permissions .

cd <AOSP_ROOT>/device/google/atv/TvSampleLeanbackLauncher
git apply <THIS_REPO>/device-patches/TvSampleLeanbackLauncher/AndroidManifest.xml.patch

cp -r <THIS_REPO>/packages/apps/VtvTvInput <AOSP_ROOT>/packages/apps/

cd <AOSP_ROOT>
source build/envsetup.sh
lunch aosp_rpi4_tv bp4a userdebug
m systemimage vendorimage   # or just `m` for a full build
```

Product packages `LiveTvNonPassthrough` and `VtvTvInput` are already added
to `PRODUCT_PACKAGES` by the `aosp_rpi4_tv.mk.patch` above -- no separate
`lunch`/menuconfig step needed for them.

## License

The patched files originate from AOSP (Apache License 2.0); patches here
follow the same license. `packages/apps/VtvTvInput` is original code
written for this repo, also under Apache License 2.0 (matches the
`Android-Apache-2.0` license declared in its `Android.bp`).
