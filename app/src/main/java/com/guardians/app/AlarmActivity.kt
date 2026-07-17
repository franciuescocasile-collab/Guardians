package com.guardians.app

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guardians.app.data.SettingsRepository
import com.guardians.app.data.SmartAlarmRepository
import com.guardians.app.data.tr
import com.guardians.app.service.AlarmReceiver
import com.guardians.app.ui.theme.GuardiansTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * La schermata della Sveglia Intelligente: appare a TUTTO SCHERMO anche sopra
 * il blocco schermo, in stile vetro ghiacciato. "SPEGNI" richiede la pressione
 * PROLUNGATA (anti-errore da addormentati); "RIMANDA" fa slittare di 9 minuti.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsRepository.load(this)
        // Sopra il lock screen, con lo schermo che si accende da solo.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        try {
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        } catch (_: Throwable) {
        }

        setContent {
            GuardiansTheme(darkTheme = true) {
                AlarmScreen(
                    onDismiss = {
                        stopAlarm()
                        finish()
                    },
                    onSnooze = {
                        stopAlarm()
                        SmartAlarmRepository.scheduleAt(
                            this, System.currentTimeMillis() + 9L * 60_000L,
                        )
                        finish()
                    },
                )
            }
        }
    }

    /** Cancella la notifica insistente (il suono si ferma) e azzera la sveglia. */
    private fun stopAlarm() {
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(AlarmReceiver.NOTIFICATION_ID)
        } catch (_: Throwable) {
        }
        SmartAlarmRepository.cancel(this)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmScreen(onDismiss: () -> Unit, onSnooze: () -> Unit) {
    val ice = Color(0xFFE0F7FA)
    // Orologio vivo.
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            kotlinx.coroutines.delay(1000L)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14)),
        contentAlignment = Alignment.Center,
    ) {
        // Pannello "vetro ghiacciato" centrale.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0x14E0F7FA))
                .border(1.dp, Color(0x99E0F7FA), RoundedCornerShape(32.dp))
                .padding(horizontal = 24.dp, vertical = 36.dp),
        ) {
            Text(
                tr("Sveglia", "Wake up"),
                style = MaterialTheme.typography.titleMedium,
                color = ice,
            )
            Text(
                now.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                tr(
                    "Fine dei cicli di sonno: alzarsi adesso è più leggero.",
                    "Sleep cycles complete: getting up now feels lighter.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = ice.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))

            // SPEGNI: solo pressione PROLUNGATA (anti-errore).
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .combinedClickable(
                        onClick = { /* il tocco singolo non spegne: anti-errore */ },
                        onLongClick = onDismiss,
                    ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        tr("SPEGNI", "STOP"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10141F),
                    )
                    Text(
                        tr("tieni premuto", "hold to stop"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xCC10141F),
                    )
                }
            }

            TextButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) {
                Text(
                    tr("RIMANDA (9 minuti)", "SNOOZE (9 minutes)"),
                    color = ice,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
