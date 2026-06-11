package com.dlnamax.tvreceiver.dlna.http

import android.util.Log
import com.dlnamax.tvreceiver.dlna.avtransport.AVTransportScpdXml
import com.dlnamax.tvreceiver.dlna.avtransport.AVTransportService
import com.dlnamax.tvreceiver.dlna.connectionmanager.ConnectionManagerScpdXml
import com.dlnamax.tvreceiver.dlna.connectionmanager.ConnectionManagerService
import com.dlnamax.tvreceiver.dlna.renderingcontrol.RenderingControlScpdXml
import com.dlnamax.tvreceiver.dlna.renderingcontrol.RenderingControlService
import com.dlnamax.tvreceiver.dlna.ssdp.SsdpDeviceConfig
import com.dlnamax.tvreceiver.dlna.soap.SoapFault
import com.dlnamax.tvreceiver.dlna.soap.SoapRequestParser
import com.dlnamax.tvreceiver.dlna.soap.SoapResponse
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class DeviceDescriptionServer(
    private val config: SsdpDeviceConfig,
    private val avTransportService: AVTransportService,
    private val renderingControlService: RenderingControlService,
    private val connectionManagerService: ConnectionManagerService,
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var listenerThread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return

        val socket = ServerSocket(config.httpPort)
        serverSocket = socket
        listenerThread = Thread(
            { listen(socket) },
            "DeviceDescriptionServer",
        ).also { it.start() }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return

        try {
            serverSocket?.close()
        } catch (error: Exception) {
            Log.d(TAG, "Unable to close device description server", error)
        }

        serverSocket = null
        listenerThread = null
    }

    private fun listen(socket: ServerSocket) {
        while (running.get()) {
            try {
                socket.accept().use(::handleClient)
            } catch (error: SocketException) {
                if (running.get()) Log.w(TAG, "Device description socket error", error)
            } catch (error: Exception) {
                Log.w(TAG, "Device description request failed", error)
            }
        }
    }

    private fun handleClient(client: Socket) {
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
        val request = readHttpRequest(reader)

        val response = when (request?.method) {
            null -> plainResponse(400, "Bad Request")
            "GET" -> handleGet(request)
            "POST" -> handlePost(request)
            else -> plainResponse(405, "Method Not Allowed")
        }

        client.getOutputStream().use { output ->
            output.write(response)
            output.flush()
        }
    }

    private fun readHttpRequest(reader: BufferedReader): HttpRequest? {
        val requestLine = reader.readLine().orEmpty()
        val request = parseRequestLine(requestLine) ?: return null
        val headers = readHeaders(reader)
        val bodyLength = headers["content-length"]?.toIntOrNull() ?: 0
        val body = readBody(reader, bodyLength)

        return request.copy(headers = headers, body = body)
    }

    private fun readBody(reader: BufferedReader, bodyLength: Int): String {
        if (bodyLength <= 0) return ""

        val body = CharArray(bodyLength)
        var offset = 0
        while (offset < bodyLength) {
            val readCount = reader.read(body, offset, bodyLength - offset)
            if (readCount < 0) break
            offset += readCount
        }

        return body.concatToString(endIndex = offset)
    }

    private fun readHeaders(reader: BufferedReader): Map<String, String> =
        generateSequence { reader.readLine() }
            .takeWhile { it.isNotEmpty() }
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) {
                    null
                } else {
                    line.substring(0, separator).trim().lowercase(Locale.US) to
                        line.substring(separator + 1).trim()
                }
            }
            .toMap()

    private fun handleGet(request: HttpRequest): ByteArray =
        when (request.path) {
            config.descriptionPath -> xmlResponse(DeviceDescriptionXml.render(config))
            AVTransportService.SCPD_PATH -> xmlResponse(AVTransportScpdXml.render())
            RenderingControlService.SCPD_PATH -> xmlResponse(RenderingControlScpdXml.render())
            ConnectionManagerService.SCPD_PATH -> xmlResponse(ConnectionManagerScpdXml.render())
            else -> plainResponse(404, "Not Found")
        }

    private fun handlePost(request: HttpRequest): ByteArray {
        val soapRequest = runCatching {
            SoapRequestParser.parse(request.headers["soapaction"], request.body)
        }.getOrNull()
        val body = if (soapRequest == null) {
            SoapResponse.fault(
                SoapFault.invalidArgs("Invalid SOAP request"),
            )
        } else {
            when (request.path) {
                AVTransportService.CONTROL_PATH -> avTransportService.handle(soapRequest)
                RenderingControlService.CONTROL_PATH -> renderingControlService.handle(soapRequest)
                ConnectionManagerService.CONTROL_PATH -> connectionManagerService.handle(soapRequest)
                else -> return plainResponse(404, "Not Found")
            }
        }

        return xmlResponse(body)
    }

    private fun parseRequestLine(requestLine: String): HttpRequest? {
        val parts = requestLine.split(" ")
        if (parts.size < 2) return null

        return HttpRequest(
            method = parts[0].uppercase(Locale.US),
            path = parts[1].substringBefore("?"),
            headers = emptyMap(),
            body = "",
        )
    }

    private fun xmlResponse(body: String): ByteArray =
        httpResponse(
            statusCode = 200,
            reason = "OK",
            contentType = "text/xml; charset=\"utf-8\"",
            body = body,
        )

    private fun plainResponse(statusCode: Int, reason: String): ByteArray =
        httpResponse(
            statusCode = statusCode,
            reason = reason,
            contentType = "text/plain; charset=\"utf-8\"",
            body = reason,
        )

    private fun httpResponse(
        statusCode: Int,
        reason: String,
        contentType: String,
        body: String,
    ): ByteArray {
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $statusCode $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(StandardCharsets.UTF_8)

        return header + bodyBytes
    }

    private data class HttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String,
    )

    companion object {
        private const val TAG = "DeviceDescription"
    }
}
