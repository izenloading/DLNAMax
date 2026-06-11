# Android TV DLNA Receiver Implementation Plan

> Plan date: 2026-06-11
> Based on: `docs/ARCHITECTURE_ANALYSIS.md`
> Scope: MVP development plan only. This document does not define code changes.

## MVP Phase Overview

The target application is a Kotlin-first Android TV DLNA Digital Media Renderer (DMR). The legacy `Android-DLNA-Server/` project is treated as reference material only, not as code to port directly.

Development is split into four phases:

| Phase | Scope | Goal |
|---|---|---|
| P0 | SSDP, DeviceDescription, AVTransport | Device can be discovered and can accept core transport SOAP commands |
| P1 | Media3 | Transport commands control real playback through ExoPlayer |
| P2 | RenderingControl | Volume and mute are exposed through UPnP and reflected in playback state |
| P3 | Android TV UI | TV home screen displays renderer status and playback state |

## Shared Target Structure

```text
app/
├── ui/
├── player/
├── dlna/
│   ├── ssdp/
│   ├── description/
│   ├── soap/
│   ├── avtransport/
│   ├── renderingcontrol/
│   ├── connectionmanager/
│   └── model/
├── repository/
└── service/
```

Shared state should be represented as immutable Kotlin data classes and exposed through `StateFlow`.

## P0: SSDP, DeviceDescription, AVTransport

### Goal

Build the minimum UPnP DMR surface so LAN controllers can discover the Android TV receiver, fetch its device/service descriptors, and call AVTransport actions.

P0 intentionally does not require real Media3 playback. `SetAVTransportURI`, `Play`, `Pause`, `Stop`, `Seek`, `GetPositionInfo`, and `GetTransportInfo` should update/read renderer state through an abstraction that P1 will connect to Media3.

### Involved Files

Planned new target files:

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/.../service/RendererService.kt`
- `app/src/main/java/.../repository/RendererStateRepository.kt`
- `app/src/main/java/.../dlna/model/RendererState.kt`
- `app/src/main/java/.../dlna/model/RendererStatus.kt`
- `app/src/main/java/.../dlna/model/DlnaMetadata.kt`
- `app/src/main/java/.../dlna/model/TransportInfo.kt`
- `app/src/main/java/.../dlna/model/PositionInfo.kt`
- `app/src/main/java/.../dlna/ssdp/SsdpServer.kt`
- `app/src/main/java/.../dlna/ssdp/SsdpMessage.kt`
- `app/src/main/java/.../dlna/ssdp/SsdpResponseFactory.kt`
- `app/src/main/java/.../dlna/description/DeviceDescriptionHandler.kt`
- `app/src/main/java/.../dlna/description/DeviceDescriptionXml.kt`
- `app/src/main/java/.../dlna/description/ServiceDescriptionXml.kt`
- `app/src/main/java/.../dlna/soap/SoapHttpServer.kt`
- `app/src/main/java/.../dlna/soap/SoapRequestParser.kt`
- `app/src/main/java/.../dlna/soap/SoapResponseWriter.kt`
- `app/src/main/java/.../dlna/soap/SoapFault.kt`
- `app/src/main/java/.../dlna/avtransport/AVTransportService.kt`
- `app/src/main/java/.../dlna/avtransport/AVTransportAction.kt`
- `app/src/main/java/.../dlna/avtransport/SeekTargetParser.kt`
- `app/src/main/java/.../dlna/connectionmanager/ConnectionManagerService.kt`
- `app/src/test/java/.../dlna/ssdp/SsdpResponseFactoryTest.kt`
- `app/src/test/java/.../dlna/description/DeviceDescriptionXmlTest.kt`
- `app/src/test/java/.../dlna/soap/SoapRequestParserTest.kt`
- `app/src/test/java/.../dlna/avtransport/AVTransportServiceTest.kt`
- `app/src/test/java/.../dlna/avtransport/SeekTargetParserTest.kt`

Legacy reference files:

- `Android-DLNA-Server/app/src/main/java/com/zxt/dlna/dmr/ZxtMediaRenderer.java`
- `Android-DLNA-Server/app/src/main/java/com/zxt/dlna/dmr/AVTransportService.java`
- `Android-DLNA-Server/app/src/main/java/com/zxt/dlna/dmr/ZxtConnectionManagerService.java`
- Selected `Android-DLNA-Server/app/src/main/java/org/fourthline/cling/**` behavior for SSDP, descriptors, and SOAP semantics only.

### New Classes

- `RendererService`
- `RendererStateRepository`
- `RendererState`
- `RendererStatus`
- `DlnaMetadata`
- `TransportInfo`
- `PositionInfo`
- `SsdpServer`
- `SsdpMessage`
- `SsdpResponseFactory`
- `DeviceDescriptionHandler`
- `DeviceDescriptionXml`
- `ServiceDescriptionXml`
- `SoapHttpServer`
- `SoapRequestParser`
- `SoapResponseWriter`
- `SoapFault`
- `AVTransportService`
- `AVTransportAction`
- `SeekTargetParser`
- `ConnectionManagerService`

### Deleted Classes

P0 should not delete legacy files until the new Android project structure is established. Mark these classes/modules as excluded from the target MVP:

- `com.zxt.dlna.dms.*`
- `com.zxt.dlna.dmc.*`
- `com.zxt.dlna.dmp.*`
- `com.zxt.dlna.activity.*`
- `com.zxt.dlna.dmr.RenderPlayerService`
- `com.zxt.dlna.dmr.GPlayer` equivalent legacy path under `dmp`
- `com.zxt.dlna.util.Action`

### Risk Analysis

| Risk | Impact | Mitigation |
|---|---|---|
| SSDP multicast behavior differs across Android TV firmware | Controllers cannot discover the device | Acquire multicast lock in `RendererService`, test on real TV hardware and emulator separately |
| Device description URLs use the wrong host/IP | Controllers fetch descriptors from an unreachable address | Resolve active LAN IPv4 address at service startup and regenerate `LOCATION` consistently |
| SOAP parser is too strict | Real controllers fail because of header or XML variations | Tolerate SOAPAction quoting/casing and missing optional metadata |
| `SetAVTransportURI` accidentally starts playback | Violates required lifecycle and breaks P1 handoff | P0 service only caches URI and metadata; playback API remains a stub |
| Incomplete `ConnectionManager` protocol info | Controllers refuse to send supported formats | Include mp4, mkv, hls, mp3, flac, jpg, and png sink protocol entries |

### Acceptance Criteria

- App starts a renderer service independent of the UI screen.
- Device advertises as `urn:schemas-upnp-org:device:MediaRenderer:1`.
- Device description returns:
  - `friendlyName`: `Android TV Receiver`
  - `manufacturer`: `Open Source`
  - `modelName`: `AndroidTVDLNA`
- SSDP responds to `M-SEARCH` with `HTTP/1.1 200 OK`.
- SSDP response includes `LOCATION`, `CACHE-CONTROL`, `USN`, `SERVER`, and `ST`.
- Device description lists `AVTransport`, `RenderingControl`, and `ConnectionManager` services, even if RenderingControl behavior is completed in P2.
- AVTransport supports:
  - `SetAVTransportURI`
  - `Play`
  - `Pause`
  - `Stop`
  - `Seek`
  - `GetPositionInfo`
  - `GetTransportInfo`
- `SetAVTransportURI` caches URI and metadata without playback.
- `Play`, `Pause`, `Stop`, and `Seek` update state through the repository/stub controller.
- Unit tests cover SSDP response generation, descriptor XML, SOAP parsing, AVTransport command handling, and seek time parsing.

## P1: Media3

### Goal

Connect AVTransport commands to AndroidX Media3 ExoPlayer. After P1, a controller can send media to the renderer and control playback.

### Involved Files

Planned new or updated files:

- `app/build.gradle.kts` or `app/build.gradle`
- `app/src/main/java/.../player/DlnaPlayerController.kt`
- `app/src/main/java/.../player/Media3DlnaPlayerController.kt`
- `app/src/main/java/.../player/MediaItemFactory.kt`
- `app/src/main/java/.../player/PlayerStateMapper.kt`
- `app/src/main/java/.../player/ImagePlaybackController.kt`
- `app/src/main/java/.../service/RendererService.kt`
- `app/src/main/java/.../repository/RendererStateRepository.kt`
- `app/src/main/java/.../dlna/avtransport/AVTransportService.kt`
- `app/src/test/java/.../player/MediaItemFactoryTest.kt`
- `app/src/test/java/.../player/PlayerStateMapperTest.kt`
- `app/src/test/java/.../dlna/avtransport/AVTransportMedia3ContractTest.kt`

Legacy reference files:

- `Android-DLNA-Server/app/src/main/java/com/zxt/dlna/dmr/ZxtMediaPlayer.java`
- `Android-DLNA-Server/app/src/main/java/com/zxt/dlna/dmr/ZxtMediaPlayers.java`
- `Android-DLNA-Server/app/src/main/java/com/zxt/dlna/dmp/GPlayer.java`
- `Android-DLNA-Server/app/src/main/java/com/zxt/dlna/dmr/RenderPlayerService.java`

### New Classes

- `DlnaPlayerController`
- `Media3DlnaPlayerController`
- `MediaItemFactory`
- `PlayerStateMapper`
- `ImagePlaybackController`

### Deleted Classes

Remove from the target app if they were copied or still referenced:

- `RenderPlayerService`
- `GPlayer`
- `ZxtMediaPlayer`
- `ZxtMediaPlayers`
- Broadcast command constants such as `Action`

### Risk Analysis

| Risk | Impact | Mitigation |
|---|---|---|
| Media3 lifecycle is tied to an Activity | Playback stops when UI closes | Own ExoPlayer from `RendererService` or an injected app-scoped component |
| `Play` before `SetAVTransportURI` | Null URI crashes or invalid state | Return a UPnP fault or keep `NO_MEDIA_PRESENT` state |
| Repeated `Play` recreates the player unnecessarily | Buffering loops or playback restarts | Only prepare a new `MediaItem` when URI changes |
| HLS/MKV/FLAC handling differs by device codec support | Supported controllers may send media the TV cannot decode | Use Media3 format support, surface errors in `RendererState`, and keep protocol info conservative |
| Position polling drifts from player state | Controllers show wrong progress | Map Media3 listener events and periodic position updates into `RendererStateRepository` |

### Acceptance Criteria

- `SetAVTransportURI` creates no `MediaItem` playback side effect.
- `Play` creates a Media3 `MediaItem`, calls `prepare()`, then starts playback.
- `Pause` calls Media3 pause and state becomes `PAUSED`.
- `Stop` calls Media3 stop and state becomes `STOPPED`.
- `Seek` parses `REL_TIME` and calls Media3 `seekTo()`.
- `GetPositionInfo` returns current URI, position, and duration from player state.
- `GetTransportInfo` maps Media3 state to UPnP transport state:
  - `STOPPED`
  - `PLAYING`
  - `PAUSED_PLAYBACK`
  - `TRANSITIONING` or equivalent buffering state
- Playback works for at least one mp4 URL and one HLS URL in local testing.
- Unit tests verify MediaItem creation, state mapping, and AVTransport-to-player command order.

## P2: RenderingControl

### Goal

Expose UPnP volume and mute controls and keep them synchronized with the player/device audio state.

### Involved Files

Planned new or updated files:

- `app/src/main/java/.../dlna/renderingcontrol/RenderingControlService.kt`
- `app/src/main/java/.../dlna/renderingcontrol/RenderingControlAction.kt`
- `app/src/main/java/.../dlna/renderingcontrol/VolumeValidator.kt`
- `app/src/main/java/.../player/DlnaPlayerController.kt`
- `app/src/main/java/.../player/Media3DlnaPlayerController.kt`
- `app/src/main/java/.../repository/RendererStateRepository.kt`
- `app/src/main/java/.../dlna/model/RendererState.kt`
- `app/src/main/java/.../dlna/description/ServiceDescriptionXml.kt`
- `app/src/test/java/.../dlna/renderingcontrol/RenderingControlServiceTest.kt`
- `app/src/test/java/.../dlna/renderingcontrol/VolumeValidatorTest.kt`

Legacy reference files:

- `Android-DLNA-Server/app/src/main/java/com/zxt/dlna/dmr/AudioRenderingControl.java`
- `Android-DLNA-Server/app/src/main/java/com/zxt/dlna/dmr/ZxtMediaPlayer.java`

### New Classes

- `RenderingControlService`
- `RenderingControlAction`
- `VolumeValidator`

### Deleted Classes

Remove from the target app if they were copied or still referenced:

- `AudioRenderingControl`
- Legacy broadcast volume/mute path from `ZxtMediaPlayer`
- Any UI-only volume classes from the old phone player

### Risk Analysis

| Risk | Impact | Mitigation |
|---|---|---|
| UPnP channel validation is missing | Controllers send unsupported channels and get inconsistent behavior | Support `Master` only and return a clear SOAP fault for unsupported channels |
| Volume range differs between UPnP and Media3 | Volume jumps or clips unexpectedly | Normalize UPnP 0-100 to Media3/device volume consistently |
| Mute state conflicts with previous volume | Unmute restores wrong volume | Store `muted` independently from `volume` in `RendererState` |
| Device-level volume APIs behave differently on TV hardware | Remote and controller volume drift | Prefer player volume for MVP, document device-volume integration as a later enhancement if needed |

### Acceptance Criteria

- RenderingControl supports:
  - `GetVolume`
  - `SetVolume`
  - `GetMute`
  - `SetMute`
- Only `Master` channel is accepted.
- `SetVolume` validates the value is within 0-100.
- `GetVolume` returns the latest state value.
- `SetMute` updates mute state without losing stored volume.
- Media3 playback volume reflects UPnP volume/mute changes.
- Renderer state includes `volume` and `muted`.
- Unit tests cover valid/invalid channel handling, volume bounds, mute behavior, and SOAP responses.

## P3: Android TV UI

### Goal

Build the Android TV receiver dashboard. The UI observes renderer state but does not own SSDP, SOAP, or player lifecycle.

### Involved Files

Planned new or updated files:

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/.../ui/MainActivity.kt`
- `app/src/main/java/.../ui/RendererDashboardScreen.kt`
- `app/src/main/java/.../ui/RendererDashboardViewModel.kt`
- `app/src/main/java/.../ui/RendererUiState.kt`
- `app/src/main/java/.../ui/StatusFormatter.kt`
- `app/src/main/java/.../repository/RendererStateRepository.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/androidTest/java/.../ui/RendererDashboardTest.kt`
- `app/src/test/java/.../ui/StatusFormatterTest.kt`

Legacy reference files:

- `Android-DLNA-Server/app/src/main/res/layout/dmr.xml`
- `Android-DLNA-Server/app/src/main/java/com/zxt/dlna/activity/DevicesActivity.java`
- `Android-DLNA-Server/app/src/main/java/com/zxt/dlna/activity/IndexActivity.java`

These files are references only. The old phone/tablet tab UI should not be ported.

### New Classes

- `MainActivity`
- `RendererDashboardScreen`
- `RendererDashboardViewModel`
- `RendererUiState`
- `StatusFormatter`

### Deleted Classes

Remove from the target app if they were copied or still referenced:

- `IndexActivity`
- `DevicesActivity`
- `ContentActivity`
- `ControlActivity`
- `BrowserActivity`
- `StartActivity`
- `AboutActivity`
- `SettingActivity`
- Old phone/tablet layout resources under `res/layout/*`

### Risk Analysis

| Risk | Impact | Mitigation |
|---|---|---|
| UI owns renderer startup | Renderer stops when Activity is destroyed | Keep renderer startup in `RendererService`; UI only observes state |
| TV layout is not readable at distance | Poor Android TV usability | Use large readable text, strong focus states, and simple status layout |
| Long media URIs overflow the screen | Dashboard becomes unreadable | Truncate URI with middle/end ellipsis and expose full value only if a detail view is added later |
| UI refresh frequency is too high | Wasted CPU during playback | Throttle position display updates to a reasonable interval |
| Missing error state | Playback failures are invisible | Surface `ERROR` status and short error message from `RendererState` |

### Acceptance Criteria

- Home screen displays:
  - Device Name
  - IP Address
  - Renderer Status
  - Current URI
  - Current Position
  - Volume
- Supported status values are visible:
  - `STOPPED`
  - `PLAYING`
  - `PAUSED`
  - `BUFFERING`
  - `ERROR`
- UI observes `RendererStateRepository` through ViewModel state.
- Closing or backgrounding the UI does not stop SSDP, SOAP, or playback service.
- Android TV remote focus behavior is predictable.
- UI tests or screenshot/manual checklist verify the dashboard at TV resolution.

## Cross-Phase Verification

Before declaring MVP complete:

- BubbleUPnP can discover the renderer.
- VLC or Kodi can discover the renderer.
- Jellyfin can cast a supported media URL to the renderer.
- Windows Media Player `Play To` can discover the renderer if available on the test LAN.
- mp4 playback succeeds.
- HLS playback succeeds.
- mkv playback succeeds on hardware with codec support.
- `Play`, `Pause`, `Resume`, `Stop`, and `Seek` work from a controller.
- `SetVolume` and `SetMute` work from a controller.
- The app can remain active for a 2-hour playback session without crashing.
- Network disconnect/reconnect does not permanently break renderer discovery.

## MVP Definition Of Done

MVP is complete when:

```text
BubbleUPnP
  -> discovers Android TV Receiver
  -> sends video URL
  -> Android TV starts playback after Play
  -> Pause works
  -> Resume works
  -> Stop works
```

The implementation must preserve the required DMR lifecycle:

```text
SetAVTransportURI -> cache URI only
Play -> prepare and play
Pause -> pause
Stop -> stop
Seek -> seekTo
```
