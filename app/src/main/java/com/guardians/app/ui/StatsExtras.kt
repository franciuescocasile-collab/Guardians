package com.guardians.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guardians.app.data.ProfileRepository
import com.guardians.app.data.UsageHistoryRepository
import com.guardians.app.data.tr
import com.guardians.app.model.formatMs
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/** Periodo mostrato nel 2° livello delle Statistiche. */
enum class StatsPeriod { WEEK, MONTH, YEAR }

/** "2h"/"45m": etichetta compatta sopra le barre. */
private fun shortLabel(ms: Long): String {
    val min = ms / 60_000L
    return if (min >= 60) "${min / 60}h" else "${min}m"
}

@Composable
fun PeriodSelector(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            StatsPeriod.WEEK to tr("Settimana", "Week"),
            StatsPeriod.MONTH to tr("Mese", "Month"),
            StatsPeriod.YEAR to tr("Anno", "Year"),
        ).forEach { (p, label) ->
            FilterChip(
                selected = selected == p,
                onClick = { onSelect(p) },
                label = { Text(label) },
            )
        }
    }
}

/** Le 24 ore di un GIORNO qualsiasi (oggi, ieri, l'altro ieri…). */
fun hourlySeriesFor(
    context: android.content.Context,
    date: LocalDate,
): List<Pair<String, Long>> {
    val z = java.time.ZoneId.systemDefault()
    val dayStart = date.atStartOfDay(z).toInstant().toEpochMilli()
    val hours = com.guardians.app.data.UsageAnalytics.hourlyTotals(context, dayStart)
    // Un'etichetta per ogni ora (0..23), su una sola riga.
    return (0..23).map { h -> "$h" to hours[h] }
}

/** Le 24 ore di oggi (scorciatoia). */
fun hourlySeries(context: android.content.Context): List<Pair<String, Long>> =
    hourlySeriesFor(context, LocalDate.now())

/**
 * MEDIA per ORA del giorno sulle ultime [days] giornate (default 28 = 4
 * settimane): per ogni fascia oraria, quanto in media hai usato il telefono.
 * Serve al grafico "Media giornaliera" (2): l'andamento tipico della giornata.
 */
fun hourlyAverageSeries(
    context: android.content.Context,
    days: Int = 28,
): List<Pair<String, Long>> {
    val z = java.time.ZoneId.systemDefault()
    // NIENTE dati da PRIMA dell'installazione (domanda 1): non si scende sotto
    // la data del primo uso, anche se Android conserva lo storico più indietro.
    val firstUse = try {
        LocalDate.parse(com.guardians.app.data.ProfileRepository.firstUseDate.value)
    } catch (_: Exception) {
        LocalDate.now()
    }
    val totals = LongArray(24)
    var counted = 0
    for (back in 0 until days) {
        val date = LocalDate.now().minusDays(back.toLong())
        if (date.isBefore(firstUse)) break
        val dayStart = date.atStartOfDay(z).toInstant().toEpochMilli()
        val h = com.guardians.app.data.UsageAnalytics.hourlyTotals(context, dayStart)
        for (i in 0..23) totals[i] += h[i]
        counted++
    }
    val div = counted.coerceAtLeast(1)
    return (0..23).map { hour -> "$hour" to (totals[hour] / div) }
}

/**
 * UTILIZZO della settimana a [weeksBack] settimane fa (0 = questa): un giorno
 * per barra, dal primo giorno impostato. Futuro = vuoto.
 */
fun weekUsageSeries(history: Map<String, Long>, weeksBack: Int): List<Pair<String, Long>> {
    val locale = if (com.guardians.app.data.SettingsRepository.english.value) Locale.ENGLISH
    else Locale.ITALIAN
    val start = currentWeekDates().first().minusWeeks(weeksBack.toLong())
    return (0..6).map { i ->
        val date = start.plusDays(i.toLong())
        val label = date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale)
        label to (history[date.toString()] ?: 0L)
    }
}

/** UTILIZZO TOTALE delle ultime 5 settimane di calendario (etichette S#). */
fun weeksTotalsSeries(history: Map<String, Long>): List<Pair<String, Long>> {
    val firstDow = if (com.guardians.app.data.SettingsRepository.weekStartMonday.value) {
        java.time.DayOfWeek.MONDAY
    } else {
        java.time.DayOfWeek.SUNDAY
    }
    val weekFields = java.time.temporal.WeekFields.of(firstDow, 1)
    val thisWeekStart = currentWeekDates().first()
    return (4 downTo 0).map { w ->
        val start = thisWeekStart.minusWeeks(w.toLong())
        val total = (0..6).sumOf { i -> history[start.plusDays(i.toLong()).toString()] ?: 0L }
        "S${start.get(weekFields.weekOfYear())}" to total
    }
}

/**
 * L'anno [yearsBack] anni fa (0 = corrente), Gennaio → Dicembre. [totals]
 * true = somma del mese (Utilizzo), false = media giornaliera (Media).
 */
fun yearSeries(
    history: Map<String, Long>,
    yearsBack: Int,
    totals: Boolean,
): List<Pair<String, Long>> {
    val locale = if (com.guardians.app.data.SettingsRepository.english.value) Locale.ENGLISH
    else Locale.ITALIAN
    val year = LocalDate.now().year - yearsBack
    return (1..12).map { m ->
        val vals = history.entries.mapNotNull { (k, v) ->
            val d = try { LocalDate.parse(k) } catch (_: Exception) { null }
            if (d != null && d.year == year && d.monthValue == m) v else null
        }
        val value = when {
            vals.isEmpty() -> 0L
            totals -> vals.sum()
            else -> vals.sum() / vals.size
        }
        YearMonth.of(year, m).month.getDisplayName(TextStyle.NARROW, locale) to value
    }
}

/**
 * MEDIA per giorno della settimana sulle ULTIME 4 SETTIMANE (28 giorni):
 * lunedì in media X, martedì Y… nell'ordine del primo giorno impostato.
 */
fun dowAverage4wSeries(history: Map<String, Long>): List<Pair<String, Long>> {
    val locale = if (com.guardians.app.data.SettingsRepository.english.value) Locale.ENGLISH
    else Locale.ITALIAN
    val today = LocalDate.now()
    val sums = HashMap<java.time.DayOfWeek, Pair<Long, Int>>()
    for (back in 0..27) {
        val d = today.minusDays(back.toLong())
        val v = history[d.toString()] ?: continue
        val cur = sums[d.dayOfWeek] ?: (0L to 0)
        sums[d.dayOfWeek] = (cur.first + v) to (cur.second + 1)
    }
    return currentWeekDates().map { date ->
        val s = sums[date.dayOfWeek]
        val label = date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale)
        label to if (s == null || s.second == 0) 0L else s.first / s.second
    }
}

/**
 * I 7 giorni della SETTIMANA CORRENTE (dal primo giorno scelto nelle
 * impostazioni). I giorni FUTURI restano vuoti — è giusto così: devono
 * ancora succedere (3).
 */
fun currentWeekDates(): List<LocalDate> {
    val today = LocalDate.now()
    val firstDow = if (com.guardians.app.data.SettingsRepository.weekStartMonday.value) {
        java.time.DayOfWeek.MONDAY
    } else {
        java.time.DayOfWeek.SUNDAY
    }
    var start = today
    while (start.dayOfWeek != firstDow) start = start.minusDays(1)
    return (0..6).map { start.plusDays(it.toLong()) }
}

/** La settimana corrente, un giorno per barra (futuri = vuoti). */
fun last7DaysSeries(history: Map<String, Long>): List<Pair<String, Long>> {
    val locale = if (com.guardians.app.data.SettingsRepository.english.value) Locale.ENGLISH
    else Locale.ITALIAN
    return currentWeekDates().map { date ->
        val label = date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale)
        label to (history[date.toString()] ?: 0L)
    }
}

/**
 * Le ultime ~5 settimane (media giornaliera), etichettate "S1".."S52/53":
 * il numero della settimana nell'anno (S1 = la prima di gennaio), che si
 * azzera a ogni anno nuovo. Rispetta il primo giorno scelto (lun/dom).
 */
fun weeksSeries(history: Map<String, Long>): List<Pair<String, Long>> {
    val firstDow = if (com.guardians.app.data.SettingsRepository.weekStartMonday.value) {
        java.time.DayOfWeek.MONDAY
    } else {
        java.time.DayOfWeek.SUNDAY
    }
    val weekFields = java.time.temporal.WeekFields.of(firstDow, 1)
    // Settimane di CALENDARIO: le 4 passate + quella corrente (in corso).
    val thisWeekStart = currentWeekDates().first()
    return (4 downTo 0).map { w ->
        val start = thisWeekStart.minusWeeks(w.toLong())
        val days = (0..6).mapNotNull { i ->
            history[start.plusDays(i.toLong()).toString()]
        }
        val avg = if (days.isEmpty()) 0L else days.sum() / days.size
        "S${start.get(weekFields.weekOfYear())}" to avg
    }
}

/**
 * L'ANNO CORRENTE, Gennaio → Dicembre (media giornaliera per mese). I mesi
 * futuri restano vuoti — devono ancora succedere (3).
 */
fun yearlySeries(history: Map<String, Long>): List<Pair<String, Long>> {
    val locale = if (com.guardians.app.data.SettingsRepository.english.value) Locale.ENGLISH
    else Locale.ITALIAN
    val year = LocalDate.now().year
    return (1..12).map { m ->
        val ym = YearMonth.of(year, m)
        val vals = history.entries.mapNotNull { (k, v) ->
            val d = try { LocalDate.parse(k) } catch (_: Exception) { null }
            if (d != null && d.year == ym.year && d.monthValue == ym.monthValue) v else null
        }
        val avg = if (vals.isEmpty()) 0L else vals.sum() / vals.size
        ym.month.getDisplayName(TextStyle.NARROW, locale) to avg
    }
}

/**
 * Sceglie il passo delle righe orizzontali di riferimento in base al massimo,
 * così ci sono ~3-5 righe leggibili (2h, 4h, 8h… o giorni per i totali grossi).
 */
private fun gridStepMs(maxMs: Long): Long {
    val candidates = listOf(
        1L, 2L, 4L, 8L, 12L, 24L, 48L, 96L, 168L, 336L, 720L,
    ).map { it * 3_600_000L }
    return candidates.firstOrNull { maxMs / it <= 5 } ?: candidates.last()
}

/** Etichetta di una riga di riferimento: "2h", "4h" o "3g" per i giorni. */
private fun gridLabel(ms: Long): String {
    val h = ms / 3_600_000L
    return if (h >= 24 && h % 24 == 0L) "${h / 24}g" else "${h}h"
}

/** Grafico a barre generico (periodo), con linea obiettivo tratteggiata opzionale. */
@Composable
fun PeriodBarChart(series: List<Pair<String, Long>>, goalMs: Long) {
    if (series.isEmpty()) return
    // Niente dati → niente grafico vuoto, ma un messaggio pulito.
    if (series.all { it.second <= 0L }) {
        NoDataYet()
        return
    }
    val maxMs = (series.maxOf { it.second }.coerceAtLeast(goalMs)).coerceAtLeast(1L)
    val goalColor = Color(0xFF90A4AE).copy(alpha = 0.65f)
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val bandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
    val refColor = MaterialTheme.colorScheme.onSurfaceVariant
    val topLabelPad = 16f  // spazio riservato in cima per il minutaggio
    val barH = 134f + topLabelPad
    // Spazio a SINISTRA per le etichette orarie (stat 1: "fai spazio").
    val leftPad = 30f
    val n = series.size
    // Sopra le 100 ore l'etichetta "150h" non ci sta: mostriamo solo il numero
    // e la nota "in ore" in alto (stat 3).
    val hoursOnly = maxMs >= 100L * 3_600_000L
    // Barra selezionata col tocco → tooltip col tempo PRECISO (stat 9).
    var selected by remember(series) { mutableStateOf(-1) }
    Column {
        // Tooltip: quando tocchi una barra, il tempo esatto di quel periodo.
        Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
            if (selected in series.indices) {
                Text(
                    "${series[selected].first} · ${formatMs(series[selected].second)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (hoursOnly) {
                Text(
                    tr("valori in ore · tocca una barra", "values in hours · tap a bar"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    tr("tocca una barra per il dettaglio", "tap a bar for details"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(barH.dp),
        ) {
            // SFONDO: bande di colonna alternate ALLINEATE alle barre + righe
            // orarie di riferimento con etichetta a sinistra (stat 1: lo sfondo
            // non è più sfalsato, colW è lo stesso delle barre).
            Canvas(Modifier.fillMaxSize()) {
                val plotTop = topLabelPad
                val plotH = size.height - plotTop
                val colAreaW = size.width - leftPad
                val colW = colAreaW / n
                for (i in 0 until n step 2) {
                    drawRect(
                        color = bandColor,
                        topLeft = Offset(leftPad + colW * i, plotTop),
                        size = androidx.compose.ui.geometry.Size(colW, plotH),
                    )
                }
                val step = gridStepMs(maxMs)
                val labelPaint = android.graphics.Paint().apply {
                    color = refColor.copy(alpha = 0.7f).toArgb()
                    textSize = 9.dp.toPx()
                    isAntiAlias = true
                }
                var v = step
                while (v < maxMs) {
                    val frac = (v.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
                    val y = plotTop + plotH * (1f - frac)
                    drawLine(
                        color = gridColor,
                        start = Offset(leftPad, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5f,
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        gridLabel(v), 2.dp.toPx(), y - 2.dp.toPx(), labelPaint,
                    )
                    v += step
                }
            }
            // Area barre: NIENTE spaziatura tra le colonne, così ogni barra sta
            // esattamente dentro la sua banda di sfondo (stat 1).
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = leftPad.dp, top = topLabelPad.dp),
            ) {
                series.forEachIndexed { i, pair ->
                    val ms = pair.second
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember {
                                    androidx.compose.foundation.interaction.MutableInteractionSource()
                                },
                                indication = null,
                            ) { selected = if (selected == i) -1 else i },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (ms > 0L) {
                            val frac = (ms.toFloat() / maxMs.toFloat()).coerceIn(0.02f, 1f)
                            Box(
                                Modifier.fillMaxWidth(0.7f).fillMaxHeight(frac),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (selected == i) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                barColor
                                            },
                                            RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                                        )
                                )
                                Text(
                                    if (hoursOnly) "${ms / 3_600_000L}" else shortLabel(ms),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = barColor,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.offset(y = (-14).dp),
                                )
                            }
                        }
                    }
                }
            }
            // Linea obiettivo tratteggiata, sopra le barre (stessa scala delle barre).
            if (goalMs > 0L) {
                val goalFrac = (goalMs.toFloat() / maxMs.toFloat()).coerceIn(0.02f, 1f)
                Canvas(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = leftPad.dp, bottom = ((barH - topLabelPad) * goalFrac).dp)
                        .fillMaxWidth()
                        .height(2.dp),
                ) {
                    drawLine(
                        color = goalColor,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = size.height,
                        pathEffect = androidx.compose.ui.graphics.PathEffect
                            .dashPathEffect(floatArrayOf(14f, 12f)),
                    )
                }
            }
            // Asse di base allineato.
            Canvas(Modifier.align(Alignment.BottomStart).padding(start = leftPad.dp).fillMaxWidth().height(1.dp)) {
                drawLine(gridColor, Offset(0f, 0f), Offset(size.width, 0f), 2f)
            }
        }
        Spacer(Modifier.height(4.dp))
        // Etichette a una sola riga sotto ogni barra (senza spaziatura: allineate).
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = leftPad.dp),
        ) {
            series.forEach { (label, _) ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Un modo di vedere i dati dentro una sezione (Utilizzo o Media). */
private enum class ChartMode { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * Grafico con VIAGGIO NEL TEMPO (2.5): trascina verso DESTRA per andare
 * indietro nel tempo, verso sinistra per tornare avanti. [maxBack] = quante
 * volte si può andare indietro (0 = niente swipe). [label] descrive il periodo
 * mostrato, [chart] lo disegna.
 */
@Composable
private fun TimeTravelChart(
    maxBack: Int,
    label: (Int) -> String,
    chart: @Composable (Int) -> Unit,
) {
    var back by remember(maxBack) { mutableStateOf(0) }
    // Direzione dell'animazione: +1 = indietro nel tempo (entra da sinistra).
    var dir by remember { mutableStateOf(0) }
    Column {
        // Etichetta del periodo + frecce (le frecce aiutano chi non scopre lo swipe).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(
                onClick = { if (back < maxBack) { dir = 1; back++ } },
                enabled = maxBack > 0 && back < maxBack,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = tr("Indietro nel tempo", "Back in time"),
                    tint = if (maxBack > 0 && back < maxBack) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    },
                )
            }
            Text(
                label(back),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { if (back > 0) { dir = -1; back-- } },
                enabled = back > 0,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = tr("Avanti nel tempo", "Forward in time"),
                    tint = if (back > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Il grafico, con swipe orizzontale per il viaggio nel tempo. Lo swipe
        // cambia lo stato una sola volta per gesto (una soglia + un flag).
        var handled by remember { mutableStateOf(false) }
        val swipe = Modifier.pointerInput(maxBack) {
            detectHorizontalDragGestures(
                onDragStart = { handled = false },
                onDragEnd = { handled = false },
            ) { change, dragAmount ->
                change.consume()
                if (!handled) {
                    if (dragAmount > 24f && back < maxBack) { dir = 1; back++; handled = true }
                    else if (dragAmount < -24f && back > 0) { dir = -1; back--; handled = true }
                }
            }
        }
        AnimatedContent(
            targetState = back,
            transitionSpec = {
                val d = if (dir != 0) dir else 1
                (slideInHorizontally { w -> -d * w } + fadeIn()) togetherWith
                    (slideOutHorizontally { w -> d * w } + fadeOut())
            },
            label = "timeTravel",
            modifier = swipe,
        ) { b ->
            chart(b)
        }
    }
}

/** Le tre/quattro linguette di scelta della modalità, in stile "segmenti". */
@Composable
private fun ModeTabs(
    modes: List<Pair<ChartMode, String>>,
    selected: ChartMode,
    onSelect: (ChartMode) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        modes.forEach { (mode, label) ->
            val active = mode == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(mode) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 3° livello — "Tempo al telefono", rivoluzionato (2.1-2.5): due sezioni,
 * UTILIZZO (totali) sopra e MEDIA sotto, ognuna con le sue modalità e il
 * viaggio nel tempo. Sotto, il calendario dell'obiettivo.
 */
@Composable
fun StatsTimeDetail(onBack: () -> Unit) {
    val context = LocalContext.current
    UsageHistoryRepository.load(context)
    UsageHistoryRepository.loadGoalSnapshots(context)
    androidx.activity.compose.BackHandler { onBack() }

    val history by UsageHistoryRepository.history.collectAsState()
    val snapshots by UsageHistoryRepository.goalSnapshots.collectAsState()

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
                tr("Tempo al telefono", "Screen time"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        // ============================================ SEZIONE UTILIZZO (totali)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text(tr("Utilizzo", "Usage"), fontWeight = FontWeight.Bold)
                Text(
                    tr(
                        "Quanto hai usato il telefono in totale.",
                        "How much you used your phone in total.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                var mode by remember { mutableStateOf(ChartMode.DAILY) }
                ModeTabs(
                    modes = listOf(
                        ChartMode.DAILY to tr("Giornaliero", "Daily"),
                        ChartMode.WEEKLY to tr("Settimanale", "Weekly"),
                        ChartMode.MONTHLY to tr("Mensile", "Monthly"),
                        ChartMode.YEARLY to tr("Annuale", "Yearly"),
                    ),
                    selected = mode,
                    onSelect = { mode = it },
                )
                Spacer(Modifier.height(12.dp))
                when (mode) {
                    ChartMode.DAILY -> TimeTravelChart(
                        maxBack = 2,
                        label = { b ->
                            when (b) {
                                0 -> tr("Oggi", "Today")
                                1 -> tr("Ieri", "Yesterday")
                                else -> tr("L'altro ieri", "Two days ago")
                            }
                        },
                    ) { b ->
                        HourLineChart(
                            hourlySeriesFor(context, LocalDate.now().minusDays(b.toLong())),
                            cutAtNow = b == 0,
                        )
                    }

                    ChartMode.WEEKLY -> TimeTravelChart(
                        maxBack = 1,
                        label = { b ->
                            if (b == 0) tr("Questa settimana", "This week")
                            else tr("Settimana scorsa", "Last week")
                        },
                    ) { b ->
                        PeriodBarChart(weekUsageSeries(history, b), 0L)
                    }

                    ChartMode.MONTHLY -> {
                        Text(
                            tr("Ultime 5 settimane", "Last 5 weeks"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        )
                        PeriodBarChart(weeksTotalsSeries(history), 0L)
                    }

                    ChartMode.YEARLY -> TimeTravelChart(
                        maxBack = 2,
                        label = { b -> "${LocalDate.now().year - b}" },
                    ) { b ->
                        PeriodBarChart(yearSeries(history, b, totals = true), 0L)
                    }
                }
            }
        }

        // ================================================ SEZIONE MEDIA
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text(tr("Media", "Average"), fontWeight = FontWeight.Bold)
                Text(
                    tr(
                        "La media giornaliera nel periodo.",
                        "Your daily average over the period.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                var mode by remember { mutableStateOf(ChartMode.DAILY) }
                ModeTabs(
                    modes = listOf(
                        ChartMode.DAILY to tr("Giornaliero", "Daily"),
                        ChartMode.WEEKLY to tr("Settimanale", "Weekly"),
                        ChartMode.MONTHLY to tr("Mensile", "Monthly"),
                        ChartMode.YEARLY to tr("Annuale", "Yearly"),
                    ),
                    selected = mode,
                    onSelect = { mode = it },
                )
                Spacer(Modifier.height(12.dp))
                when (mode) {
                    // Media GIORNALIERA (2): la linea dell'andamento tipico della
                    // giornata, media per ora sulle ultime 4 settimane.
                    ChartMode.DAILY -> {
                        Text(
                            tr(
                                "Andamento medio della giornata · ultime 4 settimane",
                                "Typical day pattern · last 4 weeks",
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        )
                        val avgHourly by androidx.compose.runtime.produceState<List<Pair<String, Long>>?>(
                            initialValue = null,
                        ) {
                            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                hourlyAverageSeries(context)
                            }
                        }
                        val data = avgHourly
                        if (data == null) {
                            Box(
                                Modifier.fillMaxWidth().height(150.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(tr("…", "…")) }
                        } else {
                            HourLineChart(data, cutAtNow = false)
                        }
                    }

                    ChartMode.WEEKLY -> {
                        Text(
                            tr("Media per giorno · ultime 4 settimane", "Per-day avg · last 4 weeks"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        )
                        PeriodBarChart(dowAverage4wSeries(history), 0L)
                    }

                    ChartMode.MONTHLY -> {
                        Text(
                            tr("Media a settimana", "Weekly average"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        )
                        PeriodBarChart(weeksSeries(history), 0L)
                    }

                    ChartMode.YEARLY -> TimeTravelChart(
                        maxBack = 2,
                        label = { b -> "${LocalDate.now().year - b}" },
                    ) { b ->
                        PeriodBarChart(yearSeries(history, b, totals = false), 0L)
                    }
                }
            }
        }

        // -------------------------------------------- calendario dell'obiettivo
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    tr("Calendario dell'obiettivo", "Goal calendar"),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tr(
                        "Verde = giorno sotto l'obiettivo, rosso = sopra. Ogni " +
                            "giorno è un'istantanea: cambiare l'obiettivo non " +
                            "ricolora il passato.",
                        "Green = day under the goal, red = over. Each day is a " +
                            "snapshot: changing the goal doesn't recolor the past.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                var shownMonth by remember { mutableStateOf(YearMonth.now()) }
                val goalMin = ProfileRepository.dailyGoalMinutes.collectAsState().value
                val goalValues by UsageHistoryRepository.goalValues.collectAsState()
                GoalCalendar(
                    snapshots, history, goalValues, goalMin, shownMonth,
                ) { shownMonth = it }
            }
        }
    }
}

/** Messaggio "nessun dato ancora" al posto di un grafico vuoto. */
@Composable
private fun NoDataYet() {
    Box(
        Modifier.fillMaxWidth().height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            tr("Nessun dato per questo periodo", "No data for this period"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


/**
 * Grafico a LINEA delle ore di oggi (00 → 23; la fascia 23-00 è l'ultimo punto
 * a destra). Curve morbide e PALLINI TOCCABILI: un tocco mostra il commentino
 * con il minutaggio di quell'ora (uno solo alla volta; ritocca per chiudere).
 */
@Composable
private fun HourLineChart(series: List<Pair<String, Long>>, cutAtNow: Boolean = true) {
    if (series.isEmpty()) return
    if (series.all { it.second <= 0L }) { NoDataYet(); return }
    val maxMs = series.maxOf { it.second }.coerceAtLeast(1L)
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = 0.15f)
    val n = series.size
    // OGGI la curva si ferma all'ora corrente (il futuro è vuoto); per i
    // giorni PASSATI si vede l'intera giornata fino alle 23.
    val lastIdx = if (cutAtNow) {
        remember { java.time.LocalTime.now().hour }.coerceIn(0, n - 1)
    } else {
        n - 1
    }
    val chartH = 150.dp
    var selected by remember { mutableStateOf<Int?>(null) }
    Column {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(chartH),
        ) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = with(androidx.compose.ui.platform.LocalDensity.current) { chartH.toPx() }
            // Stessa geometria per disegno, tocchi e posizione del commentino.
            val stepX = wPx / (n - 1).coerceAtLeast(1)
            val bottomPad = hPx * 0.12f
            val topPad = hPx * 0.10f
            val usable = hPx - bottomPad - topPad
            fun pointAt(i: Int): Offset {
                val v = series[i].second.toFloat() / maxMs.toFloat()
                return Offset(stepX * i, topPad + usable * (1f - v.coerceIn(0f, 1f)))
            }

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(n, maxMs, lastIdx) {
                        detectTapGestures { off ->
                            val idx = kotlin.math.round(off.x / stepX).toInt()
                                .coerceIn(0, lastIdx)
                            selected = if (selected == idx) null else idx
                        }
                    },
            ) {
                // Solo le ore GIÀ vissute (0..lastIdx): il futuro resta vuoto.
                val pts = (0..lastIdx).map { pointAt(it) }
                val m = pts.size
                if (m >= 2) {
                    // Curva morbida di Catmull-Rom convertita in bezier cubica.
                    fun buildSmooth(path: Path) {
                        path.moveTo(pts[0].x, pts[0].y)
                        for (i in 0 until m - 1) {
                            val p0 = pts[if (i - 1 < 0) 0 else i - 1]
                            val p1 = pts[i]
                            val p2 = pts[i + 1]
                            val p3 = pts[if (i + 2 >= m) m - 1 else i + 2]
                            val c1x = p1.x + (p2.x - p0.x) / 6f
                            val c1y = p1.y + (p2.y - p0.y) / 6f
                            val c2x = p2.x - (p3.x - p1.x) / 6f
                            val c2y = p2.y - (p3.y - p1.y) / 6f
                            path.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
                        }
                    }
                    val fill = Path().apply {
                        buildSmooth(this)
                        lineTo(pts.last().x, size.height)
                        lineTo(pts.first().x, size.height)
                        close()
                    }
                    drawPath(fill, fillColor)
                    drawPath(
                        Path().apply { buildSmooth(this) },
                        lineColor, style = Stroke(width = 5f),
                    )
                }
                // Puntini; quello selezionato è evidenziato con un anello.
                pts.forEachIndexed { i, p ->
                    if (i == selected) {
                        drawCircle(lineColor.copy(alpha = 0.35f), radius = 14f, center = p)
                        drawCircle(lineColor, radius = 7f, center = p)
                    } else {
                        drawCircle(lineColor, radius = 5f, center = p)
                    }
                }
            }

            // Il commentino sopra il pallino toccato (uno solo alla volta).
            val sel = selected
            if (sel != null) {
                val p = pointAt(sel)
                val hour = series[sel].first.toIntOrNull() ?: sel
                val density = androidx.compose.ui.platform.LocalDensity.current
                val tipW = with(density) { 128.dp.toPx() }
                val tipH = with(density) { 48.dp.toPx() }
                val x = (p.x - tipW / 2f).coerceIn(0f, (wPx - tipW).coerceAtLeast(0f))
                val y = (p.y - tipH - 14f).coerceAtLeast(0f)
                Card(
                    onClick = { selected = null },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                    ),
                    modifier = Modifier.offset {
                        androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt())
                    },
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(
                            "%02d:00–%02d:00".format(hour, (hour + 1) % 24),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                        Text(
                            formatMs(series[sel].second),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        // Etichette asse X: un sottoinsieme per non affollare; l'ultima è
        // sempre "23" (la fascia 23-00, a destra come richiesto).
        val stepLabel = ((n + 6) / 7).coerceAtLeast(1)
        Row(modifier = Modifier.fillMaxWidth()) {
            series.forEachIndexed { i, (label, _) ->
                Text(
                    if (i % stepLabel == 0 || i == n - 1) label else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Il commentino di un giorno del calendario: uso, obiettivo e SOLO UNA tra
 * "hai sforato di" e "sei sotto l'obiettivo di" (mai entrambe).
 */
@Composable
private fun DayTooltip(
    date: LocalDate,
    usageMs: Long?,
    goalMinutes: Int,
    goalIsCurrent: Boolean = false,
    onClose: () -> Unit,
) {
    Card(
        onClick = onClose,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
        ),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                "${date.dayOfMonth}/${date.monthValue}/${date.year}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
            )
            if (usageMs == null) {
                Text(
                    tr("Nessun dato registrato", "No data recorded"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            } else {
                Text(
                    tr("Uso: ", "Usage: ") + formatMs(usageMs),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
                if (goalMinutes > 0) {
                    val goalMs = goalMinutes * 60_000L
                    Text(
                        (if (goalIsCurrent) tr("Obiettivo (attuale): ", "Goal (current): ")
                        else tr("Obiettivo: ", "Goal: ")) + formatMs(goalMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                    val diff = usageMs - goalMs
                    if (diff > 0L) {
                        Text(
                            tr("Hai sforato di ", "You went over by ") + formatMs(diff),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE57373),
                        )
                    } else {
                        Text(
                            tr("Sei sotto l'obiettivo di ", "You're under the goal by ") +
                                formatMs(-diff),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF66BB6A),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Griglia mensile con i pallini verdi/rossi; navigabile per mese. Toccando un
 * giorno appare un commentino (17) con uso, obiettivo e di quanto hai sforato
 * O di quanto sei sotto (mai entrambi). Un solo commentino alla volta.
 */
@Composable
private fun GoalCalendar(
    snapshots: Map<String, Boolean>,
    history: Map<String, Long>,
    goalValues: Map<String, Int>,
    goalMinutes: Int,
    ym: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    // Il giorno selezionato (chiave ISO): tocca di nuovo per chiudere.
    var selectedDay by remember { mutableStateOf<String?>(null) }
    val locale = if (com.guardians.app.data.SettingsRepository.english.value) Locale.ENGLISH
    else Locale.ITALIAN
    // Intestazione: frecce mese + titolo cliccabile (Month/Year picker rapido).
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        IconButton(onClick = { onMonthChange(ym.minusMonths(1)) }) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = tr("Mese precedente", "Previous month"),
            )
        }
        Text(
            ym.month.getDisplayName(TextStyle.FULL, locale)
                .replaceFirstChar { it.uppercase() } + " " + ym.year,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clickable {
                    // Picker rapido per salti storici diretti.
                    android.app.DatePickerDialog(
                        context,
                        { _, y, m, _ -> onMonthChange(YearMonth.of(y, m + 1)) },
                        ym.year, ym.monthValue - 1, 1,
                    ).show()
                },
        )
        IconButton(
            onClick = { if (ym.isBefore(YearMonth.now())) onMonthChange(ym.plusMonths(1)) },
            enabled = ym.isBefore(YearMonth.now()),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = tr("Mese successivo", "Next month"),
            )
        }
    }
    val first = ym.atDay(1)
    // Lun=1 … Dom=7; quante celle vuote prima del giorno 1.
    val lead = (first.dayOfWeek.value - 1)
    val cells = lead + ym.lengthOfMonth()
    val rows = (cells + 6) / 7
    val green = Color(0xFF4CAF50)
    val red = com.guardians.app.ui.theme.RedGuardiano
    val labels = if (com.guardians.app.data.SettingsRepository.english.value) {
        listOf("M", "T", "W", "T", "F", "S", "S")
    } else {
        listOf("L", "M", "M", "G", "V", "S", "D")
    }
    Column {
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Centrata come i numeri sotto: prima era a sinistra e sembrava
                    // sfalsata rispetto alle colonne (stat 9).
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val cellIndex = r * 7 + c
                    val dayNum = cellIndex - lead + 1
                    // Celle più basse (6.1): il calendario non deve sembrare enorme.
                    Box(
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dayNum in 1..ym.lengthOfMonth()) {
                            val date = ym.atDay(dayNum).toString()
                            val under = snapshots[date]
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clickable {
                                        selectedDay = if (selectedDay == date) null else date
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (under != null) {
                                    Box(
                                        Modifier
                                            .size(28.dp)
                                            .background(
                                                (if (under) green else red).copy(alpha = 0.28f),
                                                CircleShape,
                                            )
                                    )
                                }
                                Text(
                                    "$dayNum",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (date == today.toString()) FontWeight.Bold
                                    else FontWeight.Normal,
                                    color = when (under) {
                                        true -> green
                                        false -> red
                                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                // Il commentino del giorno (come la "i" del profilo).
                                if (selectedDay == date) {
                                    androidx.compose.ui.window.Popup(
                                        alignment = Alignment.TopCenter,
                                        offset = androidx.compose.ui.unit.IntOffset(0, -190),
                                        onDismissRequest = { selectedDay = null },
                                        properties = androidx.compose.ui.window.PopupProperties(
                                            focusable = false,
                                        ),
                                    ) {
                                        DayTooltip(
                                            date = ym.atDay(dayNum),
                                            usageMs = history[date],
                                            // L'obiettivo DI QUEL GIORNO (istantanea);
                                            // solo se mai registrato usa l'attuale.
                                            goalMinutes = goalValues[date] ?: goalMinutes,
                                            goalIsCurrent = goalValues[date] == null,
                                            onClose = { selectedDay = null },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
