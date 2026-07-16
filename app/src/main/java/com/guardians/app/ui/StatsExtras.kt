package com.guardians.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
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

/** Le ultime 24 ore, ora per ora (per il grafico Giornaliero). */
fun hourlySeries(context: android.content.Context): List<Pair<String, Long>> {
    val z = java.time.ZoneId.systemDefault()
    val dayStart = LocalDate.now().atStartOfDay(z).toInstant().toEpochMilli()
    val hours = com.guardians.app.data.UsageAnalytics.hourlyTotals(context, dayStart)
    // Un'etichetta per ogni ora (0..23), su una sola riga.
    return (0..23).map { h -> "$h" to hours[h] }
}

/** Gli ultimi 7 giorni, uno per barra (per il grafico Settimanale). */
fun last7DaysSeries(history: Map<String, Long>): List<Pair<String, Long>> {
    val locale = if (com.guardians.app.data.SettingsRepository.english.value) Locale.ENGLISH
    else Locale.ITALIAN
    val today = LocalDate.now()
    return (6 downTo 0).map { d ->
        val date = today.minusDays(d.toLong())
        val label = date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale)
        label to (history[date.toString()] ?: 0L)
    }
}

/** Le ultime ~5 settimane (media giornaliera), etichettate con la data di fine. */
fun weeksSeries(history: Map<String, Long>): List<Pair<String, Long>> {
    val today = LocalDate.now()
    return (4 downTo 0).map { w ->
        val end = today.minusWeeks(w.toLong())
        val start = end.minusDays(6)
        val days = (0..6).mapNotNull { i ->
            history[start.plusDays(i.toLong()).toString()]
        }
        val avg = if (days.isEmpty()) 0L else days.sum() / days.size
        // Etichetta chiara: "sett. del d/M" abbreviata in "d/M".
        "${end.dayOfMonth}/${end.monthValue}" to avg
    }
}

/** Ultimi 12 mesi come barre (media giornaliera per mese). */
fun yearlySeries(history: Map<String, Long>): List<Pair<String, Long>> {
    val locale = if (com.guardians.app.data.SettingsRepository.english.value) Locale.ENGLISH
    else Locale.ITALIAN
    val now = YearMonth.now()
    return (11 downTo 0).map { m ->
        val ym = now.minusMonths(m.toLong())
        val vals = history.entries.mapNotNull { (k, v) ->
            val d = try { LocalDate.parse(k) } catch (_: Exception) { null }
            if (d != null && d.year == ym.year && d.monthValue == ym.monthValue) v else null
        }
        val avg = if (vals.isEmpty()) 0L else vals.sum() / vals.size
        ym.month.getDisplayName(TextStyle.NARROW, locale) to avg
    }
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
    val topLabelPad = 16f  // spazio riservato in cima per il minutaggio
    val barH = 90f + topLabelPad  // altezza totale area (barre + etichette valore)
    Column {
        // Area barre a ALTEZZA FISSA: tutte le basi sono allineate sullo stesso
        // asse (la riga inferiore). I valori sopra le barre sono in giallo.
        Box(
            Modifier
                .fillMaxWidth()
                .height(barH.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topLabelPad.dp),
            ) {
                series.forEach { (_, ms) ->
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                        if (ms > 0L) {
                            val frac = (ms.toFloat() / maxMs.toFloat()).coerceIn(0.02f, 1f)
                            // Il minutaggio sopra la barra.
                            Box(
                                Modifier.fillMaxWidth(0.7f).fillMaxHeight(frac),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            barColor,
                                            RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                                        )
                                )
                                Text(
                                    shortLabel(ms),
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
                        .padding(bottom = ((barH - topLabelPad) * goalFrac).dp)
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
            Canvas(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp)) {
                drawLine(gridColor, Offset(0f, 0f), Offset(size.width, 0f), 2f)
            }
        }
        Spacer(Modifier.height(4.dp))
        // Etichette a una sola riga sotto ogni barra.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
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

/**
 * 3° livello — "Tempo al telefono": grafico a LINEA con carosello a swipe
 * (giornaliero/settimanale/mensile) e calendario dell'obiettivo con istantanee
 * immutabili (verde sotto la soglia, rosso sopra).
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

        // ---------------------------------------------- carosello dei grafici
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                val titles = listOf(
                    tr("Giornaliero", "Daily"),
                    tr("Settimanale", "Weekly"),
                    tr("Mensile", "Monthly"),
                )
                val pager = rememberPagerState(pageCount = { 3 })
                var lineView by remember { mutableStateOf(true) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("Andamento", "Trend"),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    // Toggle SEGMENTATO barre/linea: si capisce che è un pulsante.
                    ChartTypeToggle(lineView) { lineView = it }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    titles.forEachIndexed { i, t ->
                        Text(
                            t,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (pager.currentPage == i) FontWeight.Bold
                            else FontWeight.Normal,
                            color = if (pager.currentPage == i) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalPager(state = pager) { page ->
                    val series = when (page) {
                        0 -> hourlySeries(context)          // ultime 24 ore
                        1 -> last7DaysSeries(history)        // ultimi 7 giorni
                        else -> weeksSeries(history)         // ultime settimane
                    }
                    if (lineView) LineChart(series) else PeriodBarChart(series, 0L)
                }
                Spacer(Modifier.height(10.dp))
                // Page indicators: 3 cerchi, quello attivo più grande e illuminato.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    for (i in 0 until 3) {
                        val active = pager.currentPage == i
                        Box(
                            Modifier
                                .size(if (active) 10.dp else 7.dp)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    CircleShape,
                                )
                        )
                    }
                    Spacer(Modifier.weight(1f))
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
                GoalCalendar(snapshots, shownMonth) { shownMonth = it }
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

/** Toggle segmentato barre/linea: una pillola con due icone, l'attiva evidenziata. */
@Composable
private fun ChartTypeToggle(lineView: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(50),
            )
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(false to Icons.Filled.BarChart, true to Icons.Filled.ShowChart).forEach { (line, icon) ->
            val active = lineView == line
            Box(
                modifier = Modifier
                    .clickable { onChange(line) }
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                        RoundedCornerShape(50),
                    )
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = if (line) tr("Linea", "Line") else tr("Barre", "Bars"),
                    tint = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Grafico a linea MORBIDA (curve) che segue l'andamento. */
@Composable
private fun LineChart(series: List<Pair<String, Long>>) {
    if (series.isEmpty()) return
    if (series.all { it.second <= 0L }) { NoDataYet(); return }
    val maxMs = series.maxOf { it.second }.coerceAtLeast(1L)
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = 0.15f)
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val n = series.size
            if (n < 2) return@Canvas
            val stepX = size.width / (n - 1)
            // Padding inferiore: la linea dello zero non deve toccare il bordo.
            val bottomPad = size.height * 0.12f
            val topPad = size.height * 0.10f
            val usable = size.height - bottomPad - topPad
            fun pt(i: Int): Offset {
                val v = series[i].second.toFloat() / maxMs.toFloat()
                return Offset(stepX * i, topPad + usable * (1f - v.coerceIn(0f, 1f)))
            }
            val pts = (0 until n).map { pt(it) }
            // Curva morbida di Catmull-Rom convertita in bezier cubica.
            fun buildSmooth(path: Path) {
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 0 until n - 1) {
                    val p0 = pts[if (i - 1 < 0) 0 else i - 1]
                    val p1 = pts[i]
                    val p2 = pts[i + 1]
                    val p3 = pts[if (i + 2 >= n) n - 1 else i + 2]
                    val c1x = p1.x + (p2.x - p0.x) / 6f
                    val c1y = p1.y + (p2.y - p0.y) / 6f
                    val c2x = p2.x - (p3.x - p1.x) / 6f
                    val c2y = p2.y - (p3.y - p1.y) / 6f
                    path.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
                }
            }
            // Riempimento sotto la curva.
            val fill = Path().apply {
                buildSmooth(this)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, fillColor)
            // La curva.
            val line = Path().apply { buildSmooth(this) }
            drawPath(line, lineColor, style = Stroke(width = 5f))
            // Puntini sui vertici.
            for (p in pts) drawCircle(lineColor, radius = 5f, center = p)
        }
        Spacer(Modifier.height(4.dp))
        // Etichette asse X: mostro un sottoinsieme per non affollare (max ~7).
        val n = series.size
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

/** Griglia mensile con i pallini verdi/rossi; navigabile per mese. */
@Composable
private fun GoalCalendar(
    snapshots: Map<String, Boolean>,
    ym: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
) {
    val context = LocalContext.current
    val today = LocalDate.now()
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
                    Box(
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dayNum in 1..ym.lengthOfMonth()) {
                            val date = ym.atDay(dayNum).toString()
                            val under = snapshots[date]
                            Box(
                                Modifier.size(30.dp),
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
                            }
                        }
                    }
                }
            }
        }
    }
}
