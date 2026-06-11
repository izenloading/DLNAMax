# AGENTS.md

开发规则：

1. 优先遵守 AGENTS.md

2. 每次只完成一个功能

3. 修改代码后必须执行：

./gradlew assembleDebug

4. 修复所有编译错误

5. 不允许留下 TODO

6. 不允许伪代码

7. 不允许 Mock 网络层

8. 输出：
   - 修改文件列表
   - 编译结果
   - 后续建议

9. 如果需求过大：
   先拆分任务
   不直接编码

10. 优先参考项目：

Android-DLNA-Server

不要引入新的 DLNA SDK。

## Project

Android TV DLNA Receiver

目标：

开发一个运行于 Android TV 的 DLNA Digital Media Renderer (DMR)。

设备能够被局域网中的 DLNA 控制器发现，并接收媒体播放命令。

典型使用场景：

BubbleUPnP
    ↓
Android TV Receiver
    ↓
Media3 ExoPlayer

Jellyfin
    ↓
Cast
    ↓
Android TV Receiver

Windows Media Player
    ↓
Play To
    ↓
Android TV Receiver

---

## Product Scope

第一阶段：

支持：

- SSDP Discovery
- UPnP Device Description
- AVTransport
- RenderingControl
- ConnectionManager

支持媒体：

- mp4
- mkv
- hls(m3u8)
- mp3
- flac
- jpg
- png

不支持：

- DLNA Media Server
- DLNA Control Point
- AirPlay
- Miracast

---

## Architecture

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

---

## Technical Stack

Language

- Kotlin

Player

- AndroidX Media3
- ExoPlayer

Networking

- OkHttp

XML

- XmlPullParser

Dependency Injection

- Hilt

Min SDK

- 26

Target SDK

- latest stable

---

## Core DMR Lifecycle

1.

启动 App

↓

启动 SSDP 广播

↓

向

239.255.255.250:1900

发送 NOTIFY

↓

广播自己是

MediaRenderer

2.

控制端发现设备

↓

请求 DeviceDescription.xml

↓

读取服务列表

3.

控制端调用

SetAVTransportURI

↓

保存媒体 URI

4.

控制端调用

Play

↓

ExoPlayer 开始播放

5.

控制端调用

Pause

↓

ExoPlayer.pause()

6.

控制端调用

Stop

↓

ExoPlayer.stop()

---

## Mandatory UPnP Services

### AVTransport

必须实现：

- SetAVTransportURI
- Play
- Pause
- Stop
- Seek
- GetPositionInfo
- GetTransportInfo

### RenderingControl

必须实现：

- GetVolume
- SetVolume
- GetMute
- SetMute

### ConnectionManager

必须实现：

- GetProtocolInfo
- GetCurrentConnectionIDs

---

## Media3 Integration Rules

收到：

SetAVTransportURI

后：

创建 MediaItem

不要立即播放

仅缓存 URI

收到：

Play

后：

prepare()

play()

收到：

Pause

后：

pause()

收到：

Stop

后：

stop()

收到：

Seek

后：

seekTo()

---

## Device Description

deviceType:

urn:schemas-upnp-org:device:MediaRenderer:1

friendlyName:

Android TV Receiver

manufacturer:

Open Source

modelName:

AndroidTVDLNA

---

## SSDP Requirements

监听：

UDP 1900

加入：

239.255.255.250

支持：

M-SEARCH

响应：

HTTP/1.1 200 OK

必须返回：

LOCATION
CACHE-CONTROL
USN
SERVER
ST

---

## SOAP Requirements

支持：

POST

Content-Type:

text/xml

SOAPAction

解析：

AVTransport

RenderingControl

ConnectionManager

---

## UI Requirements

首页显示：

Device Name

IP Address

Renderer Status

Current URI

Current Position

Volume

播放状态：

STOPPED

PLAYING

PAUSED

BUFFERING

ERROR

---

## Testing Checklist

### Discovery

- BubbleUPnP 能发现设备
- VLC 能发现设备
- Kodi 能发现设备
- Jellyfin 能发现设备

### Playback

- mp4 播放成功
- hls 播放成功
- mkv 播放成功

### Transport

- Play 正常
- Pause 正常
- Stop 正常
- Seek 正常

### Volume

- SetVolume 正常
- Mute 正常

### Stability

- 播放 2 小时无崩溃
- 网络断开恢复
- App 后台运行

---

## Development Priority

P0

- SSDP
- Device Description
- AVTransport
- Media3

P1

- RenderingControl
- Seek
- Position Sync

P2

- Subtitle Support
- Album Art

P3

- AirPlay Receiver
- Google Cast Receiver

---

## Coding Rules

- Kotlin Only
- Coroutines First
- No AsyncTask
- No Java Legacy APIs
- Prefer StateFlow
- Prefer immutable data classes

---

## Definition of Done

BubbleUPnP

发现设备

↓

发送视频

↓

Android TV

自动播放

↓

Pause

↓

Resume

↓

Stop

全部成功

视为 MVP 完成。
