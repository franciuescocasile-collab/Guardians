package com.guardians.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guardians.app.data.TimerRepository

/** Riavvia il monitoraggio all'accensione del telefono. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        TimerRepository.load(context)
        if (TimerRepository.timers.value.any { it.enabled }) {
            MonitorService.start(context)
        }
    }
}
