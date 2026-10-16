# Gallery

A fast, local-only personal gallery for Android 10+ (API 29+). AMOLED-minimal design, Kotlin + Jetpack Compose.

## Features

- **Photos**: uniform grid (2-6 columns) or masonry feed, switchable in Settings. Photos and videos mixed, date headers, fast-scroll bar with month bubble.
- **Multi-select**: long-press or the check button. Share, favorite, delete (system trash on Android 11+).
- **Albums**: folders, plus Favorites, Videos and Trash (Android 11+).
- **Search**: by file name, album or month, with Photos / Videos / Favorites filters.
- **Viewer**: swipe between items, pinch and double-tap zoom, swipe up for details (EXIF, size, path), swipe down to close.
- **Video player** (Media3 / ExoPlayer): brightness (left swipe), volume (right swipe), swipe to seek, double-tap +/-10s, hold for 2x, speed, audio tracks, subtitles, resume position, lock, rotate, fit/fill, picture-in-picture.
- **Editor**: crop (aspect presets), rotate, flip, straighten, brightness / contrast / saturation / warmth, 8 filters. Saves a **copy** to `Pictures/Edited`; originals are never touched.
- **Settings**: layout, columns, accent color (Lime, Blue, Pink, White, Sky, or fully custom), theme (AMOLED / Dark / Light).

## Build the APK

### Option A: GitHub Actions (no setup)

1. Create a new GitHub repository and push this folder to it.
2. Open the repo's **Actions** tab. The **Build APK** workflow runs on every push.
3. When it finishes, download the artifact:
   - `gallery-release-apk`: optimized, much smoother scrolling (use this one), signed with the debug key.
   - `gallery-debug-apk`: slower, for debugging.
4. Copy the APK to your phone and install it (allow "install unknown apps").

### Option B: Android Studio

Open this folder in Android Studio (Ladybug or newer), let Gradle sync, then **Build > Build APK(s)**,
or run `./gradlew assembleRelease` and look in `app/build/outputs/apk/release/`.

## Notes

- Min SDK 29, target SDK 35. Package: `dev.vibe.gallery`.
- Everything stays on the device. No network permission is requested.
- Video: Media3 plays most formats through the phone's hardware decoders. Some rare audio codecs (DTS, certain AC3/E-AC3 variants) depend on the device. An FFmpeg extension can be added later for those.
- On Android 10 there is no system trash, so Delete removes files permanently after the system confirmation.
