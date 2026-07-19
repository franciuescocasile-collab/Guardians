package com.guardians.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.guardians.app.MainActivity
import com.guardians.app.R
import com.guardians.app.data.SpellsRepository
import com.guardians.app.data.tr

/**
 * Scatta a fine Congelamento (15.2/15.3), se l'utente ha scelto di essere
 * avvisato. A seconda della scelta suona come una SVEGLIA (suoneria insistente)
 * oppure arriva come una semplice NOTIFICA-messaggio.
 */
class FreezeEndReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_RING = "guardians_freeze_ring"
        const val CHANNEL_MSG = "guardians_freeze_msg"
        const val NOTIFICATION_ID = 78
    }

    override fun onReceive(context: Context, intent: Intent?) {
        try {
            SpellsRepository.load(context)
            val ring = SpellsRepository.freezeNotifyRing.value
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

            val channelId = if (ring) CHANNEL_RING else CHANNEL_MSG
            val channel = NotificationChannel(
                channelId,
                tr("Fine congelamento", "Freeze end"),
                if (ring) NotificationManager.IMPORTANCE_HIGH
                else NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                if (ring) {
                    val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    setSound(
                        alarmSound,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 400, 600)
                }
            }
            nm.createNotificationChannel(channel)

            val open = PendingIntent.getActivity(
                context, 781,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_guardian)
                .setContentTitle(tr("Congelamento finito", "Freeze finished"))
                .setContentText(
                    tr(
                        "Il tempo del congelamento è scaduto. Bentornato!",
                        "Your freeze time is up. Welcome back!",
                    ),
                )
                .setContentIntent(open)
                .setAutoCancel(true)

            if (ring) {
                builder.setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(open, true)
            } else {
                builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
            }

            val notification = builder.build()
            if (ring) {
                notification.flags = notification.flags or
                    android.app.Notification.FLAG_INSISTENT
            }
            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
        }
    }
}
