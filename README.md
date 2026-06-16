# QTV

QTV is an Android TV prototype focused on validating a remote-first live player workflow from a local `qtv.json` source.

The current prototype has already verified these points:

- Android TV baseline app can launch from `LEANBACK_LAUNCHER`
- Channel list can be loaded from bundled `app/src/main/assets/qtv.json`
- Channels can be switched with D-pad focus and OK selection
- Media3 ExoPlayer can play HLS streams declared in `qtv.json`
- Cleartext HTTP streams are allowed for this prototype build

## Current Scope

This repository is currently a functional proof of concept, not a finished product. It is intended to confirm the end-to-end path of:

1. Define channels in `qtv.json`
2. Load channels into a TV-friendly Compose UI
3. Select a channel on Android TV
4. Start HLS playback through Media3

## Tech Stack

- Android application
- Kotlin
- Jetpack Compose
- Media3 ExoPlayer
- Min SDK 24
- Target SDK 36

## Project Structure

- `app/src/main/java/com/qtv/app/MainActivity.kt`
  TV home screen and channel selection UI
- `app/src/main/java/com/qtv/app/config/LocalQtvConfig.kt`
  Local `qtv.json` parsing and channel mapping
- `app/src/main/java/com/qtv/app/player/QtvPlayerPane.kt`
  Media3 playback surface and status handling
- `app/src/main/assets/qtv.json`
  Bundled local channel configuration
- `app/src/main/res/xml/network_security_config.xml`
  Prototype cleartext HTTP allowance

## Run

Open the project in Android Studio and run the `app` configuration on an Android TV emulator or TV device.

CLI build:

```powershell
.\gradlew.bat :app:assembleDebug
```

CLI Kotlin compile check:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

## Configuration

The app currently reads bundled channels from:

`app/src/main/assets/qtv.json`

Each channel contains:

- `id`
- `name`
- `category`
- `sources`

Each source currently uses:

- `url`
- `priority`

See [docs/qtv-json-format.md](docs/qtv-json-format.md) for the current format.

## Prototype Notes

- This build currently permits cleartext HTTP traffic because the validated stream sources are HTTP.
- The player forces HLS MIME type for the current live stream format.
- The current data source is local-only. NAS and remote sync are not wired in yet.

See [docs/prototype-baseline.md](docs/prototype-baseline.md) for the current validation baseline.

## Development Docs

- [docs/development/README.md](docs/development/README.md)
  Development-oriented documentation for structure, implemented features, and stream playback debugging
