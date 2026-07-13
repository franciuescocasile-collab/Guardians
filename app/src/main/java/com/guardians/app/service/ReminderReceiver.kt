package com.guardians.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.guardians.app.R
import com.guardians.app.data.NotifierRepository
import com.guardians.app.data.tr

/**
 * Riceve lo scatto di un promemoria usa-e-getta del Notificatore: mostra la
 * notifica (silenziosa se siamo nella fascia notturna) e subito dopo invoca
 * la rimozione del record — la sveglia si auto-distrugge, niente ripetizioni.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val text = intent.getStringExtra("text") ?: return
        NotifierRepository.load(context)

        // Filtro notturno: durante la notte niente avviso acustico (canale muto).
        val muted = NotifierRepository.isNightMuted()
        val channelId = if (muted) "guardians_reminders_silent" else "guardians_reminders"
        ensureChannel(context, channelId, muted)

        val n = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_guardian)
            .setContentTitle(tr("Promemoria", "Reminder"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .apply { if (muted) setSilent(true) }
            .build()
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(("reminder-$id").hashCode(), n)
        } catch (_: Exception) {
        }

        // Auto-distruzione: il record sparisce dal DB locale.
        NotifierRepository.consume(context, id)
    }

    private fun ensureChannel(context: Context, id: String, silent: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val importance = if (silent) NotificationManager.IMPORTANCE_LOW
        else NotificationManager.IMPORTANCE_HIGH
        val ch = NotificationChannel(id, "Promemoria", importance)
        nm.createNotificationChannel(ch)
    }
}
