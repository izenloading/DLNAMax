package com.dlnamax.tvreceiver.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.util.Log
import com.dlnamax.tvreceiver.dlna.avtransport.AVTransportService
import com.dlnamax.tvreceiver.dlna.connectionmanager.ConnectionManagerService
import com.dlnamax.tvreceiver.dlna.http.DeviceDescriptionServer
import com.dlnamax.tvreceiver.dlna.renderingcontrol.RenderingControlService
import com.dlnamax.tvreceiver.dlna.ssdp.SsdpDeviceConfig
import com.dlnamax.tvreceiver.dlna.ssdp.SsdpServer
import com.dlnamax.tvreceiver.player.Media3DlnaPlayer

class RendererService : Service() {
    private var ssdpServer: SsdpServer? = null
    private var deviceDescriptionServer: DeviceDescriptionServer? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var player: Media3DlnaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        val config = SsdpDeviceConfig.createDefault()
        acquireMulticastLock()
        val media3Player = Media3DlnaPlayer(applicationContext)
        player = media3Player
        val avTransportService = AVTransportService(media3Player)
        val renderingControlService = RenderingControlService(media3Player)
        val connectionManagerService = ConnectionManagerService()
        deviceDescriptionServer = DeviceDescriptionServer(
            config = config,
            avTransportService = avTransportService,
            renderingControlService = renderingControlService,
            connectionManagerService = connectionManagerService,
        ).also { it.start() }
        ssdpServer = SsdpServer(config).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ssdpServer?.stop()
        ssdpServer = null
        deviceDescriptionServer?.stop()
        deviceDescriptionServer = null
        player?.release()
        player = null
        releaseMulticastLock()
        super.onDestroy()
    }

    private fun acquireMulticastLock() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicastLock = wifiManager
            ?.createMulticastLock("AndroidTVDLNA-SSDP")
            ?.apply {
                setReferenceCounted(false)
                acquire()
            }

        if (multicastLock == null) {
            Log.w(TAG, "Wi-Fi multicast lock unavailable")
        }
    }

    private fun releaseMulticastLock() {
        multicastLock
            ?.takeIf { it.isHeld }
            ?.release()
        multicastLock = null
    }

    companion object {
        private const val TAG = "RendererService"
    }
}
