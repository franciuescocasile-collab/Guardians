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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.toSize
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
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

            // La mezzaluna e i giorni restano SEMPRE visibili (sonno 2), anche
            // a sveglia attiva: così vedi sempre com'è impostata. Cambiare i
            // cicli mentre è attiva vale dalla prossima rilevazione.
            CrescentSlider(
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

            if (armed || scheduled) {
                // Banner di stato: programmata (con orario) o in ascolto.
                Text(
                    if (scheduled) {
                        tr("Ti sveglierò alle ", "I'll wake you at ") +
                            Instant.ofEpochMilli(alarmAt).atZone(zone).format(fmt)
                    } else {
                        tr("In ascolto del tuo sonno…", "Listening for your sleep…")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (scheduled) {
                        tr(
                            "Suonerà anche a schermo bloccato. \"Spegni\" richiede " +
                                "la pressione prolungata.",
                            "It rings even on the lock screen. \"Stop\" needs a long press.",
                        )
                    } else {
                        tr(
                            "Appena starai fermo col telefono spento per un po', " +
                                "partirà il conteggio di $cycles cicli.",
                            "As soon as you rest with the screen off for a while, " +
                                "the $cycles-cycle count will start.",
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { SmartAlarmRepository.cancel(context) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(tr("Disattiva la sveglia", "Turn off the alarm"), fontWeight = FontWeight.Bold)
                }
            } else {
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
 * Slider a MEZZALUNA per i cicli (3..7). Il pallino è GRANDE e si può anche
 * toccare direttamente il punto dell'arco che si vuole: così è facile da
 * "prendere" (sonno 1). Al centro luna, cicli e ore.
 */
@Composable
private fun CrescentSlider(cycles: Int, onChange: (Int) -> Unit) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val activeColor = MaterialTheme.colorScheme.primary
    // FLUIDO (sonno 1): mentre trascini, il pomello segue il DITO in modo
    // continuo (dragFrac); lo scatto sui 5 valori riguarda solo il numero.
    // A dito alzato il pomello si adagia dolcemente sulla tacca (animazione).
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val settledFrac by androidx.compose.animation.core.animateFloatAsState(
        targetValue = (cycles - 3) / 4f,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "crescentSettle",
    )
    val frac = dragFrac ?: settledFrac

    // Frazione CONTINUA (0..1) del punto toccato sulla mezzaluna; null se fuori.
    fun fracFrom(pos: Offset, size: androidx.compose.ui.geometry.Size): Float? {
        val cx = size.width / 2f
        val cy = size.height
        val deg = Math.toDegrees(atan2((pos.y - cy).toDouble(), (pos.x - cx).toDouble()))
        if (deg !in -180.0..0.0) return null
        return ((deg + 180.0) / 180.0).toFloat()
    }

    fun cyclesOf(f: Float): Int = 3 + (f * 4f).roundToInt().coerceIn(0, 4)

    Box(
        Modifier
            .fillMaxWidth()
            .height(170.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                // Trascinamento: consuma il gesto così NON fa scorrere la pagina.
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { p ->
                            fracFrom(p, size.toSize())?.let { f ->
                                dragFrac = f
                                onChange(cyclesOf(f))
                            }
                        },
                        onDragEnd = { dragFrac = null },
                        onDragCancel = { dragFrac = null },
                    ) { change, _ ->
                        change.consume()
                        fracFrom(change.position, size.toSize())?.let { f ->
                            dragFrac = f
                            onChange(cyclesOf(f))
                        }
                    }
                }
                // Tocco secco: imposta subito il valore del punto toccato.
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        fracFrom(p, size.toSize())?.let { onChange(cyclesOf(it)) }
                    }
                },
        ) {
            val cx = size.width / 2f
            val cy = size.height
            val radius = minOf(size.width / 2f, size.height) - 34f
            val rect = androidx.compose.ui.geometry.Rect(
                cx - radius, cy - radius, cx + radius, cy + radius,
            )
            // Binario spesso della mezzaluna.
            drawArc(
                color = track, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = rect.topLeft, size = rect.size,
                style = Stroke(width = 34f, cap = StrokeCap.Round),
            )
            drawArc(
                color = activeColor, startAngle = 180f, sweepAngle = 180f * frac,
                useCenter = false, topLeft = rect.topLeft, size = rect.size,
                style = Stroke(width = 34f, cap = StrokeCap.Round),
            )
            // Tacche dei 5 valori (3..7 cicli).
            for (i in 0..4) {
                val a = Math.toRadians(180.0 + i * 45.0)
                val tx = cx + (radius * cos(a)).toFloat()
                val ty = cy + (radius * sin(a)).toFloat()
                drawCircle(
                    color = if (i <= (cycles - 3)) activeColor else track,
                    radius = 6f, center = Offset(tx, ty),
                )
            }
            // Il POMELLO grosso: segue il dito in continuo mentre trascini.
            val ka = Math.toRadians(180.0 + frac * 180.0)
            val kx = cx + (radius * cos(ka)).toFloat()
            val ky = cy + (radius * sin(ka)).toFloat()
            drawCircle(androidx.compose.ui.graphics.Color.White, radius = 30f, center = Offset(kx, ky))
            drawCircle(activeColor, radius = 22f, center = Offset(kx, ky))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Icon(
                Icons.Default.Bedtime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Text(
                tr("$cycles cicli", "$cycles cycles"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "≈ " + formatMs(cycles * SmartAlarmRepository.CYCLE_MS) +
                    tr(" di sonno", " of sleep"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
