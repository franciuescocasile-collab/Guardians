package com.guardians.app.ui

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guardians.app.data.StatsRepository
import com.guardians.app.data.UsageHistoryRepository
import com.guardians.app.data.tr
import com.guardians.app.model.TimerType
import com.guardians.app.model.formatMs
import com.guardians.app.ui.theme.RedGuardiano
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.guardians.app.data.HealthConnectManager

/** Un'app tra le più usate: nome, tempo, icona vera e colore della categoria. */
private class TopApp(
    val label: String,
    val ms: Long,
    val icon: androidx.compose.ui.graphics.ImageBitmap?,
    /** Colore ARGB della categoria (il pallino accanto all'icona). */
    val categoryColor: Long,
)

/** Riepilogo dell'uso del telefono letto da UsageStatsManager. */
private data class UsageSummary(
    val todayMs: Long,
    /** Le app più usate oggi (già ordinate, max 4), con la loro icona. */
    val topAppsToday: List<TopApp>,
    /** TUTTE le app usate oggi (ordinate desc), per la pagina di dettaglio. */
    val allAppsToday: List<TopApp>,
    /** Un elemento per giorno, dal più vecchio a oggi: iniziale del giorno -> ms. */
    val last7Days: List<Pair<String, Long>>,
    /** Categoria -> millisecondi di oggi (ordinate desc). */
    val categoriesToday: List<Pair<com.guardians.app.model.AppCategory, Long>>,
    val weekMs: Long,
    val monthMs: Long,
    val yearMs: Long,
)

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val usage by produceState<UsageSummary?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            UsageHistoryRepository.load(context)
            loadUsageSummary(context)
        }
    }

    // 3° livello: andamento del tempo + calendario dell'obiettivo.
    var showTimeDetail by remember { mutableStateOf(false) }
    // Dettaglio: l'uso di OGNI app di oggi (14.1).
    var showAppsDetail by remember { mutableStateOf(false) }
    // Selettore di periodo del 2° livello.
    var period by remember { mutableStateOf(StatsPeriod.WEEK) }

    if (showAppsDetail) {
        AllAppsDetail(
            apps = usage?.allAppsToday.orEmpty(),
            onBack = { showAppsDetail = false },
        )
        return
    }

    if (showTimeDetail) {
        StatsTimeDetail(onBack = { showTimeDetail = false })
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                }
                Column {
                    Text(
                        tr("Statistiche", "Statistics"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        tr("Il tuo telefono e i tuoi guardiani", "Your phone and your guardians"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ------------------------------------------------ uso del telefono
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("Tempo al telefono", "Screen time"),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        // Ingresso al 3° livello: andamento + calendario obiettivo.
                        TextButton(onClick = { showTimeDetail = true }) {
                            Text(tr("Dettaglio", "Details"))
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val u = usage
                    when {
                        u == null -> Box(
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }

                        !hasUsageAccess(context) -> Text(
                            tr(
                                "Serve il permesso \"Accesso ai dati di utilizzo\" " +
                                    "(vedi schermata principale).",
                                "The \"Usage access\" permission is required " +
                                    "(see the main screen).",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        else -> {
                            Text(
                                tr("Oggi: ", "Today: ") + formatMs(u.todayMs),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(12.dp))
                            // Selettore di periodo: Settimana / Mese / Anno.
                            PeriodSelector(period) { period = it }
                            Spacer(Modifier.height(12.dp))
                            val histAvg = UsageHistoryRepository.dailyAverageMs()
                            val goalMs = com.guardians.app.data.ProfileRepository
                                .dailyGoalMinutes.collectAsState().value * 60_000L
                            when (period) {
                                StatsPeriod.WEEK -> WeekBarChart(u.last7Days, histAvg, goalMs)
                                StatsPeriod.MONTH -> PeriodBarChart(
                                    weeksSeries(UsageHistoryRepository.history.value), goalMs,
                                )
                                StatsPeriod.YEAR -> PeriodBarChart(
                                    yearlySeries(UsageHistoryRepository.history.value), 0L,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            // Solo il dato del periodo ATTIVO (niente elenco cumulativo).
                            // I campi si nascondono se mancano dati storici.
                            val periodMs = when (period) {
                                StatsPeriod.WEEK -> u.weekMs
                                StatsPeriod.MONTH -> u.monthMs
                                StatsPeriod.YEAR -> u.yearMs
                            }
                            val periodDays = when (period) {
                                StatsPeriod.WEEK -> LocalDate.now().dayOfWeek.value
                                StatsPeriod.MONTH -> 30
                                StatsPeriod.YEAR -> null
                            }
                            if (periodMs > 0L) {
                                SummaryRow(
                                    when (period) {
                                        StatsPeriod.WEEK -> tr("Questa settimana", "This week")
                                        StatsPeriod.MONTH -> tr("Ultimi 30 giorni", "Last 30 days")
                                        StatsPeriod.YEAR -> tr("Ultimo anno", "Last year")
                                    },
                                    periodMs,
                                    days = periodDays,
                                )
                            }

                            // Confronto con la media storica registrata dai guardiani.
                            if (histAvg > 0L) {
                                val weekAvg = u.weekMs / LocalDate.now().dayOfWeek.value
                                val diff = ((weekAvg - histAvg) * 100 / histAvg).toInt()
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    tr("Media storica: ", "Historical average: ") +
                                        formatMs(histAvg) + tr("/giorno", "/day"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    tr("Questa settimana: ", "This week: ") +
                                        formatMs(weekAvg) + tr("/giorno ", "/day ") +
                                        when {
                                            diff <= -5 -> tr(
                                                "(${-diff}% sotto la tua media!)",
                                                "(${-diff}% below your average!)",
                                            )
                                            diff >= 5 -> tr(
                                                "($diff% sopra la tua media)",
                                                "($diff% above your average)",
                                            )
                                            else -> tr("(in linea)", "(on track)")
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (diff <= -5) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ----------------------- app e categorie di oggi, in un'unica card
        // (14: prima le APP più usate — cliccabili → dettaglio — poi la torta)
        val categories = usage?.categoriesToday.orEmpty()
        val topApps = usage?.topAppsToday.orEmpty()
        if (categories.isNotEmpty() || topApps.isNotEmpty()) {
            item {
                Card(
                    onClick = { showAppsDetail = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        // Prima le app più usate di oggi (tocca per il dettaglio).
                        if (topApps.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    tr("App più usate oggi", "Most used apps today"),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = tr("Tutte le app", "All apps"),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            val maxMs = topApps.first().ms.coerceAtLeast(1L)
                            topApps.forEach { app -> AppUsageRow(app, maxMs) }
                        }
                        // Sotto, la torta delle macro-categorie.
                        if (categories.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                tr("Categorie (ultimi 7 giorni)", "Categories (last 7 days)"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CategoryPie(categories, Modifier.size(110.dp))
                                Spacer(Modifier.width(20.dp))
                                Column(Modifier.weight(1f)) {
                                    val total = categories.sumOf { it.second }.coerceAtLeast(1L)
                                    categories.take(5).forEach { (cat, ms) ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(vertical = 2.dp),
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(10.dp)
                                                    .background(
                                                        Color(cat.colorArgb),
                                                        RoundedCornerShape(2.dp),
                                                    )
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                cat.label,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                "${ms * 100 / total}%",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        // Il legame mente-sonno ora vive nella pagina SONNO (card in home).

    }
}

/**
 * Card "Il legame Mente-Sonno": collega Guardians a Health Connect e mostra
 * l'ultima dormita (durata, ora di addormentamento e risveglio) letta dai dati
 * che Samsung Health condivide. Difensiva: se manca Health Connect o il permesso
 * mostra il passo successivo, senza mai bloccare.
 */
@Composable
internal fun SleepConnectCard() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var status by remember {
        androidx.compose.runtime.mutableIntStateOf(
            androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE
        )
    }
    var granted by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var lastSleep by remember {
        mutableStateOf<androidx.health.connect.client.records.SleepSessionRecord?>(null)
    }
    var nights by remember {
        mutableStateOf<List<HealthConnectManager.WindDownNight>>(emptyList())
    }

    fun reload() {
        scope.launch {
            checking = true
            status = HealthConnectManager.status(context)
            if (status == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE) {
                granted = HealthConnectManager.hasPermission(context)
                if (granted) {
                    withContext(Dispatchers.IO) {
                        lastSleep = HealthConnectManager.lastSleep(context)
                        nights = HealthConnectManager.windDownNights(context)
                    }
                } else {
                    lastSleep = null
                    nights = emptyList()
                }
            }
            checking = false
        }
    }

    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.health.connect.client.PermissionController
            .createRequestPermissionResultContract(),
    ) { grantedSet ->
        granted = grantedSet.containsAll(HealthConnectManager.PERMISSIONS)
        reload()
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { reload() }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌙", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Text(
                    tr("Il legame Mente-Sonno", "The Mind-Sleep bond"),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))

            when {
                checking -> {
                    Text(
                        tr("Controllo Health Connect…", "Checking Health Connect…"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                status == androidx.health.connect.client.HealthConnectClient
                    .SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    Text(
                        tr(
                            "Health Connect va aggiornato per leggere il sonno.",
                            "Health Connect needs updating to read sleep.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.Button(onClick = { openHealthConnect(context) }) {
                        Text(tr("Apri Health Connect", "Open Health Connect"))
                    }
                }

                status != androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE -> {
                    Text(
                        tr(
                            "Health Connect non è disponibile su questo telefono. " +
                                "Installalo dal Play Store, poi in Samsung Health attiva " +
                                "la condivisione del Sonno con Health Connect.",
                            "Health Connect isn't available on this phone. Install it " +
                                "from the Play Store, then in Samsung Health enable " +
                                "sharing Sleep with Health Connect.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                !granted -> {
                    Text(
                        tr(
                            "Collega il sonno che Samsung Health condivide con Health " +
                                "Connect: vedrai l'ultima dormita qui, e l'Araldo userà il " +
                                "risveglio vero.",
                            "Connect the sleep Samsung Health shares with Health Connect: " +
                                "you'll see your last night here, and the Herald will use " +
                                "your real wake-up time.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.Button(
                        onClick = { permLauncher.launch(HealthConnectManager.PERMISSIONS) },
                    ) {
                        Text(tr("Collega Samsung Health", "Connect Samsung Health"))
                    }
                }

                else -> {
                    val sleep = lastSleep
                    if (sleep == null) {
                        Text(
                            tr(
                                "Collegato ✓ — ma non trovo ancora dormite recenti. " +
                                    "Assicurati che Samsung Health condivida il Sonno con " +
                                    "Health Connect.",
                                "Connected ✓ — but no recent sleep found yet. Make sure " +
                                    "Samsung Health shares Sleep with Health Connect.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val zone = ZoneId.systemDefault()
                        val mins = java.time.Duration.between(sleep.startTime, sleep.endTime)
                            .toMinutes().coerceAtLeast(0L)
                        val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                        val bed = sleep.startTime.atZone(zone).format(fmt)
                        val wake = sleep.endTime.atZone(zone).format(fmt)
                        Text(
                            tr("Ultima dormita", "Last night's sleep"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            formatMs(mins * 60_000L),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            tr("Dalle ", "From ") + bed + tr(" alle ", " to ") + wake,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Il grafico Distacco → Sonno (unico), sotto l'ultima dormita.
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                    )
                    Spacer(Modifier.height(14.dp))
                    WindDownSection(nights)
                }
            }
        }
    }
}

/**
 * Il legame "Distacco → Sonno": confronta quanto dormi nelle notti in cui posi il
 * telefono ALMENO mezz'ora prima di addormentarti con quelle in cui lo tieni fino
 * all'ultimo. È la prova, sui tuoi dati, che staccare prima fa bene al sonno.
 */
@Composable
private fun WindDownSection(nights: List<HealthConnectManager.WindDownNight>) {
    Text(tr("Distacco → Sonno", "Detach → Sleep"), fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(
        tr(
            "Quanto tempo posi il telefono prima di addormentarti, e come dormi.",
            "How long you put the phone down before falling asleep, and how you sleep.",
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    if (nights.size < 2) {
        Text(
            tr(
                "Sto raccogliendo le notti: servono ancora qualche sera con il " +
                    "telefono collegato a Health Connect per mostrare il legame.",
                "Collecting nights: a few more evenings with the phone connected to " +
                    "Health Connect are needed to show the link.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val early = nights.filter { it.gapMinutes >= 30 }
    val late = nights.filter { it.gapMinutes < 30 }
    val avgEarly = early.map { it.sleepMinutes }.average().takeIf { early.isNotEmpty() } ?: 0.0
    val avgLate = late.map { it.sleepMinutes }.average().takeIf { late.isNotEmpty() } ?: 0.0
    val maxAvg = maxOf(avgEarly, avgLate, 1.0)

    WindDownBar(
        label = tr("Stacco ≥ 30 min prima", "Detach ≥ 30 min before"),
        avgMinutes = avgEarly,
        count = early.size,
        fraction = (avgEarly / maxAvg).toFloat(),
        strong = true,
    )
    Spacer(Modifier.height(8.dp))
    WindDownBar(
        label = tr("Stacco < 30 min prima", "Detach < 30 min before"),
        avgMinutes = avgLate,
        count = late.size,
        fraction = (avgLate / maxAvg).toFloat(),
        strong = false,
    )

    // La frase-verdetto, solo se abbiamo entrambi i gruppi.
    if (early.isNotEmpty() && late.isNotEmpty()) {
        val diff = (avgEarly - avgLate) / 60.0
        Spacer(Modifier.height(12.dp))
        Text(
            if (diff >= 0.1) {
                tr(
                    "Quando stacchi almeno mezz'ora prima, dormi in media " +
                        "%.1f ore in più.".format(diff),
                    "When you detach at least half an hour earlier, you sleep " +
                        "%.1f h more on average.".format(diff),
                )
            } else {
                tr(
                    "Per ora la differenza è piccola: continua a raccogliere notti.",
                    "The difference is small so far: keep collecting nights.",
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    // Le ultime notti, dalla più recente.
    Spacer(Modifier.height(14.dp))
    val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    val zone = ZoneId.systemDefault()
    nights.sortedByDescending { it.sleepOnset }.take(5).forEach { n ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                n.sleepOnset.atZone(zone).toLocalDate()
                    .format(java.time.format.DateTimeFormatter.ofPattern("d/M")),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(38.dp),
            )
            Text(
                tr("posato ", "down ") + n.phoneOff.atZone(zone).format(fmt) +
                    " → " + tr("dormito ", "asleep ") + n.sleepOnset.atZone(zone).format(fmt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${n.gapMinutes}m",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (n.gapMinutes >= 30) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Una barra del confronto Distacco→Sonno: durata media del sonno di un gruppo. */
@Composable
private fun WindDownBar(
    label: String,
    avgMinutes: Double,
    count: Int,
    fraction: Float,
    strong: Boolean,
) {
    val color = if (strong) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                    .height(14.dp)
                    .background(color, RoundedCornerShape(4.dp)),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (count == 0) "—" else formatMs((avgMinutes * 60_000L).toLong()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Apre l'app/impostazioni di Health Connect (o il Play Store se assente). */
private fun openHealthConnect(context: Context) {
    val tryIntents = listOf(
        android.content.Intent("androidx.health.connect.action.HEALTH_CONNECT_SETTINGS"),
        android.content.Intent(android.content.Intent.ACTION_VIEW).setData(
            android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata")
        ),
    )
    for (intent in tryIntents) {
        try {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Throwable) {
        }
    }
}

/**
 * Grafico a barre degli ultimi 7 giorni (fatto solo di Box, si adatta al tema).
 * Se c'è una media storica [avgMs] > 0, accanto alla barra del giorno compare
 * una seconda barra più tenue con la media, per confrontare a colpo d'occhio.
 */
@Composable
private fun WeekBarChart(days: List<Pair<String, Long>>, avgMs: Long, goalMs: Long = 0L) {
    if (days.isEmpty()) return
    val showAvg = avgMs > 0L
    val showGoal = goalMs > 0L
    val maxMs = days.maxOf { it.second }
        .coerceAtLeast(avgMs)
        .coerceAtLeast(goalMs)
        .coerceAtLeast(1L)
    val avgColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    // Grigio-azzurro tenue: una guida discreta, non un allarme.
    val goalColor = Color(0xFF90A4AE).copy(alpha = 0.65f)

    Column {
        if (showAvg || showGoal) {
            // Legenda.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                LegendDot(MaterialTheme.colorScheme.primary, tr("Giorno", "Day"))
                if (showAvg) LegendDot(avgColor, tr("Media", "Average"))
                if (showGoal) LegendDot(goalColor, tr("Obiettivo", "Goal"))
            }
        }
        Box {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            days.forEach { (label, ms) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                ) {
                    Text(
                        if (ms > 0L) shortHours(ms) else "",
                        style = MaterialTheme.typography.labelSmall,
                        // Valore sopra la barra in GIALLO VIVO (il grigio è per le medie).
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    // Coppia di barre affiancate: giorno + media di riferimento.
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Nessuna barra "vuota": i giorni senza uso restano spazio.
                        if (ms > 0L) {
                            val dayFrac = (ms.toFloat() / maxMs.toFloat()).coerceIn(0.02f, 1f)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height((72 * dayFrac).dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                                    )
                            )
                        } else {
                            Box(Modifier.weight(1f))
                        }
                        if (showAvg) {
                            val avgFrac = (avgMs.toFloat() / maxMs.toFloat()).coerceIn(0.02f, 1f)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height((72 * avgFrac).dp)
                                    .background(
                                        avgColor,
                                        RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // Linea TRATTEGGIATA dell'obiettivo: una guida leggera, mai invadente.
        if (showGoal) {
            val goalFrac = (goalMs.toFloat() / maxMs.toFloat()).coerceIn(0.02f, 1f)
            androidx.compose.foundation.Canvas(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = (22 + 72 * goalFrac).dp)
                    .fillMaxWidth()
                    .height(2.dp)
            ) {
                drawLine(
                    color = goalColor,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                    strokeWidth = size.height,
                    pathEffect = androidx.compose.ui.graphics.PathEffect
                        .dashPathEffect(floatArrayOf(14f, 12f)),
                )
            }
        }
        }
    }
}

/** Grafico a torta delle categorie d'uso di oggi. */
@Composable
private fun CategoryPie(
    categories: List<Pair<com.guardians.app.model.AppCategory, Long>>,
    modifier: Modifier = Modifier,
) {
    val total = categories.sumOf { it.second }.coerceAtLeast(1L)
    androidx.compose.foundation.Canvas(modifier) {
        var startAngle = -90f
        categories.forEach { (cat, ms) ->
            val sweep = 360f * (ms.toFloat() / total.toFloat())
            drawArc(
                color = Color(cat.colorArgb),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "2h", "45m": etichette compatte sopra le barre. */
private fun shortHours(ms: Long): String {
    val minutes = ms / 60_000L
    return if (minutes >= 60) "${minutes / 60}h" else "${minutes}m"
}

@Composable
private fun SummaryRow(label: String, totalMs: Long, days: Int?) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatMs(totalMs) +
                if (days != null && totalMs > 0L) {
                    " (media ${formatMs(totalMs / days)}/giorno)"
                } else {
                    ""
                },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ------------------------------------------------------------------ dati uso

private fun loadUsageSummary(context: Context): UsageSummary {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val ignored = ignoredPackages(context)

    // Uso di un giorno calcolato dagli EVENTI reali: una sola app è in primo
    // piano alla volta, quindi il totale non può superare le 24h (a differenza
    // di queryAndAggregateUsageStats, che sommava tempi sovrapposti e gonfiava).
    fun dayUsage(startMs: Long, endMs: Long): Pair<Long, Map<String, Long>> {
        val perApp = HashMap<String, Long>()
        var total = 0L
        var curPkg: String? = null
        var curStart = 0L
        fun close(at: Long) {
            val p = curPkg ?: return
            val dur = (at - curStart)
            // Cap a 2h per intervallo: oltre è quasi sempre un evento di chiusura
            // mancato (schermo spento senza pausa), non uso reale.
            if (dur in 1 until 2L * 3600_000L && p !in ignored) {
                perApp[p] = (perApp[p] ?: 0L) + dur
                total += dur
            }
            curPkg = null
        }
        try {
            val events = usm.queryEvents(startMs, endMs)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        close(e.timeStamp)
                        curPkg = e.packageName
                        curStart = e.timeStamp
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (e.packageName == curPkg) close(e.timeStamp)
                    }
                    // Schermo spento (16), blocco (17), spegnimento (26): chiudi
                    // l'intervallo, così non si conta il tempo a telefono fermo.
                    16, 17, 26 -> close(e.timeStamp)
                }
            }
            close(minOf(endMs, now))
        } catch (_: Exception) {
        }
        return total.coerceAtMost(24L * 3600_000L) to perApp
    }

    fun startOf(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    // La settimana del grafico parte da lunedì; i giorni futuri restano a zero.
    val today = LocalDate.now()
    val monday = today.with(java.time.DayOfWeek.MONDAY)
    val locale = if (com.guardians.app.data.SettingsRepository.english.value) {
        Locale.ENGLISH
    } else {
        Locale.ITALIAN
    }
    val days = mutableListOf<Pair<String, Long>>()
    var todayPerApp: Map<String, Long> = emptyMap()
    var todayMs = 0L
    var weekMs = 0L
    val weekPerApp = HashMap<String, Long>()
    for (i in 0..6) {
        val date = monday.plusDays(i.toLong())
        val label = date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale)
        if (date.isAfter(today)) {
            days.add(label to 0L)
            continue
        }
        val (total, perApp) = dayUsage(startOf(date), startOf(date.plusDays(1)))
        if (date == today) {
            todayPerApp = perApp
            todayMs = total
        }
        perApp.forEach { (k, v) -> weekPerApp[k] = (weekPerApp[k] ?: 0L) + v }
        weekMs += total
        days.add(label to total)
    }

    // Migrazione: quando cambia il metodo di calcolo, azzera lo storico gonfiato
    // una volta sola, così la media riparte pulita.
    UsageHistoryRepository.migrateIfNeeded(context)

    // Semina/aggiorna lo storico degli ultimi 30 giorni dagli EVENTI (precisi,
    // ma Android li tiene solo ~1-2 settimane).
    val existing = UsageHistoryRepository.history.value
    for (i in 0..30) {
        val date = today.minusDays(i.toLong())
        val key = date.toString()
        if (i != 0 && existing.containsKey(key)) continue
        val total = dayUsage(startOf(date), startOf(date.plusDays(1))).first
        if (total > 0L) UsageHistoryRepository.record(context, key, total)
    }

    // Storico PROFONDO: oltre i 30 giorni gli eventi non ci sono più, ma le
    // statistiche AGGREGATE giornaliere di Android arrivano molto più indietro
    // (mesi). Le usiamo per riempire i giorni ancora vuoti, una tantum, con un
    // cap a 24h (l'aggregato tende a gonfiare sommando sessioni sovrapposte).
    try {
        val deepStart = today.minusDays(400).let(::startOf)
        val statsList = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, deepStart, now,
        )
        val perDay = HashMap<String, Long>()
        statsList?.forEach { s ->
            if (s.totalTimeInForeground <= 0L) return@forEach
            val day = java.time.Instant.ofEpochMilli(s.firstTimeStamp)
                .atZone(zone).toLocalDate().toString()
            perDay[day] = (perDay[day] ?: 0L) + s.totalTimeInForeground
        }
        val now2 = UsageHistoryRepository.history.value
        perDay.forEach { (day, ms) ->
            if (!now2.containsKey(day)) {
                UsageHistoryRepository.record(context, day, ms.coerceAtMost(24L * 3600_000L))
            }
        }
    } catch (_: Exception) {
    }

    // Media da confrontare e totali su finestre, presi dallo storico accurato.
    val hist = UsageHistoryRepository.history.value
    fun sumSince(days: Int): Long {
        val from = today.minusDays(days.toLong() - 1)
        return hist.entries.sumOf { (k, v) ->
            val d = try { LocalDate.parse(k) } catch (_: Exception) { null }
            if (d != null && !d.isBefore(from) && !d.isAfter(today)) v else 0L
        }
    }

    val pm = context.packageManager
    fun label(pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.')
    }

    // Icona nativa dell'app (ridotta a bitmap piccola per non pesare in memoria).
    fun icon(pkg: String): androidx.compose.ui.graphics.ImageBitmap? = try {
        pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
    } catch (_: Exception) {
        null
    }

    // Tutte le app di oggi (per la pagina di dettaglio), con colore categoria.
    val allApps = todayPerApp.entries
        .sortedByDescending { it.value }
        .filter { it.value >= 30_000L }  // sotto i 30s è rumore
        .map {
            TopApp(
                label(it.key), it.value, icon(it.key),
                com.guardians.app.model.categoryOf(context, it.key).colorArgb,
            )
        }
    val topApps = allApps.take(4)

    // Categorie sugli ULTIMI 7 GIORNI (auto-riconosciute).
    val categories = weekPerApp.entries
        .groupBy { com.guardians.app.model.categoryOf(context, it.key) }
        .mapValues { (_, list) -> list.sumOf { it.value } }
        .entries
        .sortedByDescending { it.value }
        .map { it.key to it.value }

    return UsageSummary(
        todayMs = todayMs,
        topAppsToday = topApps,
        allAppsToday = allApps,
        last7Days = days,
        categoriesToday = categories,
        weekMs = weekMs,
        monthMs = sumSince(30),
        yearMs = sumSince(365),
    )
}

/**
 * Una riga "app + uso": pallino del colore della categoria accanto all'icona
 * (14.2), barra proporzionale e minutaggio a destra.
 */
@Composable
private fun AppUsageRow(app: TopApp, maxMs: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        // Il pallino della categoria, poi l'icona vera dell'app.
        Box(
            Modifier
                .size(10.dp)
                .background(Color(app.categoryColor), androidx.compose.foundation.shape.CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        if (app.icon != null) {
            androidx.compose.foundation.Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .fillMaxWidth((app.ms.toFloat() / maxMs.toFloat()).coerceIn(0.05f, 1f))
                    .height(6.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(3.dp),
                    )
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            formatMs(app.ms),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Il dettaglio con l'uso di OGNI app di oggi (14.1): così si vede anche cosa
 * arriva da Maps, Spotify, Android Auto e simili. Pallino = categoria.
 */
@Composable
private fun AllAppsDetail(apps: List<TopApp>, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler { onBack() }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                }
                Text(
                    tr("Tutte le app di oggi", "All of today's apps"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (apps.isEmpty()) {
            item {
                Text(
                    tr("Nessun uso registrato oggi (per ora).", "No usage recorded today (yet)."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            val maxMs = apps.first().ms.coerceAtLeast(1L)
            items(apps.size) { i -> AppUsageRow(apps[i], maxMs) }
        }
    }
}

/**
 * App di sistema che non ha senso contare nel tempo di utilizzo. Delega alla
 * lista unica di UsageAnalytics (che esclude anche ANDROID AUTO e il launcher),
 * così tutti i conteggi dell'app usano le stesse regole.
 */
private fun ignoredPackages(context: Context): Set<String> =
    com.guardians.app.data.UsageAnalytics.ignored(context)
