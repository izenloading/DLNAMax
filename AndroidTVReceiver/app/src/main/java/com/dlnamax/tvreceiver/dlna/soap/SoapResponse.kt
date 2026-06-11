package com.dlnamax.tvreceiver.dlna.soap

object SoapResponse {
    fun success(serviceType: String, action: String): String =
        envelope(
            """
            <u:${action}Response xmlns:u="$serviceType"/>
            """.trimIndent(),
        )

    fun success(
        serviceType: String,
        action: String,
        arguments: Map<String, String>,
    ): String =
        envelope(
            buildString {
                append("<u:${action}Response xmlns:u=\"$serviceType\">")
                arguments.forEach { (name, value) ->
                    append("<$name>${escapeXml(value)}</$name>")
                }
                append("</u:${action}Response>")
            },
        )

    fun fault(fault: SoapFault): String =
        envelope(
            """
            <s:Fault>
                <faultcode>s:Client</faultcode>
                <faultstring>UPnPError</faultstring>
                <detail>
                    <UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
                        <errorCode>${fault.code}</errorCode>
                        <errorDescription>${escapeXml(fault.description)}</errorDescription>
                    </UPnPError>
                </detail>
            </s:Fault>
            """.trimIndent(),
        )

    private fun envelope(body: String): String =
        """
        <?xml version="1.0"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
            <s:Body>
                $body
            </s:Body>
        </s:Envelope>
        """.trimIndent()

    fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
