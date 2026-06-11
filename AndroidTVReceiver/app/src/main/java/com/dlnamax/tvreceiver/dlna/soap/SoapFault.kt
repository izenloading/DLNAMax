package com.dlnamax.tvreceiver.dlna.soap

data class SoapFault(
    val code: Int,
    val description: String,
) {
    companion object {
        fun invalidAction(description: String): SoapFault =
            SoapFault(code = 401, description = description)

        fun invalidArgs(description: String): SoapFault =
            SoapFault(code = 402, description = description)
    }
}
