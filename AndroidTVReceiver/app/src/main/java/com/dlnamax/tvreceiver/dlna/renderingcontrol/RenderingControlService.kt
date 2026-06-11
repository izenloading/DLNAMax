package com.dlnamax.tvreceiver.dlna.renderingcontrol

import com.dlnamax.tvreceiver.dlna.soap.SoapFault
import com.dlnamax.tvreceiver.dlna.soap.SoapRequest
import com.dlnamax.tvreceiver.dlna.soap.SoapResponse
import com.dlnamax.tvreceiver.player.Media3DlnaPlayer

class RenderingControlService(
    private val player: Media3DlnaPlayer,
) {
    fun handle(request: SoapRequest): String =
        when (request.action) {
            ACTION_GET_VOLUME -> SoapResponse.success(
                serviceType = SERVICE_TYPE,
                action = request.action,
                arguments = mapOf("CurrentVolume" to player.refreshedSnapshot().volume.toString()),
            )
            ACTION_SET_VOLUME -> handleSetVolume(request)
            ACTION_GET_MUTE -> SoapResponse.success(
                serviceType = SERVICE_TYPE,
                action = request.action,
                arguments = mapOf("CurrentMute" to player.refreshedSnapshot().muted.toUpnpBoolean()),
            )
            ACTION_SET_MUTE -> handleSetMute(request)
            else -> SoapResponse.fault(
                SoapFault.invalidAction("Unsupported RenderingControl action: ${request.action}"),
            )
        }

    private fun handleSetVolume(request: SoapRequest): String {
        val volume = request.arguments[ARG_DESIRED_VOLUME]?.toIntOrNull()
            ?: return SoapResponse.fault(SoapFault.invalidArgs("DesiredVolume is required"))
        player.setVolume(volume)
        return SoapResponse.success(serviceType = SERVICE_TYPE, action = request.action)
    }

    private fun handleSetMute(request: SoapRequest): String {
        val muted = when (request.arguments[ARG_DESIRED_MUTE]?.trim()) {
            "1", "true", "TRUE", "True" -> true
            "0", "false", "FALSE", "False" -> false
            else -> return SoapResponse.fault(SoapFault.invalidArgs("DesiredMute is required"))
        }
        player.setMuted(muted)
        return SoapResponse.success(serviceType = SERVICE_TYPE, action = request.action)
    }

    private fun Boolean.toUpnpBoolean(): String = if (this) "1" else "0"

    companion object {
        const val SERVICE_TYPE = "urn:schemas-upnp-org:service:RenderingControl:1"
        const val SERVICE_ID = "urn:upnp-org:serviceId:RenderingControl"
        const val CONTROL_PATH = "/upnp/control/RenderingControl"
        const val EVENT_PATH = "/upnp/event/RenderingControl"
        const val SCPD_PATH = "/RenderingControl.xml"

        private const val ACTION_GET_VOLUME = "GetVolume"
        private const val ACTION_SET_VOLUME = "SetVolume"
        private const val ACTION_GET_MUTE = "GetMute"
        private const val ACTION_SET_MUTE = "SetMute"
        private const val ARG_DESIRED_VOLUME = "DesiredVolume"
        private const val ARG_DESIRED_MUTE = "DesiredMute"
    }
}
