# Android-DLNA-Server Architecture Analysis

> Analysis date: 2026-06-11
> Target source: `Android-DLNA-Server/`
> Goal: develop an Android TV DLNA Digital Media Renderer (DMR)

## Executive Summary

`Android-DLNA-Server` is a legacy Java Android app, originally imported from Eclipse/ADT, that combines three product roles in one application:

- DMS: local media server
- DMC/DMP: control point and local media browser/player
- DMR: media renderer exposed through UPnP/Cling

Only the DMR concepts and parts of the UPnP service implementation are useful for the new Android TV receiver. The current code should not be ported directly because it is activity-driven, Java-only, tied to Android `MediaPlayer`, and violates the target DMR lifecycle: `SetAVTransportURI` starts playback UI immediately instead of only caching the URI.

The most valuable source material is:

- `com.zxt.dlna.dmr.ZxtMediaRenderer`
- `com.zxt.dlna.dmr.AVTransportService`
- `com.zxt.dlna.dmr.AudioRenderingControl`
- `com.zxt.dlna.dmr.ZxtConnectionManagerService`
- selected Cling UPnP protocol/model/support classes under `org.fourthline.cling`

The new implementation should be Kotlin-first and structured around:

```text
app
├── ui
├── player
├── dlna
│   ├── ssdp
│   ├── avtransport
│   ├── renderingcontrol
│   ├── connectionmanager
│   └── soap
├── repository
└── service
```

## Current Architecture

### Build And Platform

The project is an old Android application:

- `compileSdkVersion 22`, `targetSdkVersion 21`, `minSdkVersion 15`
- old `compile` dependency syntax
- Android Support libraries instead of AndroidX
- many bundled JARs for Cling, Jetty, Apache HTTP, SLF4J, Seamless, and image loading

Evidence:

- `Android-DLNA-Server/app/build.gradle:5` declares SDK 22.
- `Android-DLNA-Server/app/build.gradle:23` loads old support libraries and local JARs.
- `Android-DLNA-Server/import-summary.txt` shows this was imported from an Eclipse Android project.

This build system is not suitable for the new app. Treat it as reference source, not as a base project.

### Product Role Mixing

The legacy application mixes media server, controller, browser, and renderer behavior:

- `com.zxt.dlna.dms`: media server and content directory
- `com.zxt.dlna.dmc`: control point action callbacks
- `com.zxt.dlna.dmp`: local playback and image display
- `com.zxt.dlna.dmr`: renderer service classes
- `com.zxt.dlna.activity`: phone/tablet UI tabs, device lists, settings, content browser

The DMR is registered from `DevicesActivity` after binding to Cling's Android service:

- `DevicesActivity` binds `AndroidUpnpServiceImpl`.
- It optionally registers a DMS.
- It optionally creates `ZxtMediaRenderer` and adds it to the UPnP registry.

Evidence:

- `Android-DLNA-Server/app/src/main/java/com/zxt/dlna/activity/DevicesActivity.java:111` stores `AndroidUpnpService`.
- `Android-DLNA-Server/app/src/main/java/com/zxt/dlna/activity/DevicesActivity.java:116` conditionally creates a DMS.
- `Android-DLNA-Server/app/src/main/java/com/zxt/dlna/activity/DevicesActivity.java:144` conditionally creates and registers the DMR.

For Android TV DMR, this should move to a foreground/background renderer service or application-level service, not a device-list activity.

## DMR-Relevant Modules

### `ZxtMediaRenderer`

Role: constructs a UPnP `LocalDevice` of type `MediaRenderer:1`, binds three local services, and manages LastChange event flushing.

Useful behavior:

- Creates a `MediaRenderer` device.
- Registers `AVTransport`, `RenderingControl`, and `ConnectionManager`.
- Uses Cling `AnnotationLocalServiceBinder`.
- Uses `LastChangeAwareServiceManager` for evented state.

Evidence:

- `ZxtMediaRenderer.java:82` creates `ConnectionManager` service.
- `ZxtMediaRenderer.java:93` creates `AVTransport` service.
- `ZxtMediaRenderer.java:107` creates `RenderingControl` service.
- `ZxtMediaRenderer.java:124` creates `LocalDevice`.
- `ZxtMediaRenderer.java:128` sets device type `MediaRenderer`.
- `ZxtMediaRenderer.java:140` attaches the three mandatory services.
- `ZxtMediaRenderer.java:157` starts an endless LastChange thread.

Assessment: needs refactor.

Reason: the service composition is valuable, but it is Java, depends on legacy settings/utilities, uses a raw endless thread, and exposes legacy device metadata. The new target metadata should be:

- `friendlyName`: `Android TV Receiver`
- `manufacturer`: `Open Source`
- `modelName`: `AndroidTVDLNA`

### `AVTransportService`

Role: implements AVTransport action handlers and delegates to `ZxtMediaPlayer`.

Useful behavior:

- Validates instance ID.
- Implements mandatory actions: `SetAVTransportURI`, `Play`, `Pause`, `Stop`, `Seek`, `GetPositionInfo`, `GetTransportInfo`.
- Maintains Cling-compatible `MediaInfo`, `TransportInfo`, and `PositionInfo`.

Evidence:

- `AVTransportService.java:58` implements `setAVTransportURI`.
- `AVTransportService.java:103` returns `MediaInfo`.
- `AVTransportService.java:108` returns `TransportInfo`.
- `AVTransportService.java:113` returns `PositionInfo`.
- `AVTransportService.java:130` maps `Stop`.
- `AVTransportService.java:135` maps `Play`.
- `AVTransportService.java:140` maps `Pause`.
- `AVTransportService.java:151` maps `Seek`.

Assessment: needs major refactor.

Problems:

- `SetAVTransportURI` only accepts `http:` and `file:`. Target needs HLS and normal `https` media URLs.
- Metadata parsing is string-based and unsafe. It assumes `<dc:title>` exists and can throw on missing metadata.
- It classifies media by searching object class substrings instead of parsing DIDL-Lite.
- It calls `ZxtMediaPlayer.setURI`, which starts the player service immediately.

Target behavior:

- `SetAVTransportURI`: validate and cache URI/metadata only.
- `Play`: build `MediaItem`, call `prepare()`, then `play()`.
- `Pause`: call Media3 `pause()`.
- `Stop`: call Media3 `stop()` and clear or retain URI according to renderer state policy.
- `Seek`: parse `REL_TIME` robustly and call Media3 `seekTo()`.

### `ZxtMediaPlayer` And `ZxtMediaPlayers`

Role: stores renderer state, emits LastChange updates, and bridges UPnP commands to local playback.

Useful behavior:

- Keeps transport, position, and media info state.
- Computes allowed transport actions by state.
- Emits LastChange values for URI, track URI, transport state, volume, mute, duration, and position.

Evidence:

- `ZxtMediaPlayer.java:123` exposes current transport info.
- `ZxtMediaPlayer.java:127` exposes current position info.
- `ZxtMediaPlayer.java:131` exposes current media info.
- `ZxtMediaPlayer.java:142` emits URI LastChange values.
- `ZxtMediaPlayer.java:198` computes current transport actions.
- `ZxtMediaPlayers.java` creates per-instance players.

Assessment: needs major refactor.

Problems:

- `setURI` starts `RenderPlayerService` immediately, violating the required lifecycle.
- Playback control uses app-wide broadcasts instead of a typed player service API.
- Volume control directly broadcasts to a UI player.
- It uses Java synchronization and mutable shared state instead of Kotlin `StateFlow`.
- It couples protocol state to Android Activity lifecycle.

Evidence:

- `ZxtMediaPlayer.java:136` implements `setURI`.
- `ZxtMediaPlayer.java:150` creates an intent for `RenderPlayerService`.
- `ZxtMediaPlayer.java:155` starts that service.
- `ZxtMediaPlayer.java:163` sends volume via broadcast.

Target replacement:

- `RendererSession` or `RendererStateStore` as immutable state with `StateFlow`.
- `DlnaPlayerController` interface backed by Media3 ExoPlayer.
- UPnP services should call the player controller directly, not broadcasts.

### `RenderPlayerService`, `GPlayer`, And `ImageDisplay`

Role: old local playback UI.

Assessment: delete for MVP, then replace with Android TV UI and Media3 player.

Problems:

- `RenderPlayerService` launches activities for audio/video/image.
- `GPlayer` is Android `MediaPlayer` based, not Media3.
- `GPlayer.onPrepared()` starts playback automatically.
- Commands arrive through dynamic broadcasts.
- `ImageDisplay` is mixed DMP/DMC/DMR UI, not a renderer service.

Evidence:

- `RenderPlayerService.java:30` launches `GPlayer` for audio.
- `RenderPlayerService.java:39` launches `GPlayer` for video.
- `RenderPlayerService.java:45` launches `ImageDisplay` for image.
- `GPlayer.java:453` calls `mp.start()` in `onPrepared`.
- `GPlayer.java:667` registers broadcast actions.
- `GPlayer.java:683` handles broadcast `PLAY`.
- `GPlayer.java:686` handles broadcast `PAUSE`.
- `GPlayer.java:689` handles broadcast `SEEK`.
- `GPlayer.java:702` handles broadcast `SET_VOLUME`.
- `GPlayer.java:706` handles broadcast `STOP`.

### `AudioRenderingControl`

Role: implements RenderingControl mute and volume by delegating to `ZxtMediaPlayer`.

Useful behavior:

- Supports `Master` channel only.
- Implements `GetVolume`, `SetVolume`, `GetMute`, `SetMute`.

Evidence:

- `AudioRenderingControl.java:41` validates channel.
- `AudioRenderingControl.java:47` implements `GetMute`.
- `AudioRenderingControl.java:53` implements `SetMute`.
- `AudioRenderingControl.java:60` implements `GetVolume`.
- `AudioRenderingControl.java:68` implements `SetVolume`.

Assessment: needs refactor.

Reason: the UPnP action surface is useful, but the backend should map to Media3/device audio through a Kotlin player controller. Keep `Master` channel semantics and LastChange behavior.

### `ZxtConnectionManagerService`

Role: declares sink protocol MIME types for the renderer.

Useful behavior:

- Populates `sinkProtocolInfo`.
- Includes target-adjacent media types: JPEG, PNG, MP4, Matroska, MP3-ish audio.

Evidence:

- `ZxtConnectionManagerService.java:19` starts image sink MIME types.
- `ZxtConnectionManagerService.java:27` starts video sink MIME types.
- `ZxtConnectionManagerService.java:40` starts audio sink MIME types.

Assessment: directly reusable as a protocol reference, but rewrite in Kotlin.

Required additions/changes:

- Add HLS: `application/vnd.apple.mpegurl`, `application/x-mpegURL`, and common `audio/mpegurl` variants if needed by controllers.
- Add FLAC: `audio/flac`, `audio/x-flac`.
- Consider `video/x-matroska`, `video/webm`, `audio/mp4`, `audio/aac`, `audio/mpeg`.
- Return protocol info in a predictable list aligned with Media3-supported formats.

### Cling UPnP Stack Under `org.fourthline.cling`

Role: embedded UPnP protocol stack.

Relevant capabilities:

- SSDP multicast and M-SEARCH response.
- Device and service descriptor generation.
- SOAP action parsing and dispatch.
- GENA LastChange events.
- AVTransport, RenderingControl, ConnectionManager support models and base services.

Evidence:

- There are 533 files under `org/fourthline/cling`.
- `UpnpHeader` maps required headers including `USN`, `SERVER`, `LOCATION`, `CACHE-CONTROL`, and `SOAPACTION`.
- `UpnpRequest` includes `M-SEARCH`.
- `ReceivingSearch`, `SendingNotificationAlive`, and `OutgoingSearchResponse*` exist.
- `LocalDevice` creates device and service descriptor resources.
- `SOAPActionProcessorImpl` exists for SOAP processing.

Assessment: reuse conceptually, but do not blindly vendor the full source tree.

Options:

1. Prefer a maintained dependency or a small modern UPnP layer if compatible with Android TV and target SDK.
2. If vendoring is required, trim to only DMR/server-side modules and remove DMC/DMS/control-point/mock/unused transports.
3. If writing custom networking, implement the required subset explicitly: SSDP, device description HTTP, service descriptions, SOAP dispatch, and LastChange only.

## Reuse Matrix

| Module | Decision | Reason |
|---|---|---|
| `ZxtMediaRenderer` | Refactor | Correct service/device composition, but old Java, wrong metadata, raw thread, Activity-era dependencies |
| `AVTransportService` | Refactor | Correct action list, but lifecycle and metadata parsing are wrong |
| `ZxtMediaPlayer` | Refactor | Useful state model ideas, but playback bridge must become Media3 controller |
| `ZxtMediaPlayers` | Refactor | Single-instance map can become Kotlin session store |
| `AudioRenderingControl` | Refactor | Action surface useful; backend must change |
| `ZxtConnectionManagerService` | Reuse as reference | MIME/protocol list is useful but incomplete for target |
| `RenderPlayerService` | Delete | Launches legacy Activities instead of Media3 service |
| `GPlayer` | Delete | Uses Android `MediaPlayer`, auto-plays on prepare, phone UI |
| `ImageDisplay` | Delete for MVP | DMP/DMC/image viewer behavior, not receiver core |
| `com.zxt.dlna.dms` | Delete | Media Server is out of scope |
| `com.zxt.dlna.dmc` | Delete | Control Point is out of scope |
| `com.zxt.dlna.dmp` | Delete except ideas | Local browser/player is not target DMR |
| `activity/*` | Delete | Tabbed phone UI is unrelated to Android TV receiver home/status UI |
| `BaseApplication` | Delete/refactor | Global mutable state and ImageLoader setup are unrelated |
| `Action` broadcast constants | Delete | Replace with typed Kotlin APIs and StateFlow |
| `UpnpUtil.uniqueSystemIdentifier` | Refactor | Useful UDN idea; remove global IP dependency and MD5-era implementation |
| `Utils.getRealTime` | Refactor | Useful time parsing idea; implementation is narrow and unsafe |
| `org.fourthline.cling` | Conditional reuse | Protocol stack is valuable but old, large, and includes unused client/server pieces |
| `app/libs/*.jar` | Delete | Old bundled dependencies should not drive the new build |
| `res/layout/*` old UI | Delete | Phone/tablet UI, not Android TV status screen |
| launcher icon asset | Optional reuse | Can be temporary only |

## Directly Reusable Concepts

These are safe to carry forward as design input:

- DMR advertises as `urn:schemas-upnp-org:device:MediaRenderer:1`.
- DMR exposes `AVTransport`, `RenderingControl`, and `ConnectionManager`.
- LastChange should be emitted for transport and rendering state.
- Instance ID `0` / single renderer instance is enough for MVP.
- `ConnectionManager` sink protocol list should be explicit.
- `GetPositionInfo` and `GetTransportInfo` should read from a shared renderer state model.

## Must Be Refactored

### DMR Startup

Current startup is tied to `DevicesActivity`. New startup should be independent of a screen:

- App start or Android TV boot flow starts renderer service.
- Renderer service acquires multicast lock if needed.
- Renderer service starts SSDP and HTTP/SOAP endpoints.
- UI observes renderer state but does not own the network stack.

### Playback Lifecycle

Current:

```text
SetAVTransportURI -> ZxtMediaPlayer.setURI -> RenderPlayerService -> GPlayer/ImageDisplay -> auto play
```

Required:

```text
SetAVTransportURI -> cache URI + metadata + state STOPPED
Play -> create MediaItem -> ExoPlayer.prepare() -> ExoPlayer.play()
Pause -> ExoPlayer.pause()
Stop -> ExoPlayer.stop()
Seek -> ExoPlayer.seekTo()
```

### State Model

Replace mutable Java fields and broadcasts with:

- immutable Kotlin data classes
- `StateFlow<RendererState>`
- explicit `RendererCommand` handling
- Media3 listener updates into the state store

Suggested state:

```text
RendererState(
  deviceName,
  ipAddress,
  status,
  currentUri,
  metadata,
  positionMs,
  durationMs,
  volume,
  muted,
  error
)
```

### SOAP And Metadata Parsing

Legacy code does string matching against DIDL-Lite metadata. New code should parse XML using `XmlPullParser` or a small typed parser:

- detect `object.item.videoItem`
- detect `object.item.audioItem`
- detect `object.item.imageItem`
- read `dc:title`
- read `res@protocolInfo`
- tolerate missing metadata

### Protocol Info

Align `ConnectionManager` with target support:

- mp4: `video/mp4`, `audio/mp4`
- mkv: `video/x-matroska`, `video/x-mkv`
- hls: `application/vnd.apple.mpegurl`, `application/x-mpegURL`
- mp3: `audio/mpeg`
- flac: `audio/flac`, `audio/x-flac`
- jpg: `image/jpeg`
- png: `image/png`

## Should Be Deleted From Target MVP

Delete or exclude these from the new app architecture:

- DMS module: `com.zxt.dlna.dms`
- DMC module: `com.zxt.dlna.dmc`
- DMP phone player/browser module: most of `com.zxt.dlna.dmp`
- old tab activities: `IndexActivity`, `DevicesActivity`, `ContentActivity`, `ControlActivity`, `BrowserActivity`, `StartActivity`, `AboutActivity`, `SettingActivity`
- legacy broadcast playback path: `Action`, `RenderPlayerService`, `GPlayer`
- old external storage/media scanning logic
- bundled old support/UI/image-loader libraries
- unused Cling control point, media server support, mock classes, and old transport implementations if vendoring Cling is chosen

## Target Architecture Recommendation

### `dlna`

Responsibilities:

- SSDP NOTIFY and M-SEARCH response
- device description HTTP endpoint
- service description HTTP endpoints
- SOAP dispatch by service and action
- LastChange eventing if needed for compatibility

Suggested packages:

```text
dlna/ssdp
dlna/description
dlna/soap
dlna/avtransport
dlna/renderingcontrol
dlna/connectionmanager
dlna/model
```

### `player`

Responsibilities:

- Own Media3 ExoPlayer
- Convert DMR commands into Media3 calls
- Emit state updates
- Handle buffering/error/end-of-media
- Support video, audio, and image rendering strategy

Suggested boundary:

```kotlin
interface DlnaPlayerController {
    val state: StateFlow<RendererState>
    suspend fun setUri(uri: Uri, metadata: DlnaMetadata?)
    suspend fun play()
    suspend fun pause()
    suspend fun stop()
    suspend fun seekTo(positionMs: Long)
    suspend fun setVolume(volume: Int)
    suspend fun setMute(muted: Boolean)
}
```

### `service`

Responsibilities:

- Start renderer on app launch
- Keep renderer alive for Android TV usage
- Own multicast lock and lifecycle cleanup
- Expose state to UI/repository

### `ui`

First screen should be the receiver dashboard:

- Device Name
- IP Address
- Renderer Status
- Current URI
- Current Position
- Volume

No DMS/DMC browsing UI should be included in MVP.

## Key Risks For Migration

| Risk | Impact | Mitigation |
|---|---|---|
| Copying legacy DMR classes directly | Breaks required `SetAVTransportURI` lifecycle | Rewrite player bridge first |
| Keeping Activity-owned renderer startup | DMR disappears when UI lifecycle changes | Move renderer into service/application lifecycle |
| Depending on old embedded Cling unchanged | Target SDK/build issues, dependency bloat | Prefer maintained dependency or trim vendored protocol stack |
| String parsing DIDL metadata | Crashes or wrong media type with real controllers | Use `XmlPullParser` and tolerant defaults |
| Missing HLS/FLAC protocol info | Controllers may refuse to send supported media | Expand `ConnectionManager` sink protocol info |
| Broadcast-based player commands | Race conditions and untestable control path | Use typed controller API and StateFlow |

## Recommended Migration Order

1. Create Kotlin renderer state and `DlnaPlayerController` abstraction.
2. Implement Media3-backed player controller.
3. Port/refactor AVTransport actions against the controller.
4. Port/refactor RenderingControl against the controller.
5. Port/refactor ConnectionManager protocol info.
6. Implement or integrate SSDP/device description/SOAP stack.
7. Build Android TV status UI.
8. Validate with BubbleUPnP, VLC/Kodi discovery, Jellyfin cast, and Windows Play To.

## Final Classification

### Can Be Directly Reused

Strictly speaking, no production code should be copied directly into the target Kotlin/Media3 app without adaptation.

Reusable as-is only as reference:

- DMR service list and UPnP type choices from `ZxtMediaRenderer`
- required action coverage from `AVTransportService`
- rendering control action mapping from `AudioRenderingControl`
- protocol MIME list from `ZxtConnectionManagerService`
- Cling protocol stack behavior and model classes if dependency strategy permits

### Needs Refactor

- `ZxtMediaRenderer`
- `AVTransportService`
- `AudioRenderingControl`
- `ZxtConnectionManagerService`
- `ZxtMediaPlayer`
- `ZxtMediaPlayers`
- `UpnpUtil`
- selected Cling Android service/bootstrap pieces

### Needs Delete

- `com.zxt.dlna.dms`
- `com.zxt.dlna.dmc`
- phone/tablet `activity` package
- legacy `dmp` player/browser UI
- `RenderPlayerService`
- `Action` broadcast command path
- old Gradle/JAR dependency setup
- old resources unrelated to Android TV receiver dashboard

