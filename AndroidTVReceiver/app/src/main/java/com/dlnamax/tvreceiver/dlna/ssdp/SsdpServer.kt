package com.dlnamax.tvreceiver.dlna.ssdp

import android.util.Log
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class SsdpServer(
    private val config: SsdpDeviceConfig,
) {
    private val running = AtomicBoolean(false)
    private val multicastAddress: InetAddress = InetAddress.getByName(MULTICAST_ADDRESS)
    private var socket: MulticastSocket? = null
    private var listenerThread: Thread? = null
    private var notifierThread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return

        val networkInterface = findIpv4NetworkInterface()
        val hostAddress = networkInterface?.ipv4Address?.hostAddress

        if (networkInterface == null || hostAddress.isNullOrBlank()) {
            running.set(false)
            Log.w(TAG, "No active IPv4 network interface; SSDP server not started")
            return
        }

        val serverSocket = createSocket(networkInterface.networkInterface)
        socket = serverSocket

        sendAliveNotifications(serverSocket, hostAddress)

        listenerThread = Thread(
            { listen(serverSocket, hostAddress) },
            "SsdpServer-Listener",
        ).also { it.start() }

        notifierThread = Thread(
            { notifyAlivePeriodically(hostAddress) },
            "SsdpServer-Notifier",
        ).also { it.start() }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return

        socket?.let { serverSocket ->
            sendByebyeNotifications(serverSocket)
            try {
                serverSocket.leaveGroup(multicastAddress)
            } catch (error: Exception) {
                Log.d(TAG, "Unable to leave SSDP multicast group", error)
            }
            serverSocket.close()
        }

        listenerThread = null
        notifierThread = null
        socket = null
    }

    private fun createSocket(networkInterface: NetworkInterface): MulticastSocket {
        val serverSocket = MulticastSocket(null).apply {
            reuseAddress = true
            timeToLive = 4
            this.networkInterface = networkInterface
            bind(InetSocketAddress(SSDP_PORT))
            joinGroup(multicastAddress)
        }
        return serverSocket
    }

    private fun listen(serverSocket: MulticastSocket, hostAddress: String) {
        val buffer = ByteArray(RECEIVE_BUFFER_SIZE)

        while (running.get()) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                serverSocket.receive(packet)
                handlePacket(serverSocket, packet, hostAddress)
            } catch (error: SocketException) {
                if (running.get()) Log.w(TAG, "SSDP socket error", error)
            } catch (error: Exception) {
                Log.w(TAG, "SSDP receive failed", error)
            }
        }
    }

    private fun handlePacket(serverSocket: MulticastSocket, packet: DatagramPacket, hostAddress: String) {
        val payload = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8)
        val request = SsdpSearchRequest.parse(payload) ?: return

        if (!request.matches(config)) return

        val response = SsdpResponseFactory.discoveryResponse(
            config = config,
            hostAddress = hostAddress,
            searchTarget = request.searchTarget,
        )
        sendMessage(
            serverSocket = serverSocket,
            message = response,
            address = packet.address,
            port = packet.port,
        )
    }

    private fun notifyAlivePeriodically(hostAddress: String) {
        while (running.get()) {
            try {
                Thread.sleep((config.maxAgeSeconds / 2L) * 1000L)
                socket?.let { sendAliveNotifications(it, hostAddress) }
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            } catch (error: Exception) {
                Log.w(TAG, "SSDP alive notification failed", error)
            }
        }
    }

    private fun sendAliveNotifications(serverSocket: MulticastSocket, hostAddress: String) {
        SsdpResponseFactory.aliveNotifications(config, hostAddress).forEach { message ->
            sendMessage(serverSocket, message, multicastAddress, SSDP_PORT)
        }
    }

    private fun sendByebyeNotifications(serverSocket: MulticastSocket) {
        SsdpResponseFactory.byebyeNotifications(config).forEach { message ->
            sendMessage(serverSocket, message, multicastAddress, SSDP_PORT)
        }
    }

    private fun sendMessage(
        serverSocket: MulticastSocket,
        message: String,
        address: InetAddress,
        port: Int,
    ) {
        val bytes = message.toByteArray(StandardCharsets.UTF_8)
        serverSocket.send(DatagramPacket(bytes, bytes.size, address, port))
    }

    private fun findIpv4NetworkInterface(): Ipv4NetworkInterface? =
        NetworkInterface.getNetworkInterfaces()
            .toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback && it.supportsMulticast() }
            .mapNotNull { networkInterface ->
                networkInterface.inetAddresses
                    .toList()
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    ?.let { Ipv4NetworkInterface(networkInterface, it) }
            }
            .firstOrNull()

    private data class Ipv4NetworkInterface(
        val networkInterface: NetworkInterface,
        val ipv4Address: Inet4Address,
    )

    companion object {
        const val MULTICAST_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        private const val RECEIVE_BUFFER_SIZE = 2048
        private const val TAG = "SsdpServer"
    }
}
