# SSDP Verification

Date: 2026-06-11

Scope: current SSDP implementation under `AndroidTVReceiver/`. This verification does not cover AVTransport, SOAP, DeviceDescription HTTP serving, or Media3.

## Summary

| Requirement | Status | Evidence |
|---|---|---|
| Listen on `239.255.255.250:1900` | Pass | `SsdpServer.MULTICAST_ADDRESS` is `239.255.255.250`, `SSDP_PORT` is `1900`, `MulticastSocket` binds to port `1900`, and joins the multicast group. |
| Respond to `M-SEARCH` | Pass | Incoming packets are parsed by `SsdpSearchRequest.parse`; matching targets trigger `HTTP/1.1 200 OK` responses sent back to the requester address/port. |
| Send `NOTIFY` | Pass | `ssdp:alive` messages are sent on startup and periodically; `ssdp:byebye` messages are sent on shutdown. |

## 1. Listening on `239.255.255.250:1900`

Status: Pass

Code evidence:

- `AndroidTVReceiver/app/src/main/java/com/dlnamax/tvreceiver/dlna/ssdp/SsdpServer.kt`
  - Defines `MULTICAST_ADDRESS = "239.255.255.250"`.
  - Defines `SSDP_PORT = 1900`.
  - Creates `MulticastSocket(null)`.
  - Sets `reuseAddress = true`.
  - Binds with `bind(InetSocketAddress(SSDP_PORT))`.
  - Joins the multicast group with `joinGroup(multicastAddress)`.
  - Receives datagrams through `serverSocket.receive(packet)` in the listener loop.

Runtime prerequisites:

- `RendererService` starts `SsdpServer` in `onCreate`.
- `RendererService` acquires a Wi-Fi multicast lock via `WifiManager.createMulticastLock`.
- Manifest declares `CHANGE_WIFI_MULTICAST_STATE`, `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`, and `INTERNET`.

Current limitation:

- The server starts only if an active non-loopback IPv4 network interface with multicast support is found.

## 2. Responding to `M-SEARCH`

Status: Pass

Code evidence:

- `SsdpSearchRequest.parse` accepts packets whose request line starts with `M-SEARCH`.
- It requires `MAN: "ssdp:discover"`.
- It requires a non-empty `ST` header.
- `SsdpSearchRequest.matches` responds to:
  - `ssdp:all`
  - `upnp:rootdevice`
  - `urn:schemas-upnp-org:device:MediaRenderer:1`
  - the renderer `uuid:...` UDN
- `SsdpServer.handlePacket` generates a response through `SsdpResponseFactory.discoveryResponse`.
- The response is sent back to `packet.address` and `packet.port`, which is the controller's source endpoint.

Response headers included:

- `HTTP/1.1 200 OK`
- `CACHE-CONTROL`
- `EXT`
- `LOCATION`
- `SERVER`
- `ST`
- `USN`

Current limitation:

- `LOCATION` points to `/DeviceDescription.xml`, but this verification only covers SSDP. The DeviceDescription HTTP endpoint is not implemented in this SSDP-only step.

## 3. Sending `NOTIFY`

Status: Pass

Code evidence:

- `SsdpServer.start` calls `sendAliveNotifications` immediately after socket creation.
- `notifyAlivePeriodically` sends alive notifications every `maxAgeSeconds / 2`.
- `SsdpServer.stop` calls `sendByebyeNotifications`.
- `SsdpResponseFactory.aliveNotifications` generates `NOTIFY * HTTP/1.1` messages with `NTS: ssdp:alive`.
- `SsdpResponseFactory.byebyeNotifications` generates `NOTIFY * HTTP/1.1` messages with `NTS: ssdp:byebye`.
- Notifications are sent to `239.255.255.250:1900`.

Notification targets:

- `upnp:rootdevice`
- renderer UDN, `uuid:...`
- `urn:schemas-upnp-org:device:MediaRenderer:1`

## Overall Result

The current SSDP implementation satisfies the requested P0 SSDP discovery checks:

1. It listens on SSDP multicast group `239.255.255.250` and UDP port `1900`.
2. It responds to valid `M-SEARCH` discovery requests for supported targets.
3. It sends `NOTIFY` alive and byebye advertisements.

