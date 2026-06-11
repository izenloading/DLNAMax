package com.dlnamax.tvreceiver.dlna.avtransport

import com.dlnamax.tvreceiver.dlna.soap.SoapFault
import com.dlnamax.tvreceiver.dlna.soap.SoapRequest
import com.dlnamax.tvreceiver.dlna.soap.SoapResponse
import com.dlnamax.tvreceiver.dlna.soap.TimeFormat
import com.dlnamax.tvreceiver.player.Media3DlnaPlayer
import com.dlnamax.tvreceiver.player.PlaybackState

class AVTransportService(
    private val player: Media3DlnaPlayer,
) {
    fun handle(request: SoapRequest): String =
        when (request.action) {
            ACTION_SET_AV_TRANSPORT_URI -> {
                val uri = request.arguments[ARG_CURRENT_URI].orEmpty()
                if (uri.isBlank()) {
                    SoapResponse.fault(SoapFault.invalidAction("CurrentURI is required"))
                } else {
                    player.setMediaUri(uri)
                    SoapResponse.success(serviceType = SERVICE_TYPE, action = request.action)
                }
            }
            ACTION_PLAY -> {
                player.play()
                SoapResponse.success(serviceType = SERVICE_TYPE, action = request.action)
            }
            ACTION_PAUSE -> {
                player.pause()
                SoapResponse.success(serviceType = SERVICE_TYPE, action = request.action)
            }
            ACTION_STOP -> {
                player.stop()
                SoapResponse.success(serviceType = SERVICE_TYPE, action = request.action)
            }
            ACTION_SEEK -> handleSeek(request)
            ACTION_GET_POSITION_INFO -> {
                val snapshot = player.refreshedSnapshot()
                SoapResponse.success(
                    serviceType = SERVICE_TYPE,
                    action = request.action,
                    arguments = mapOf(
                        "Track" to "1",
                        "TrackDuration" to TimeFormat.formatMillis(snapshot.durationMs),
                        "TrackMetaData" to "",
                        "TrackURI" to snapshot.currentUri,
                        "RelTime" to TimeFormat.formatMillis(snapshot.currentPositionMs),
                        "AbsTime" to TimeFormat.formatMillis(snapshot.currentPositionMs),
                        "RelCount" to "0",
                        "AbsCount" to "0",
                    ),
                )
            }
            ACTION_GET_TRANSPORT_INFO -> {
                val snapshot = player.refreshedSnapshot()
                SoapResponse.success(
                    serviceType = SERVICE_TYPE,
                    action = request.action,
                    arguments = mapOf(
                        "CurrentTransportState" to snapshot.playbackState.toTransportState(),
                        "CurrentTransportStatus" to "OK",
                        "CurrentSpeed" to "1",
                    ),
                )
            }
            else -> SoapResponse.fault(SoapFault.invalidAction("Unsupported AVTransport action: ${request.action}"))
        }

    private fun handleSeek(request: SoapRequest): String {
        val unit = request.arguments[ARG_UNIT].orEmpty()
        if (unit != SEEK_UNIT_REL_TIME) {
            return SoapResponse.fault(SoapFault.invalidArgs("Only REL_TIME seek is supported"))
        }

        val target = request.arguments[ARG_TARGET].orEmpty()
        val positionMs = TimeFormat.parseMillis(target)
            ?: return SoapResponse.fault(SoapFault.invalidArgs("Invalid seek target: $target"))

        player.seekTo(positionMs)
        return SoapResponse.success(serviceType = SERVICE_TYPE, action = request.action)
    }

    private fun PlaybackState.toTransportState(): String =
        when (this) {
            PlaybackState.STOPPED -> "STOPPED"
            PlaybackState.PLAYING -> "PLAYING"
            PlaybackState.PAUSED -> "PAUSED_PLAYBACK"
            PlaybackState.BUFFERING -> "TRANSITIONING"
        }

    companion object {
        const val SERVICE_TYPE = "urn:schemas-upnp-org:service:AVTransport:1"
        const val SERVICE_ID = "urn:upnp-org:serviceId:AVTransport"
        const val CONTROL_PATH = "/upnp/control/AVTransport"
        const val EVENT_PATH = "/upnp/event/AVTransport"
        const val SCPD_PATH = "/AVTransport.xml"

        private const val ACTION_SET_AV_TRANSPORT_URI = "SetAVTransportURI"
        private const val ACTION_PLAY = "Play"
        private const val ACTION_PAUSE = "Pause"
        private const val ACTION_STOP = "Stop"
        private const val ACTION_SEEK = "Seek"
        private const val ACTION_GET_POSITION_INFO = "GetPositionInfo"
        private const val ACTION_GET_TRANSPORT_INFO = "GetTransportInfo"
        private const val ARG_CURRENT_URI = "CurrentURI"
        private const val ARG_UNIT = "Unit"
        private const val ARG_TARGET = "Target"
        private const val SEEK_UNIT_REL_TIME = "REL_TIME"
    }
}
