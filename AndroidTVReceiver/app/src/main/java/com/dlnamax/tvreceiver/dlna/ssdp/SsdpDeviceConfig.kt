package com.dlnamax.tvreceiver.dlna.ssdp

import java.util.Locale
import java.util.UUID

data class SsdpDeviceConfig(
    val uuid: String,
    val friendlyName: String = "Android TV Receiver",
    val manufacturer: String = "Open Source",
    val modelName: String = "AndroidTVDLNA",
    val httpPort: Int = 8080,
    val descriptionPath: String = "/description.xml",
    val maxAgeSeconds: Int = 1800,
) {
    val udn: String = "uuid:$uuid"
    val deviceType: String = "urn:schemas-upnp-org:device:MediaRenderer:1"
    val serverHeader: String = "Android/1.0 UPnP/1.1 AndroidTVDLNA/0.1"

    fun location(hostAddress: String): String = "http://$hostAddress:$httpPort$descriptionPath"

    companion object {
        fun createDefault(): SsdpDeviceConfig =
            SsdpDeviceConfig(uuid = UUID.nameUUIDFromBytes("AndroidTVDLNA".toByteArray()).toString().lowercase(Locale.US))
    }
}
