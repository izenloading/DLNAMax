package com.dlnamax.tvreceiver.dlna.ssdp

data class SsdpSearchRequest(
    val searchTarget: String,
    val headers: Map<String, String>,
) {
    fun matches(config: SsdpDeviceConfig): Boolean {
        val target = searchTarget.trim()
        return target.equals("ssdp:all", ignoreCase = true) ||
            target.equals("upnp:rootdevice", ignoreCase = true) ||
            target.equals(config.deviceType, ignoreCase = true) ||
            target.equals(config.udn, ignoreCase = true)
    }

    companion object {
        fun parse(payload: String): SsdpSearchRequest? {
            val lines = payload.replace("\r\n", "\n").split('\n')
            val requestLine = lines.firstOrNull()?.trim().orEmpty()
            if (!requestLine.startsWith("M-SEARCH", ignoreCase = true)) return null

            val headers = lines.drop(1)
                .mapNotNull { line ->
                    val separator = line.indexOf(':')
                    if (separator <= 0) {
                        null
                    } else {
                        val name = line.substring(0, separator).trim().lowercase()
                        val value = line.substring(separator + 1).trim().trim('"')
                        name to value
                    }
                }
                .toMap()

            val man = headers["man"].orEmpty()
            if (!man.equals("ssdp:discover", ignoreCase = true)) return null

            val target = headers["st"].orEmpty()
            if (target.isBlank()) return null

            return SsdpSearchRequest(searchTarget = target, headers = headers)
        }
    }
}
