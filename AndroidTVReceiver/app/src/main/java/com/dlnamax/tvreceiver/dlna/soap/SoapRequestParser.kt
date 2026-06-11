package com.dlnamax.tvreceiver.dlna.soap

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

object SoapRequestParser {
    fun parse(soapActionHeader: String?, body: String): SoapRequest? {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(StringReader(body))
        }
        var action: String? = parseActionName(soapActionHeader)
        val arguments = mutableMapOf<String, String>()
        var actionDepth: Int

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name.equals("Body", ignoreCase = true)) {
                actionDepth = parser.depth + 1
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.depth == actionDepth) {
                        action = action ?: parser.name
                        readActionArguments(parser, arguments)
                        return action?.let { SoapRequest(it, arguments.toMap()) }
                    }
                }
            }
        }

        return null
    }

    private fun readActionArguments(
        parser: XmlPullParser,
        arguments: MutableMap<String, String>,
    ) {
        val actionDepth = parser.depth
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == actionDepth) return
            if (parser.eventType == XmlPullParser.START_TAG && parser.depth == actionDepth + 1) {
                arguments[parser.name] = parser.nextText()
            }
        }
    }

    private fun parseActionName(soapActionHeader: String?): String? =
        soapActionHeader
            ?.trim()
            ?.trim('"')
            ?.substringAfter('#', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
}
