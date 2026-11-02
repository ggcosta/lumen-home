# MEOBox 4K Speed & Debloat Guide

Tested on the MEOBox 4K (`MEOBox_4K_DV8985`). These commands disable operator telemetry, auto-run daemons, the stock launcher, the voice assistant and pre-installed apps. Packages are **disabled, not removed**: they stay on the device and can be re-enabled at any time.

> [!WARNING]
> Use at your own risk. Disabling system packages can break features on your device, and other Android TV boxes ship different packages. Only run the commands you understand.

Make sure Lumen Home is installed and set as the home app first (see the main [README](../README.md)), otherwise disabling the stock launcher leaves you without a Home screen.

## Commands

```sh
# 1. SDMC / MEO Operator Telemetry, TR-069 & Auto-Run Daemons
adb shell pm disable-user --user 0 com.sdmc.tms
adb shell pm disable-user --user 0 com.sdmc.tms.pt.acs
adb shell pm disable-user --user 0 com.sdmc.tms.daemon
adb shell pm disable-user --user 0 com.sdmc.autorunapp
adb shell pm disable-user --user 0 com.sdmc.leakbackcustomer
adb shell pm disable-user --user 0 com.sdmc.diagnostic
adb shell pm disable-user --user 0 com.sdmc.cleardataservice
adb shell pm disable-user --user 0 com.sdmc.facTest
adb shell pm disable-user --user 0 tv.broadpeak.nanocdnwrapper

# 2. Stock Android TV Launcher & Recommendations
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell pm disable-user --user 0 com.google.android.tvrecommendations
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith

# 3. Google Assistant & Voice Search
adb shell pm disable-user --user 0 com.google.android.katniss
adb shell pm disable-user --user 0 com.google.android.tts

# 4. Pre-installed Streaming Bloatware
adb shell pm disable-user --user 0 com.netflix.ninja
adb shell pm disable-user --user 0 com.netflix.tokenmanager
adb shell pm disable-user --user 0 com.skyshowtime.skyshowtime.google
adb shell pm disable-user --user 0 com.amazon.amazonvideo.livingroom
adb shell pm disable-user --user 0 com.google.android.youtube.tvmusic
adb shell pm disable-user --user 0 com.google.android.play.games
adb shell pm disable-user --user 0 com.spocky.projengmenu

# 5. Unused System Services & Telemetry
adb shell pm disable-user --user 0 com.android.providers.calendar
adb shell pm disable-user --user 0 com.google.android.syncadapters.calendar
adb shell pm disable-user --user 0 com.android.providers.contacts
adb shell pm disable-user --user 0 com.android.printspooler
adb shell pm disable-user --user 0 com.android.providers.userdictionary
adb shell pm disable-user --user 0 com.google.android.feedback

# 6. Snappier UI Animation Scales
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5
```

## Do not disable

- Play Store (`com.android.vending`) and Play Services (`com.google.android.gms`)
- Settings (`com.android.tv.settings`)
- Bluetooth and remote services (`com.sdmc.bluetooth*`, `com.sdmc.remotet4happlication`, `com.ref.remote`, `com.sdmc.GlobalKey`, `com.google.android.tv.remote.service`)
- Dolby / DroidLogic hardware services

## Restore the stock launcher or packages

```sh
adb shell pm enable com.google.android.tvlauncher
adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
```

Re-enable any other package the same way: `adb shell pm enable PACKAGE`.
