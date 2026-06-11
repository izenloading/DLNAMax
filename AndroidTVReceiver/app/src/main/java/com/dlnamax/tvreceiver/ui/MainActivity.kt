package com.dlnamax.tvreceiver.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.dlnamax.tvreceiver.service.RendererService

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startService(Intent(this, RendererService::class.java))

        val statusView = TextView(this).apply {
            text = "Android TV Receiver\nSSDP Discovery running"
            textSize = 24f
            setPadding(48, 48, 48, 48)
        }
        setContentView(statusView)
    }
}
