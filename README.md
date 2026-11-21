# AI STUDIO — Professional Android Video Editor

**AI STUDIO** is a native Kotlin & Jetpack Compose Android non-linear video editor (NLE) featuring a multi-track timeline, real-time OpenGL ES GLSL pixel shaders, a sandboxed `.aifx` plugin package system, hardware-accelerated Media3 MP4 export, and memory optimizations for 4GB RAM Android devices.

## Key Capabilities

- **Multi-Track Timeline Editor**: Primary video (`V1`), overlay (`V2`), audio (`A1`), and keyframe/FX (`FX`) tracks with trim, split, crop, rotate, speed (`0.25x`–`4.0x`), keyframe animation, shape masking, chroma key, transitions, undo/redo, and Room project persistence.
- **GPU Effects Engine & 32-Effect Library**: OpenGL ES GLSL fragment shaders shared between real-time `ExoPlayer` preview and `Transformer` export across 6 categories (**Sharpen**, **Glow**, **Blur**, **Color**, **Distortion**, **Lighting**), with independent parameter controls and a live Before/After split wipe.
- **`.aifx` Sandboxed Plugin System**: ZIP-based plugin package format (`manifest.json`, `shader.frag`, `presets.json`, `signature.sha256`) with SHA-256 integrity verification, GLSL sandbox validation, and strict blocking of untrusted native binaries (`.so`, `.dex`, `.jar`).
- **Hardware Export Pipeline**: Media3 `Transformer` export supporting 720p, 1080p, 1440p, and 4K (gated by device `MediaCodecList` capabilities), 30/60/120 FPS, H.264/HEVC codecs, and configurable bitrate.
- **4GB RAM & Thermal Optimization**: Live `EGL14`, Vulkan, RAM, and `PowerManager` thermal diagnostics, adaptive preview resolution presets, `ComponentCallbacks2` memory trimming, and cache management.

## Repository Structure

```text
├── .github/workflows/android-build.yml  # GitHub Actions CI/CD APK build workflow
├── app/
│   ├── build.gradle.kts                 # App module Gradle configuration & dependencies
│   ├── proguard-rules.pro               # ProGuard/R8 rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/
│       │   │   ├── data/                # Room database, repositories, DataStore preferences
│       │   │   ├── engine/              # GLSL shader library, Media3 GlEffect, Transformer export, MediaCodec calibration MP4 generator
│       │   │   ├── model/               # Navigation routes, timeline, effects, and .aifx plugin models
│       │   │   ├── ui/                  # Compose screens (Home, Editor, Effects, Plugins, Projects, Settings) & theme
│       │   │   └── viewmodel/           # StudioViewModel state & undo/redo management
│       │   └── res/                     # Adaptive launcher icon, hero art, bundled fonts, strings
│       └── test/                        # Unit & Robolectric tests
├── gradle/
│   ├── libs.versions.toml               # Version catalog
│   └── wrapper/                         # Gradle wrapper (9.3.1)
├── build.gradle.kts
├── settings.gradle.kts
└── .gitignore
```

## Building Locally (Android Studio or CLI)

### Prerequisites
- **JDK 21** (Eclipse Temurin recommended)
- **Android SDK** (Platform 36, Build-Tools 36)
- **Gradle 9.3.1** (included via `./gradlew`)

### Debug APK
```bash
./gradlew :app:assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Signed Release APK
Generate an upload keystore (keep it private and never commit it to Git):
```bash
keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```
Then build with signing environment variables:
```bash
export KEYSTORE_PATH="$(pwd)/my-upload-key.jks"
export STORE_PASSWORD="your_store_password"
export KEY_PASSWORD="your_key_password"
./gradlew :app:assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

### Run Unit & Robolectric Tests
```bash
./gradlew :app:testDebugUnitTest
```

## GitHub Actions Automated APK Builds

Pushing to `main` (or manually running **Actions -> AI STUDIO Android Build -> Run workflow**) automatically:
1. Runs unit and Robolectric tests.
2. Builds `app-debug.apk` and uploads it as the **`ai-studio-debug-apk`** artifact.
3. Builds `app-release.apk` and uploads **`ai-studio-release-apk`** when these repository secrets are configured under **Settings -> Secrets and variables -> Actions**:
   - `RELEASE_KEYSTORE_BASE64`: Base64-encoded contents of your `my-upload-key.jks` (`base64 -w 0 my-upload-key.jks`).
   - `STORE_PASSWORD`: Keystore password.
   - `KEY_PASSWORD`: Key password for alias `upload`.
