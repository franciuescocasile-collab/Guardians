package com.guardians.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guardians.app.data.AraldoData
import com.guardians.app.data.HealthConnectManager
import com.guardians.app.data.ProfileRepository
import com.guardians.app.data.SmartAlarmRepository
import com.guardians.app.data.tr
import com.guardians.app.model.TimerType
import com.guardians.app.model.formatMs
import com.guardians.app.model.formatTimeOfDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La pagina SONNO: tutto ciò che riguarda il riposo — la Sveglia Intelligente
 * a cicli di sonno, i dati di Health Connect (ultima dormita e Distacco→Sonno),
 * la settimana di sonno con il voto per notte, e l'Araldo coi suoi orari.
 */
@Composable
fun SleepScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    AraldoData.load(context)
    ProfileRepository.load(context)
    SmartAlarmRepository.load(context)
    val median by AraldoData.medianBedtimeMinute.collectAsState()
    val bedMinute by ProfileRepository.usualBedMinute.collectAsState()
    val wakeMinute by ProfileRepository.usualWakeMinute.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
            Text(
                tr("Sonno", "Sleep"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        // ------------------------------------------- SVEGLIA INTELLIGENTE (26)
        SmartAlarmCard()

        // Health Connect: ultima dormita + il legame Distacco → Sonno.
        SleepConnectCard()

        // --------------------------------- la settimana di sonno (voto + stacco)
        SleepWeekCard()

        // ------------------------------------------ L'ARALDO E IL SONNO
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimerShapeIcon(TimerType.ARALDO, Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        tr("L'Araldo e il tuo sonno", "The Herald and your sleep"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    if (median != null) {
                        tr(
                            "Nanna imparata: ~${formatTimeOfDay(median!!)}",
                            "Learned bedtime: ~${formatTimeOfDay(median!!)}",
                        )
                    } else {
                        tr(
                            "L'Araldo usa i tuoi orari indicativi e intanto impara i veri.",
                            "The Herald uses your indicative times while learning the real ones.",
                        )
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (median != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (median != null) FontWeight.Bold else FontWeight.Normal,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeOfDayPicker(
                        label = tr("A dormire verso le", "To sleep around"),
                        minuteOfDay = bedMinute.takeIf { it >= 0 },
                        onChange = { ProfileRepository.setUsualBedMinute(context, it) },
                        modifier = Modifier.weight(1f),
                    )
                    TimeOfDayPicker(
                        label = tr("Sveglia verso le", "Wake around"),
                        minuteOfDay = wakeMinute.takeIf { it >= 0 },
                        onChange = { ProfileRepository.setUsualWakeMinute(context, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * La Sveglia Intelligente: selettore a MEZZALUNA per i cicli di sonno
 * (~90 min l'uno). L'orario si calcola da ADESSO + ~15 min per addormentarsi,
 * così suona a fine ciclo, nel sonno leggero. Consigliati: 5 cicli (7h30)
 * o 6 cicli (9h). La schermata di sveglia appare anche sopra il blocco.
 */
@Composable
private fun SmartAlarmCard() {
    val context = LocalContext.current
    val cycles by SmartAlarmRepository.cycles.collectAsState()
    val alarmAt by SmartAlarmRepository.alarmAt.collectAsState()
    val armed by SmartAlarmRepository.armed.collectAsState()
    val scheduled = alarmAt > System.currentTimeMillis()
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    val zone = ZoneId.systemDefault()

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    tr("Sveglia intelligente", "Smart alarm"),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                tr(
                    "Scegli i CICLI di sonno (90 minuti l'uno). Poi vai a dormire " +
                        "quando vuoi: l'app si accorge da sola quando ti addormenti " +
                        "e ti sveglia a fine ciclo, nel sonno leggero. Consigliati: " +
                        "5 cicli (7h30) o 6 cicli (9h).",
                    "Pick your sleep CYCLES (90 min each). Then go to bed whenever " +
                        "you like: the app notices when you fall asleep and wakes " +
                        "you at the end of a cycle, in light sleep. Recommended: 5 " +
                        "cycles (7.5h) or 6 cycles (9h).",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            if (armed || scheduled) {
                if (scheduled) {
                    // Sonno rilevato: orario del risveglio calcolato.
                    Text(
                        tr("Ti sveglierò alle ", "I'll wake you at ") +
                            Instant.ofEpochMilli(alarmAt).atZone(zone).format(fmt),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        tr(
                            "Suonerà anche a schermo bloccato. \"Spegni\" richiede la " +
                                "pressione prolungata.",
                            "It rings even on the lock screen. \"Stop\" needs a long press.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // Armata, in attesa che ti addormenti.
                    Text(
                        tr("In ascolto del tuo sonno…", "Listening for your sleep…"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        tr(
                            "Appena starai fermo col telefono spento per un po', farò " +
                                "partire il conteggio di $cycles cicli.",
                            "As soon as you rest with the screen off for a while, I'll " +
                                "start counting $cycles cycles.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { SmartAlarmRepository.cancel(context) }) {
                    Text(tr("Disattiva la sveglia", "Turn off the alarm"))
                }
            } else {
                // Scelta dei CICLI con pulsanti tondi facili da toccare (12):
                // niente più slider che si scontra con lo scorrimento.
                CyclePicker(
                    cycles = cycles,
                    onChange = { SmartAlarmRepository.setCycles(context, it) },
                )
                Spacer(Modifier.height(10.dp))
                // Ripetizione per giorni (9). Vuoto = una-tantum.
                val alarmDays by SmartAlarmRepository.days.collectAsState()
                Text(
                    tr(
                        "Ripeti nei giorni (vuoto = una volta sola):",
                        "Repeat on days (empty = one-shot):",
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                WeekDaysEditor(alarmDays) { iso ->
                    val next = if (alarmDays.contains(iso)) alarmDays - iso
                    else alarmDays + iso
                    SmartAlarmRepository.setDays(context, next)
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { SmartAlarmRepository.arm(context) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(tr("Attiva la sveglia", "Set the alarm"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Scelta dei cicli di sonno con pulsanti tondi 3..7 (facili da toccare, non
 * si scontrano con lo scorrimento della pagina, 12). Sotto, luna e ore.
 */
@Composable
private fun CyclePicker(cycles: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            (3..7).forEach { n ->
                val on = n == cycles
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .background(
                            if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            androidx.compose.foundation.shape.CircleShape,
                        )
                        .clickable { onChange(n) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$n",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (on) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            tr("🌙 $cycles cicli", "🌙 $cycles cycles") +
                " ≈ " + formatMs(cycles * SmartAlarmRepository.CYCLE_MS),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * La settimana di sonno (domanda 3): per ogni giorno il VOTO della notte
 * (durata + fasi, 0..100) e sotto quanto avevi staccato il telefono prima di
 * addormentarti. Servono Health Connect collegato e qualche sera di dati.
 */
@Composable
private fun SleepWeekCard() {
    val context = LocalContext.current
    val nights by produceState<Map<LocalDate, HealthConnectManager.NightScore>>(
        initialValue = emptyMap(),
    ) {
        value = withContext(Dispatchers.IO) {
            if (HealthConnectManager.hasPermission(context)) {
                HealthConnectManager.weeklyNightScores(context)
            } else {
                emptyMap()
            }
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                tr("La tua settimana di sonno", "Your week of sleep"),
                fontWeight = FontWeight.Bold,
            )
            Text(
                tr(
                    "Il voto di ogni notte (durata + fasi, se lo smartwatch le " +
                        "misura) e, sotto, quanto avevi staccato il telefono prima " +
                        "di addormentarti.",
                    "Each night's score (duration + stages, if your smartwatch " +
                        "tracks them) and, below, how long you'd put the phone " +
                        "down before falling asleep.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (nights.isEmpty()) {
                Text(
                    tr(
                        "Ancora nessuna notte con dati: servono Health Connect " +
                            "collegato (vedi sopra) e qualche sera di raccolta.",
                        "No nights with data yet: you need Health Connect linked " +
                            "(see above) and a few evenings of collection.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SleepWeekChart(nights)
            }
        }
    }
}

@Composable
private fun SleepWeekChart(nights: Map<LocalDate, HealthConnectManager.NightScore>) {
    val locale = if (com.guardians.app.data.SettingsRepository.english.value) Locale.ENGLISH
    else Locale.ITALIAN
    val today = LocalDate.now()
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        (6 downTo 0).forEach { back ->
            val day = today.minusDays(back.toLong())
            val night = nights[day]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                // GIORNO in alto, chiaro (3): Lun, Mar, Mer…
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                        .replaceFirstChar { it.uppercase() }
                        .removeSuffix("."),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                // Colonna del voto: il numero è ATTACCATO alla cima della barra.
                Box(
                    Modifier.height(96.dp).fillMaxWidth(0.6f),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (night != null) {
                        val barColor = when {
                            night.score >= 75 -> Color(0xFF66BB6A)
                            night.score >= 50 -> MaterialTheme.colorScheme.primary
                            else -> Color(0xFFE57373)
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight((night.score / 100f).coerceIn(0.08f, 1f)),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        barColor,
                                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                    )
                            )
                            // Il voto, appena sopra la cima della barra.
                            Text(
                                night.score.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = barColor,
                                modifier = Modifier.offset(y = (-16).dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                // Lo stacco dal telefono di quella sera.
                Text(
                    night?.gapMin?.let { "$it min" } ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = if ((night?.gapMin ?: 0L) >= 30L) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}
