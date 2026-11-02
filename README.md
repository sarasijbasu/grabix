# Grabix — ad-free video & music downloader for Android

Grabix is an original, open Android app that offers the full feature set people expect from SnapTube-style downloaders, with **no ads, no tracking and no dependency on any companion app** (such as Lark Player). Everything — download engine, converter and media player — is bundled inside the APK.

## Features

**Download core**
- Paste a link, use the Paste button, or *Share → Grabix* from YouTube, Instagram, Facebook, X, TikTok and any other app
- Supports 1,000+ sites through the bundled yt-dlp engine
- Video from 144p up to 4K / 60 fps, with estimated file sizes per quality
- Music: MP3 (320 / 192 / 128 kbps), M4A (AAC) or original stream, with cover art and tags embedded
- Whole playlists and channels in one tap
- Background download queue with pause, resume, retry and cancel; survives app restarts
- 1–5 parallel downloads, Wi-Fi-only mode, optional "Turbo mode" (aria2c multi-connection)
- Embedded subtitles (English, Hindi, Bengali or all languages)
- In-app YouTube search with recent-search history

**In-app browser**
- Quick tiles for 12 popular video sites
- Floating **Download** button appears on video pages
- Sign in once inside the browser; cookies are passed to the engine so private, age-restricted or login-only videos (e.g. Instagram) download
- Basic ad/tracker blocking, desktop-site toggle, sign-out button

**Built-in player (no external player needed)**
- Plays downloads *and* all videos/music already on the phone
- Background playback with lock-screen / notification controls and headset buttons
- Picture-in-picture (manual button or automatic on Home)
- Double-tap to seek ±10 s, swipe for brightness (left) and volume (right)
- Playback speed 0.25×–3×, sleep timer, repeat / shuffle, fit / zoom / stretch, rotation lock
- Subtitle track selection, resume where you left off
- Playlists; also appears as "Grabix Player" in Android's *Open with* menu

**Extras**
- Private folder protected by PIN and optional fingerprint/face unlock (files hidden from gallery)
- Save to phone storage (`Download/Grabix`) or SD card
- Light / dark / system theme
- Daily automatic engine update so sites keep working; manual "Update now"

## What's new in 1.1

- **Faster downloads and post-processing** – the link info read for the quality picker is reused (no second page fetch), M4A audio is copied instead of re-encoded, 8 parallel fragments, and the Downloads tab now shows each step after 100 % ("Merging audio + video", "Converting audio", "Adding cover art", "Writing tags").
- **Cover art and metadata switches** (Me → Downloads). Turning metadata off strips title/artist/date/description tags; the source link is always kept.
- **microG Google sign-in** (Me → Google account, off by default). Uses a GmsCore account (ReVanced / Vanced microG) to sign the in-app browser into Google; falls back to Google's sign-in page.
- **Icon-only bottom bar** with **History** between Downloads and Library.
- **WhatsApp Status saver** (Home → Status, or Me → Tools). Grant the status folder once; saves go to `Download/Grabix/WhatsApp Status`.
- **VLC-style video fit**: Best fit, Fit width, Fit height, Fill (crop), Stretch, Original size, plus 16:9, 4:3, 1:1, 16:10, 5:4, 3:2, 21:9, 2.21:1, 2.35:1, 2.39:1 and 9:16.
- **Auto-play next** toggle in the player (and in Me → Player).
- **Playback speed panel** that fits in landscape full screen (chips + fine slider, 0.25×–4×).
- **Open source page** button in the player for downloaded files.
- **Theme**: System / Light / Dark plus Material You colours (Android 12+); the player follows the theme too.
- **Closing from recents now stops everything** (playback, browser audio, and downloads unless "Keep downloading after closing" is on).
- **Playlists download into their own folder**; you name it before the download starts.
- **Backup & restore** of settings and playlists as a plain `.txt` file (Me → Backup & restore).

## Getting the APK

### Option A — GitHub Actions (no Android Studio needed)
1. Create a new repository on GitHub (private is fine).
2. Upload the contents of this folder (or `git push` it).
3. Open the **Actions** tab → *Build APK* runs automatically (about 8–12 minutes).
4. Open the finished run → **Artifacts** → download `grabix-apks`.
5. Install `app-universal-release.apk` on your phone (allow "Install unknown apps" when prompted).

Pushing a tag such as `v1.0.0` also publishes the APKs as a GitHub Release.

### Option B — Your own computer
1. Install [Android Studio](https://developer.android.com/studio) (includes Java 17 and the Android SDK).
2. Either open this folder in Android Studio and choose *Build → Build APK(s)*, or run:
   - Windows: `build-apk.bat`
   - macOS / Linux: `./build-apk.sh`
3. APKs are written to `app/build/outputs/apk/release/`.

### Which APK should I install?
| File | Phones |
|---|---|
| `app-arm64-v8a-release.apk` | Almost all phones from 2017 onwards (smallest download) |
| `app-armeabi-v7a-release.apk` | Older 32-bit phones |
| `app-universal-release.apk` | Any device — use this if unsure |

Requires Android 8.0 (API 26) or newer.

## Signing your own release (optional)
Without a keystore, release APKs are signed with the debug key — they install and run normally, but updates must be signed with the same key. To use your own key:

```bash
keytool -genkey -v -keystore grabix.jks -keyalg RSA -keysize 2048 -validity 10000 -alias grabix
```

- **Locally:** create `keystore.properties` in the project root:
  ```
  storeFile=/full/path/grabix.jks
  storePassword=...
  keyAlias=grabix
  keyPassword=...
  ```
- **GitHub Actions:** add repository secrets `KEYSTORE_BASE64` (output of `base64 -w0 grabix.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Project layout
```
app/src/main/java/com/grabix/app/
├── engine/      yt-dlp wrapper: link analysis, search, format selection, download command
├── download/    foreground download service, queue, notifications
├── player/      Media3 playback service, full-screen player, shared player connection
├── vault/       private folder (PIN + biometric)
├── data/        Room database, settings (DataStore), on-device media scanner
├── ui/          Jetpack Compose screens: Home, Browser, Downloads, Library, Settings
└── util/        storage paths, sharing, cookies export, formatting
```

**Stack:** Kotlin 2.1 · Jetpack Compose (Material 3) · Media3 ExoPlayer 1.5 · Room · DataStore · Coil · [youtubedl-android](https://github.com/JunkFood02/youtubedl-android) (yt-dlp + FFmpeg + aria2c).

## Troubleshooting
- **"Couldn't get this video"** — open *Me → Update now* to refresh the engine; sites change often.
- **Instagram / Facebook / private videos fail** — sign in inside the Grabix browser first, then download from there.
- **Files not in the gallery** — they are in `Download/Grabix/Video` and `Download/Grabix/Music`; items moved to the private folder are intentionally hidden.
- **Build error about a youtubedl-android version** — check the latest release at the link above and update the `ytdlp` version in `app/build.gradle.kts`.

## Responsible use
Only download content you own, that is in the public domain, or that you have permission to save, and respect each website's terms of service and local copyright law.
