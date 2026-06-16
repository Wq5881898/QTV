# qtv.json Format

This project currently reads a bundled JSON file from:

`app/src/main/assets/qtv.json`

## Current Shape

```json
{
  "channels": {
    "version": "local-test-1",
    "items": [
      {
        "id": "test-902070",
        "name": "Test Stream 902070",
        "category": "Local qtv.json",
        "sources": [
          {
            "url": "http://example.com/live.m3u8",
            "priority": 1
          }
        ]
      }
    ]
  }
}
```

## Field Notes

### Root

- `channels.version`
  Logical config version string
- `channels.items`
  Channel list consumed by the app

### Channel

- `id`
  Stable channel identifier
- `name`
  Display name shown in the UI
- `category`
  Group or source label shown in the UI
- `sources`
  Candidate playback sources for the channel

### Source

- `url`
  Playback URL
- `priority`
  Lower-level ordering hint for source preference

## Current Runtime Behavior

- The app currently uses the first available source for playback
- The player currently assumes the source is HLS
- HTTP sources are allowed in the current prototype build

## Suggested Future Expansion

Possible fields that may be added later:

- `logo`
- `epgId`
- `headers`
- `drm`
- `backupSources`
- `userAgent`
- `referer`
- `timeoutMs`
