# Prototype Baseline

This document records the current QTV proof-of-concept baseline.

## What Has Been Validated

- Android TV style launcher entry is configured
- Compose TV-style channel list UI is working
- Local `qtv.json` can be parsed and rendered into selectable channels
- Channel switching updates the active player source
- Media3 playback works for validated HLS sources
- HTTP stream playback is enabled for the prototype build

## Current UX

The main screen is split into two areas:

- Left side: channel list
- Right side: selected channel details and active playback surface

The current interaction model is:

1. Move focus with D-pad
2. Press OK on a channel row
3. Start playback for the selected stream

## Playback Assumptions

- Stream sources are currently treated as HLS
- The player sets MIME type to `application/x-mpegURL`
- HTTP traffic is explicitly allowed in the manifest and network security config

## Configuration Source

Bundled data lives at:

`app/src/main/assets/qtv.json`

This is the current prototype source of truth. No remote fetch, NAS mount, or sync pipeline is enabled yet.

## Known Boundaries

- No remote configuration loading yet
- No EPG integration yet
- No authentication flow yet
- No retry or failover strategy across multiple sources yet
- No production hardening for cleartext traffic
- No dedicated TV playback controls overlay yet

## Recommended Next Steps

- Replace bundled-only config with local file import or remote config loading
- Add source failover and retry strategy
- Add basic playback telemetry and richer error reporting
- Decide how `qtv.json` should be sourced in production
- Tighten network policy after the real stream topology is known
