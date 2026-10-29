# KevBox Home on-TV checklist — Xiaomi TV Box S 3rd gen (MiTV-AFMU0), <xiaomi box ip>
Date: 2026-10-29. Build: main e0012d3 (code = a5add94), release APK 382,795 B, debug-key signed.
Box: Android 14, UKG3.250107.001.V816.0.25.7.22.UZFAABX, fresh test box. Stock launcherx installed.
Note: setupwraith owns the saved Wi-Fi (cname=com.google.android.tungsten.setupwraith); nothing here disables it.

## Before you start
- PASS Projectivy / tvQuickActions services: none on this box (enabled list was `null`).
- PASS Installed for the checklist: KevBox TV, KevBox Live, SmartTube, VLC, Speedtest, FX, Downloader, Surfshark.
- PASS Release APK installed as tv.kevbox.home. Opened once: before provisioning the column shows only "More apps" (null case).
- PASS provision.sh -> "provision: done on <xiaomi box ip>:33785", exit 0. HOME role holder = tv.kevbox.home.
- FINDING (minor): opening KevBox Home from the app list (LEANBACK_LAUNCHER) starts a type=standard MainActivity in its own task (t11) beside the home-type one (t12), so two home screens sit in memory. provision.sh itself started only the home-type one (the fix-3 HOME intent works). Fix idea: in MainActivity.onCreate, when this task is not the home task and the app is the HOME role holder, call KevBox.goHome(this) and finish().

## AC-1 identity
- PASS package:tv.kevbox.home; versionName=0.8.1.0, versionCode=80100; launcher entry resolves.
## AC-6 provision file
- PASS good file: applied, `applied kevbox-home.json: hidden`, no kevbox-home.json left.
- PASS bad file: kevbox-home.rejected.json, config.json byte-identical, `rejected kevbox-home.json: Value not of type java.lang.String cannot be converted to JSONObject`.
- PASS applied on resume (pushed while VLC open, applied on return to Home).
## AC-8 script
- PASS done/exit 0 (above); accessibility service listed, accessibility_enabled=1; applied.json present.
- PASS more than one device -> red line, exit 1. Unknown ANDROID_SERIAL -> red line, exit 1.
- NOT RUN: the "adb disconnect, no device" case (would drop the other connected TVs); provision-test.sh covers it.
## AC-9 example packages
- PASS all nine packages present (installed for this run).
## AC-10 left column
- PASS order KevBox Live, KevBox TV, SmartTube, divider, More apps; unchanged after opening SmartTube.
- PASS `hidden: [smarttube]` removes it from column and drawer; `apps: []` leaves only More apps; re-provision restores.
## AC-11 status lines (partial)
- PASS SmartTube "Opened today at 07:25"; never-opened apps show name only. KevBox TV episode line: NOT RUN (KevBox TV not signed in on this box).
## AC-12 More apps
- PASS Down past the last app lands on More apps under a divider; OK opens the drawer.
## AC-14 / 18 / 20 top right
- PASS default Wi-Fi, Bluetooth, Speed test, Settings, clock, weather (<city> 25°). `["bluetooth","wifi"]` -> Bluetooth, Wi-Fi, Settings (added). Speed test hidden when speedtestApp is missing, back when restored.
- PASS focused text "Wi-Fi · strong", "Bluetooth · 1 connected". Up from the first app lands on the first button.
## AC-19 Settings button
- PASS cog + "Settings" always shown; hold OK opens Customize.
- FINDING (important): OK opens whatever page Android's Settings app was left on. After any App info visit (hold-OK menu) and Home, the Settings button reopens that app's details page (AppManagementActivity), not MainSettings; only after `am force-stop com.android.tv.settings` does it open MainSettings. Back from App info also lands on MainSettings instead of KevBox Home. ButtonRow.openSettings (Wi-Fi, Bluetooth, Settings) and AppMenu/AppRestart App info all start into the reused settings task. Fix: add FLAG_ACTIVITY_CLEAR_TASK (with NEW_TASK) to those intents.
## AC-21 drawer
- PASS 5 columns; first six Surfshark, Speedtest, Play Store, FX, Downloader, VLC; rest alphabetical; KevBox Home/Live/TV and SmartTube absent; Back focuses More apps.
## AC-23 / 24 hold-OK menu
- PASS column app: App info, Move, Restart app (no Uninstall). VLC and iQIYI (user apps): + Uninstall. Gallery (SYSTEM, never updated): no Uninstall.
- PASS App info opens Android's details page (drawer app and KevBox TV).
## AC-33 Customize
- PASS rows exactly as the spec lists (Permissions 4, Appearance, Wallpaper 4, Home > App channels, Clock and weather 3, System 2). None of the removed rows. 7x OK on About opens nothing. Notification access shows "Turn on" (provision.sh does not grant it; not in AC-8's list).
## AC-25 Move
- PASS column Move (KevBox Live down one) and drawer Move (VLC right one) saved to config.json.
- PASS after `adb reboot`: box boots straight into KevBox Home (one instance, service still on), both orders kept.
  (Wireless debugging changed port on reboot; reconnected on <xiaomi box ip>:5555, which this box also serves.)
- PASS provision.sh with the example file restores the file's column and drawer order.
## AC-30 Home
- PASS Home (injected) from KevBox TV, VLC and Play Store returns to KevBox Home; starting launcherx's HOME directly lands on KevBox Home; one MainActivity throughout. On this box KevBox Home is also the HOME role holder, so this is not a test of the takeover path. "Never even a flash": needs Kevin's eyes.
## AC-37 Uninstall
- PASS Uninstall on VLC opens Android's "Do you want to uninstall this app?"; Cancel keeps VLC and the drawer.
- PASS confirm: VLC gone within ~0.5 s, drawer stays open with focus on the next tile, "Uninstall finished" toast. VLC reinstalled from ~/ADB_AppControl/install.
## AC-13 backdrop
- PASS Dim levels order the wallpaper brightness Light 55.2 > Medium 54.6 > Strong 54.0 (Medium identical twice). The step is small on this dark wallpaper: Kevin to eyeball. Channel-art backdrop NOT RUN: no app on this box publishes a channel (KevBox TV not signed in; SmartTube shows "has no channel on this TV").
## AC-26 Restart app
- FAIL (important): on Xiaomi's Google TV Settings (MiBoxGTVSettingsTwoPanel, package com.android.tv.settings) Restart app opens the details page and stops: `restart org.smarttube.stable: step FIND timed out` (also for com.xiaomi.mitv.mediaexplorer). Force stop was visible and enabled (screenshot 39), and also failed in the stopped/absent case. Ruled out: the resource name (code already uses device_apps_app_management_force_stop, which exists in this APK), package visibility (QUERY_ALL_PACKAGES declared, no "cannot read" log), service capabilities (capabilities=9: retrieve content + filter keys). Next step: a debug build logging what rootInActiveWindow returns (package, null, node count) during the poll.
- NOT RUN reliably: service-off toast (blind presses went astray: landed on the Netflix stub). Redo with Kevin.
## Real remote session (Kevin pressing, recorded with getevent / logcat / top-activity watch)
Remote: "Xiaomi RC" (Keyboard + Consumer Control). Real codes: Netflix KEY_VIDEOPHONE, Prime Video KEY_PLAY (scancode 207),
YouTube KEY_PRESENTATION, Apps KEY_CHAT, Home KEY_HOMEPAGE. KevBox Home logged them as 193/416, 194/207, 190/425, 284/216, 3/172.
- PASS AC-27/28 Netflix -> KevBox TV, Prime -> KevBox Live, YouTube -> SmartTube, Home -> KevBox Home (each target on screen ~0.15 s after the key).
- PASS AC-22 Apps on Home -> drawer, Back -> Home. Apps inside VLC -> Home then drawer over it (08:48:13.84 -> 14.22), Back -> Home, not VLC. Kevin confirmed focus on More apps.
- PASS AC-27 hold Netflix 3.3 s -> KevBox TV opened once, ~0.01 s after release.
- PASS AC-30 Kevin saw no flash of the stock launcher on any Home press.
- NOT RUN AC-29 Netflix from inside VLC (step skipped); speed shown from Home instead.
- FAIL AC-27 hold Volume up inside KevBox TV: volume does not keep ramping (getevent: single DOWN, held 1.3-3 s, single UP; Android's own repeat missing). A/B with the service off follows.
- FINDING (important), A/B confirmed: with KevBox Home's accessibility service OFF (enabled list null, WRITE_SECURE_SETTINGS revoked), holding Volume up in KevBox TV ramps normally; with it ON it does not. So KevBox Home's key filter (flagRequestFilterKeyEvents) breaks Android's held-key repeat for keys it passes through. Members hold volume all the time. Fix idea: KeyFilter re-creates the repeat for volume keys it passes (AudioManager.adjustStreamVolume on a timer while held, ~400 ms initial / ~50 ms repeat), or narrows filtering; check how tvQuickActions avoids it.
- PASS AC-26 service off: with the service off and the grant revoked, Restart app on KevBox TV leaves the details page open with the toast "Press Force stop, then reopen the app" (Kevin confirmed).
## AC-38 English whatever the language (box set to fr-FR)
- PASS Home "More apps", status "Opened today at 07:47", buttons "Wi-Fi · strong" / "Bluetooth · 1 connected" / "Settings", chart letters f s s m t w t, "Last 7 days", bottom line "memory used", "storage, 26 GB free", decimal points (26.7, 92.2). Hold-OK menu and Customize in English.
- NOTE drawer labels are the apps' own (Xiaomi's "Lecteur Multimédia", "Paramètres"): correct, not ours. Weekday names ("Opened Wednesday at …") not reachable on a fresh box.
- NOT RUN AC-26 in French: Restart app fails in English already.

## Not run on this box (and why)
- AC-11 KevBox TV episode status line, AC-13 channel-art backdrop: no app publishes a channel here (KevBox TV not signed in; SmartTube publishes none on this box).
- AC-15 Ethernet / no-network icons, AC-16 VPN indicator (Surfshark not signed in), AC-17 Bluetooth off states: need Kevin to change the box's network/Bluetooth or sign in to Surfshark.
- AC-14 Update chip: no newer version.json on tv.kevbox.dev.
- AC-8 "no device" (adb disconnect): would drop the other connected TVs; covered by provision-test.sh.

## Summary
Passed: AC-1, 6, 8 (2 of 3 error cases live), 9, 10, 12, 14, 18, 19 (hold), 20 (texts), 21, 22, 23, 24, 25, 27 (keys, hold-Netflix), 28, 30, 33, 37, 38; AC-26 service-off toast.
Failed / findings:
1. AC-26 Restart app (important): on Xiaomi's Google TV Settings the Force stop step times out ("step FIND timed out"), so Restart app only opens the details page. Root cause not found from outside; needs a debug build with logging.
2. AC-27 held volume (important): KevBox Home's key filter stops held Volume up from ramping; A/B confirmed (ramps with the service off).
3. AC-19 Settings / App info (important): Settings, Wi-Fi and Bluetooth buttons reopen whatever page Android's Settings app was left on (e.g. the last App info page); Back from App info lands on Settings' main screen. Start those intents in a fresh task (FLAG_ACTIVITY_CLEAR_TASK).
4. Minor: opening KevBox Home from the app list starts a second, standard-type copy beside the Home one.
5. Minor: uiautomator dump never works on Home (the animated weather icon keeps the screen from going idle), so screen readers/automation tools see nothing there.
