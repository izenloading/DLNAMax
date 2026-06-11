package com.dlnamax.tvreceiver.dlna.soap

data class SoapRequest(
    val action: String,
    val arguments: Map<String, String>,
)
