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
import com.guardians.app.AlarmActivity
import com.guardians.app.R
import com.guardians.app.data.tr

/**
 * Riceve lo scatto della Sveglia Intelligente e mostra una notifica FULL
 * SCREEN: a schermo spento apre direttamente AlarmActivity SOPRA il blocco
 * schermo; a schermo acceso appare l'heads-up. Il suono è quello di sveglia
 * di sistema sul canale (USAGE_ALARM) e con FLAG_INSISTENT continua a
 * suonare finché la notifica non viene cancellata da "Spegni" o "Rimanda".
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "guardians_alarm"
        const val NOTIFICATION_ID = 77
    }

    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

            // Canale della sveglia: suono di ALLARME (non di notifica) e vibrazione.
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val channel = NotificationChannel(
                CHANNEL_ID,
                tr("Sveglia intelligente", "Smart alarm"),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setSound(
                    alarmSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 700, 400, 700, 400, 700)
                setBypassDnd(true)
            }
            nm.createNotificationChannel(channel)

            val fullScreen = PendingIntent.getActivity(
                context, 771,
                Intent(context, AlarmActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_guardian)
                .setContentTitle(tr("Sveglia!", "Wake up!"))
                .setContentText(
                    tr(
                        "Fine dei cicli di sonno: è il momento giusto per alzarsi.",
                        "Sleep cycles complete: it's the right moment to get up.",
                    ),
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreen, true)
                .setContentIntent(fullScreen)
                .setOngoing(true)
                .setAutoCancel(false)
                .build()
            // INSISTENT: il suono va in loop finché la notifica non si cancella.
            notification.flags = notification.flags or
                android.app.Notification.FLAG_INSISTENT

            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
        }
    }
}
