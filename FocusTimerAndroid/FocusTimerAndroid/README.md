# 🎯 Focus Timer — Native Android App

A real Android APK with genuine app-blocking using:
- **UsageStatsManager** — detects which app is in the foreground every second
- **SYSTEM_ALERT_WINDOW** — launches a fullscreen block overlay over blocked apps
- **Foreground Service** — keeps the timer running even when you leave the app
- **Normal & Strict modes** — Strict locks the timer so you can't stop it early
- **Session history** — records every session with start/end times, duration, mode
- **CSV export** — saves to Downloads folder, open in Excel

---

## 📋 What You Need (One-Time Setup)

1. **Android Studio** — https://developer.android.com/studio (free, ~1GB download)
2. **JDK 17** — Android Studio installs this automatically
3. A **USB cable** OR Wi-Fi to install APK on your phone

---

## 🚀 How to Build the APK

### Step 1 — Open in Android Studio
```
File → Open → select the FocusTimerAndroid folder → OK
```
Wait for Gradle sync to finish (blue progress bar at bottom, ~2–5 min first time).

### Step 2 — Build the APK
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```
Wait ~1 minute. A notification pops up: **"Build successful"**

### Step 3 — Find the APK
Click **"locate"** in the notification, or find it at:
```
FocusTimerAndroid/app/build/outputs/apk/debug/app-debug.apk
```

### Step 4 — Install on your phone

**Option A — USB:**
- Enable Developer Options on phone: Settings → About Phone → tap Build Number 7 times
- Enable USB Debugging: Settings → Developer Options → USB Debugging → ON
- Connect phone via USB, run in terminal:
  ```
  adb install app-debug.apk
  ```

**Option B — Direct file transfer:**
- Copy `app-debug.apk` to your phone (WhatsApp, Google Drive, USB, etc.)
- On phone: tap the file → Install
- If blocked: Settings → Security → Install unknown apps → allow your file manager

---

## 🔒 Permissions the App Requests

When you first open the app, it will ask for **2 permissions**:

### 1. Usage Access
```
Settings → Apps → Special app access → Usage access → Focus Timer → Allow
```
This lets the app detect which app is in the foreground (needed to trigger blocking).

### 2. Display over other apps
```
Settings → Apps → Special app access → Display over other apps → Focus Timer → Allow
```
This lets the app show the red block screen on top of blocked apps.

**Both are required for app blocking to work.**

---

## 📱 How the App Works

### Timer Tab
- Pick **Normal** or **Strict** mode
- Select duration (5 / 15 / 25 / 45 / 60 min, or custom)
- Tap **Start Focus** — the timer starts and a foreground notification appears
- In **Normal mode**: you can Pause or Stop anytime
- In **Strict mode**: the Stop button is locked — you must finish the session

### Block Apps Tab
- Shows every installed app on your phone (real list, not fake)
- Toggle individual apps ON (blocked) or OFF (allowed)
- Social, messaging, entertainment, and games are **blocked by default**
- Productivity apps stay open
- Search by app name
- "Block All", "Unblock All", "Reset Default" buttons
- **Cannot change while timer is running**

### Sessions Tab
- Shows all past sessions with date, time, duration, mode
- Summary: total focus time, completion rate, normal vs strict count
- **Export CSV** saves to Downloads/ folder — open in Excel or Google Sheets

### App Blocking in Action
When timer is running and you open a blocked app:
1. The **red block screen** instantly appears full-screen
2. Shows the app name, time remaining, and "Keep going!" message
3. In **Normal mode**: "Go back to Focus Timer" button appears
4. In **Strict mode**: no way out — back button does nothing, screen stays until timer ends
5. Timer keeps counting in the background the whole time

---

## 📁 Project Structure

```
FocusTimerAndroid/
├── app/
│   ├── build.gradle                          ← App dependencies & config
│   ├── src/main/
│   │   ├── AndroidManifest.xml               ← Permissions & components
│   │   ├── java/com/focustimer/app/
│   │   │   ├── MainActivity.java             ← 3-tab UI (Timer, Apps, Stats)
│   │   │   ├── AppMonitorService.java        ← Background service: polls foreground app
│   │   │   ├── BlockOverlayActivity.java     ← Fullscreen block screen
│   │   │   ├── PrefsManager.java             ← SharedPreferences storage
│   │   │   ├── SessionData.java              ← Session data model
│   │   │   └── AppInfo.java                  ← App info model
│   │   └── res/
│   │       ├── layout/
│   │       │   ├── activity_main.xml         ← Root layout with tab bar
│   │       │   ├── tab_timer.xml             ← Timer screen
│   │       │   ├── tab_apps.xml              ← App blocker screen
│   │       │   ├── tab_stats.xml             ← Sessions & stats screen
│   │       │   ├── activity_block_overlay.xml ← Red block screen
│   │       │   ├── item_app.xml              ← Single row in app list
│   │       │   └── item_session.xml          ← Single row in history
│   │       ├── drawable/                     ← Backgrounds, icons, shapes
│   │       ├── values/                       ← Colors, strings, themes
│   │       └── color/                        ← Color state lists
├── gradle/wrapper/                           ← Gradle build system
├── build.gradle                              ← Root build config
├── settings.gradle
└── gradle.properties
```

---

## ⚠️ Troubleshooting

| Problem | Fix |
|---|---|
| Gradle sync fails | File → Invalidate Caches → Invalidate and Restart |
| "SDK not found" | Android Studio → SDK Manager → install Android 14 (API 34) |
| App installs but blocking doesn't work | Grant both permissions in Settings |
| Timer stops when phone sleeps | Grant battery optimization exception: Settings → Battery → Focus Timer → Unrestricted |
| "Install blocked" on phone | Enable "Install unknown apps" in phone Security settings |

---

## 🔧 Customization

- **Change default blocked apps** → edit `PrefsManager.java` → `getDefaultBlockedPackages()`
- **Change poll interval** (how fast blocking triggers) → `AppMonitorService.java` → `POLL_INTERVAL` (default 1000ms)
- **Add more preset durations** → `MainActivity.java` → `PRESET_MINS` array
- **Change colors** → `res/values/colors.xml`
