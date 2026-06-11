package com.dlnamax.tvreceiver.dlna.connectionmanager

import com.dlnamax.tvreceiver.dlna.soap.SoapFault
import com.dlnamax.tvreceiver.dlna.soap.SoapRequest
import com.dlnamax.tvreceiver.dlna.soap.SoapResponse

class ConnectionManagerService {
    fun handle(request: SoapRequest): String =
        when (request.action) {
            ACTION_GET_PROTOCOL_INFO -> SoapResponse.success(
                serviceType = SERVICE_TYPE,
                action = request.action,
                arguments = mapOf(
                    "Source" to "",
                    "Sink" to SINK_PROTOCOL_INFO,
                ),
            )
            ACTION_GET_CURRENT_CONNECTION_IDS -> SoapResponse.success(
                serviceType = SERVICE_TYPE,
                action = request.action,
                arguments = mapOf("ConnectionIDs" to "0"),
            )
            else -> SoapResponse.fault(
                SoapFault.invalidAction("Unsupported ConnectionManager action: ${request.action}"),
            )
        }

    companion object {
        const val SERVICE_TYPE = "urn:schemas-upnp-org:service:ConnectionManager:1"
        const val SERVICE_ID = "urn:upnp-org:serviceId:ConnectionManager"
        const val CONTROL_PATH = "/upnp/control/ConnectionManager"
        const val EVENT_PATH = "/upnp/event/ConnectionManager"
        const val SCPD_PATH = "/ConnectionManager.xml"

        private const val ACTION_GET_PROTOCOL_INFO = "GetProtocolInfo"
        private const val ACTION_GET_CURRENT_CONNECTION_IDS = "GetCurrentConnectionIDs"
        private const val SINK_PROTOCOL_INFO =
            "http-get:*:video/mp4:*,http-get:*:video/x-matroska:*,http-get:*:application/vnd.apple.mpegurl:*," +
                "http-get:*:audio/mpeg:*,http-get:*:audio/flac:*,http-get:*:image/jpeg:*,http-get:*:image/png:*"
    }
}
