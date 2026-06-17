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
            "priority": 1,
            "type": "hls",
            "label": "Primary"
          },
          {
            "url": "http://backup.example.com/live.m3u8",
            "priority": 2,
            "type": "hls",
            "label": "Backup A"
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
- `type`
  Stream type hint. Current player path expects `hls`
- `label`
  Human-readable source label reserved for diagnostics or future UI

## Current Runtime Behavior

- The app sorts sources by ascending `priority`
- The app starts from the highest-priority source
- The app currently plays only that highest-priority source
- If playback fails, the player retries the same source instead of auto-failing over
- The first retry happens immediately
- Later retries wait 10 seconds between attempts
- The current retry limit is 3 attempts per channel selection
- The player currently uses `type` to resolve the playback MIME type
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
