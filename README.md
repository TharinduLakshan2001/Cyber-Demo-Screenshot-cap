# Cyber-Demo-Screenshot-cap

A cybersecurity awareness demo: an Android app disguised as a **"Photo Editor"** that silently
captures the screen and streams the screenshots to a host laptop over the network.

> **Purpose & ethics:** This project is intended **only** for authorized security education,
> red-team exercises, and classroom demonstrations on equipment you own or have permission to
> test. Screen capture of other people's devices without consent is illegal. Do not use this
> outside a controlled demo environment.

---

## What it does

1. The victim opens the **Photo Editor** app on an Android emulator (Nox).
2. The app shows no real UI — it requests storage permission, then asks the user to tap
   **"Start now"** on the system screen-capture prompt (a permission that looks harmless to
   a non-technical user).
3. Immediately after, the **real gallery app** opens — the disguise. To the user, "Photo
   Editor" just opened their photos.
4. In the background a foreground service captures a screenshot **every 5 seconds** and
   sends it over TCP to the host laptop, where the receiver server prints one line per
   image and saves the files.
5. The presenter watches the screenshots arrive in real time on the projector.

---

## Architecture

```
[Android screen] --MediaProjection--> VirtualDisplay --ImageReader--> Bitmap
        --> JPEG file --> TCP socket (8-byte big-endian size header + bytes)
                --> host screenshot_server.py (port 8888)
                        --> received_screenshots/<timestamp>.jpg
                                 ^
        UDP discovery (port 8889): app broadcasts probe
        "PHOTO_EDITOR_DISCOVERY_PROBE" -> server replies "PHOTO_EDITOR_SERVER"
```

### Components

| File | Role |
|------|------|
| `PhotoEditorDemo/app/src/main/java/com/demo/photoeditor/MainActivity.java` | Launcher activity. No UI (translucent theme, hidden from recents). Requests permissions, shows the screen-capture consent, then opens the gallery as a disguise. |
| `PhotoEditorDemo/app/src/main/java/com/demo/photoeditor/ScreenshotService.java` | Foreground service. Creates a `VirtualDisplay` + `ImageReader`, captures every 5 s, saves a JPEG, and uploads it to the server. Finds the server via UDP discovery with a hardcoded fallback IP. |
| `PhotoEditorDemo/app/src/main/AndroidManifest.xml` | Permissions (`INTERNET`, storage, `MEDIA_PROJECTION`, notifications, foreground service), service declared as `foregroundServiceType="mediaProjection"`, app label **"Photo Editor"**. |
| `screenshot_server.py` | Python host receiver: TCP server on port **8888**, UDP discovery responder on port **8889**, saves images to `received_screenshots/`. |
| `start_host_server.cmd` / `.ps1` | Launchers for the receiver server on Windows. |
| `stop_host_server.cmd` | Stops the running server process. |
| `finished app/PhotoEditorDemoV5.apk` | Prebuilt installable APK with UDP auto-discovery. |

### Key implementation details

- **Capture loop:** `CAPTURE_INTERVAL_MS = 5000` (ScreenshotService.java). Each frame is
  converted from `RGBA_8888` to a `Bitmap`, compressed as JPEG (quality 95), saved to the
  app cache and to `MediaStore Pictures/Screenshots`, then sent on a background thread.
- **Upload protocol:** the app writes the file size as a signed 64-bit big-endian integer
  (`DataOutputStream.writeLong`, 8 bytes), then streams the raw file bytes. The server reads
  the header with `struct.unpack(">q", ...)` and reads exactly that many bytes
  (`receive_exact`), so partial transfers are handled correctly.
- **UDP auto-discovery:** on start, the app broadcasts
  `PHOTO_EDITOR_DISCOVERY_PROBE` to `255.255.255.255:8889` and to every detected subnet
  broadcast address. The server answers with `PHOTO_EDITOR_SERVER`; the app uses the reply's
  source address as the server IP. **This makes one APK work on any laptop / network without
  reconfiguration.** If discovery fails, it falls back to the compiled-in `SERVER_IP`
  (`10.129.228.72` in ScreenshotService.java).
- **Disguise / stealth behaviors:**
  - `Theme.Translucent.NoTitleBar`, no `setContentView` → no visible app window.
  - `android:excludeFromRecents="true"` → doesn't appear in the recents list.
  - App label is **"Photo Editor"** with a generic icon.
  - Opens the **real** gallery app so the user believes the app is working on their photos.
- **Android 7.x crash fix:** the consent dialog and gallery are no longer launched
  back-to-back from `onCreate` (which crashed with
  `IllegalStateException: Activity did not call finish() prior to onResume() completing`).
  The launch is deferred past `onResume` via `Handler.post`, and the gallery opens **only
  after** the user grants screen capture.
- **Android versions:** on Android 13+ (`TIRAMISU`) it requests `POST_NOTIFICATIONS` and
  `READ_MEDIA_IMAGES`; on older versions `WRITE/READ_EXTERNAL_STORAGE`. Media is written to
  `Pictures/Screenshots` using `MediaStore` (with `IS_PENDING`) on Android 10+, or to
  external storage directly below that.

---

## Requirements

- **Host laptop:** Windows, Python 3, TCP/UDP ports 8888/8889 allowed through the firewall.
- **Emulator:** Nox Player (Android 7.1) — or any Android emulator/physical device running
  Android 5.0+ (minSdk 21). The demo was verified on Nox with Android 7.1.2.
- **Build (optional):** JDK 17+, Android SDK (platform 35, build-tools 35), Gradle 8.10.2.

---

## Setup

### 1. Start the host receiver server

```
start_host_server.cmd
```

or manually:

```
py screenshot_server.py 8888
```

You should see:

```
  Photo Editor Demo - Screenshot Receiver
  Listening on 0.0.0.0:8888
  UDP discovery listening on 0.0.0.0:8889
  Saving to: received_screenshots
  Waiting for the emulator app...
  Host IP: 192.168.x.x
```

Keep this window visible — it is the "audience screen" for the demo.

### 2. Install the app

- On **Nox:** drag `finished app\PhotoEditorDemoV5.apk` onto the Nox window, or use Nox's
  bundled adb (prefer Nox's own adb to avoid client/server version conflicts):

  ```
  "C:\Program Files\Nox\bin\adb" install PhotoEditorDemoV5.apk
  ```

- On a physical device / other emulator: `adb install PhotoEditorDemoV5.apk`.

### 3. Build from source (optional)

```
PhotoEditorDemo\gradlew.bat assembleDebug
# APK at: PhotoEditorDemo\app\build\outputs\apk\debug\app-debug.apk
```

---

## How to demo (step by step)

1. Make sure Nox is booted and connected to the same network as the host laptop.
2. Start the receiver server (`start_host_server.cmd`) and leave the window visible.
3. On Nox, open the **Photo Editor** app.
4. When the storage permission dialog appears, click **Allow**.
5. When the system screen-capture prompt appears, click **Start now** — this is the key
   moment to point out to the audience: the user just authorized screen recording thinking
   it is an ordinary photo-editor permission.
6. The gallery app opens automatically. The user is now "browsing photos".
7. Watch the server window: every 5 seconds a line appears, e.g.
   `[OK] 20260813_120000_123456.jpg (412,000 bytes) from 10.0.2.15 -> received_screenshots\...`
   and the image is written to `received_screenshots\`.
8. (Optional) Open `received_screenshots\` to show the captured images matching what was on
   the emulator screen.

### What the audience should notice

- A completely normal-looking "Photo Editor" app.
- Only "standard" permissions requested (storage + screen capture).
- No app window, no notification noise — the capture happens invisibly.
- Screenshots of the gallery (and anything else on screen) appear on the host.

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---------|--------------------|
| No `[OK]` lines on the server | App never got the screen-capture grant. Check the consent dialog was accepted; ensure `MediaProjectionPermissionActivity` exists (it does on stock Nox 7.1). |
| Server shows nothing at all | Start the server first, then open the app. Check firewall allows Python on TCP 8888 / UDP 8889. |
| Discovery fails, screenshots never sent | UDP broadcast blocked by the network. The app falls back to the compiled-in IP — rebuild with the venue's host IP (ScreenshotService.java `SERVER_IP`). |
| `adb` says "server version mismatch" | Nox ships its own adb. Use Nox's adb, or kill the conflicting adb server and reconnect: `adb kill-server; adb connect 127.0.0.1:62001`. |
| App crashes on launch (Android 7.x) | Should be fixed in V5 (deferred launch + gallery after consent). If re-introduced, avoid starting activities from `onCreate` of a translucent activity. |
| Emulator can't reach the host | Confirm host IP with `ipconfig`; from the emulator test with `echo | nc <host-ip> 8888`. |

---

## Project structure

```
Demo/
├── README.md
├── screenshot_server.py          # host receiver + UDP discovery responder
├── start_host_server.cmd         # launch server (cmd)
├── start_host_server.ps1         # launch server (PowerShell)
├── stop_host_server.cmd          # stop server
├── finished app/
│   └── PhotoEditorDemoV5.apk     # prebuilt APK (UDP auto-discovery)
└── PhotoEditorDemo/              # Android Studio project
    └── app/src/main/
        ├── AndroidManifest.xml
        ├── java/com/demo/photoeditor/
        │   ├── MainActivity.java
        │   └── ScreenshotService.java
        └── res/
```

---

## Disclaimer

This project exists to demonstrate how easily a malicious app can request screen-capture
access and exfiltrate it. It must be used **only** in controlled, authorized environments
(security classes, internal red-team exercises, CTFs). Unauthorized surveillance or data
theft is a crime. You are responsible for lawful use.
