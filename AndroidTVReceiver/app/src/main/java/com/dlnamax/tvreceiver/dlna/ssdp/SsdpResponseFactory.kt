package com.dlnamax.tvreceiver.dlna.ssdp

object SsdpResponseFactory {
    fun discoveryResponse(
        config: SsdpDeviceConfig,
        hostAddress: String,
        searchTarget: String,
    ): String {
        val normalizedTarget = normalizeSearchTarget(config, searchTarget)
        return buildString {
            appendLine("HTTP/1.1 200 OK")
            appendLine("CACHE-CONTROL: max-age=${config.maxAgeSeconds}")
            appendLine("EXT:")
            appendLine("LOCATION: ${config.location(hostAddress)}")
            appendLine("SERVER: ${config.serverHeader}")
            appendLine("ST: $normalizedTarget")
            appendLine("USN: ${usnFor(config, normalizedTarget)}")
            appendLine()
        }.replace("\n", "\r\n")
    }

    fun aliveNotifications(config: SsdpDeviceConfig, hostAddress: String): List<String> =
        notificationTargets(config).map { target ->
            notifyMessage(
                config = config,
                hostAddress = hostAddress,
                notificationType = target,
                subType = "ssdp:alive",
                includeLocation = true,
            )
        }

    fun byebyeNotifications(config: SsdpDeviceConfig): List<String> =
        notificationTargets(config).map { target ->
            notifyMessage(
                config = config,
                hostAddress = "0.0.0.0",
                notificationType = target,
                subType = "ssdp:byebye",
                includeLocation = false,
            )
        }

    private fun notifyMessage(
        config: SsdpDeviceConfig,
        hostAddress: String,
        notificationType: String,
        subType: String,
        includeLocation: Boolean,
    ): String = buildString {
        appendLine("NOTIFY * HTTP/1.1")
        appendLine("HOST: ${SsdpServer.MULTICAST_ADDRESS}:${SsdpServer.SSDP_PORT}")
        appendLine("CACHE-CONTROL: max-age=${config.maxAgeSeconds}")
        if (includeLocation) appendLine("LOCATION: ${config.location(hostAddress)}")
        appendLine("NT: $notificationType")
        appendLine("NTS: $subType")
        appendLine("SERVER: ${config.serverHeader}")
        appendLine("USN: ${usnFor(config, notificationType)}")
        appendLine()
    }.replace("\n", "\r\n")

    private fun normalizeSearchTarget(config: SsdpDeviceConfig, searchTarget: String): String =
        when {
            searchTarget.equals(config.udn, ignoreCase = true) -> config.udn
            searchTarget.equals("upnp:rootdevice", ignoreCase = true) -> "upnp:rootdevice"
            else -> config.deviceType
        }

    private fun notificationTargets(config: SsdpDeviceConfig): List<String> =
        listOf("upnp:rootdevice", config.udn, config.deviceType)

    private fun usnFor(config: SsdpDeviceConfig, target: String): String =
        when {
            target.equals(config.udn, ignoreCase = true) -> config.udn
            target.equals("upnp:rootdevice", ignoreCase = true) -> "${config.udn}::upnp:rootdevice"
            else -> "${config.udn}::$target"
        }
}
