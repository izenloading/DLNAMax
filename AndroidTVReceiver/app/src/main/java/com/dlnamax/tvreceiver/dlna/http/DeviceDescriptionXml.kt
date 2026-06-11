package com.dlnamax.tvreceiver.dlna.http

import com.dlnamax.tvreceiver.dlna.avtransport.AVTransportService
import com.dlnamax.tvreceiver.dlna.connectionmanager.ConnectionManagerService
import com.dlnamax.tvreceiver.dlna.renderingcontrol.RenderingControlService
import com.dlnamax.tvreceiver.dlna.ssdp.SsdpDeviceConfig

object DeviceDescriptionXml {
    fun render(config: SsdpDeviceConfig): String =
        """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
            <specVersion>
                <major>1</major>
                <minor>0</minor>
            </specVersion>
            <device>
                <deviceType>${config.deviceType}</deviceType>
                <friendlyName>${config.friendlyName}</friendlyName>
                <manufacturer>${config.manufacturer}</manufacturer>
                <modelName>${config.modelName}</modelName>
                <UDN>${config.udn}</UDN>
                <serviceList>
                    <service>
                        <serviceType>${AVTransportService.SERVICE_TYPE}</serviceType>
                        <serviceId>${AVTransportService.SERVICE_ID}</serviceId>
                        <SCPDURL>${AVTransportService.SCPD_PATH}</SCPDURL>
                        <controlURL>${AVTransportService.CONTROL_PATH}</controlURL>
                        <eventSubURL>${AVTransportService.EVENT_PATH}</eventSubURL>
                    </service>
                    <service>
                        <serviceType>${RenderingControlService.SERVICE_TYPE}</serviceType>
                        <serviceId>${RenderingControlService.SERVICE_ID}</serviceId>
                        <SCPDURL>${RenderingControlService.SCPD_PATH}</SCPDURL>
                        <controlURL>${RenderingControlService.CONTROL_PATH}</controlURL>
                        <eventSubURL>${RenderingControlService.EVENT_PATH}</eventSubURL>
                    </service>
                    <service>
                        <serviceType>${ConnectionManagerService.SERVICE_TYPE}</serviceType>
                        <serviceId>${ConnectionManagerService.SERVICE_ID}</serviceId>
                        <SCPDURL>${ConnectionManagerService.SCPD_PATH}</SCPDURL>
                        <controlURL>${ConnectionManagerService.CONTROL_PATH}</controlURL>
                        <eventSubURL>${ConnectionManagerService.EVENT_PATH}</eventSubURL>
                    </service>
                </serviceList>
            </device>
        </root>
        """.trimIndent()
}
