package com.guardians.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guardians.app.data.AraldoData
import com.guardians.app.data.ProfileRepository
import com.guardians.app.data.StatsRepository
import com.guardians.app.data.TimerRepository
import com.guardians.app.data.UsageHistoryRepository
import com.guardians.app.data.tr
import com.guardians.app.model.TimerType
import com.guardians.app.model.formatMs
import com.guardians.app.model.formatTimeOfDay
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Il Profilo: lo stemma grande in cima, il nome, l'obiettivo giornaliero e
 * il sonno dell'Araldo in bella vista, i numeri e il collegamento al perché.
 */
@Composable
fun UserProfileScreen(
    onBack: () -> Unit,
    onOpenWhy: () -> Unit,
    onOpenGoalGuide: () -> Unit,
) {
    val context = LocalContext.current
    ProfileRepository.load(context)
    StatsRepository.load(context)
    UsageHistoryRepository.load(context)
    AraldoData.load(context)

    val nickname by ProfileRepository.nickname.collectAsState()
    val avatar by ProfileRepository.avatar.collectAsState()
    val firstUse by ProfileRepository.firstUseDate.collectAsState()
    val timers by TimerRepository.timers.collectAsState()
    val stats by StatsRepository.stats.collectAsState()
    val goals by ProfileRepository.goals.collectAsState()
    val dailyGoal by ProfileRepository.dailyGoalMinutes.collectAsState()
    val bedMinute by ProfileRepository.usualBedMinute.collectAsState()
    val wakeMinute by ProfileRepository.usualWakeMinute.collectAsState()

    var nameField by remember(nickname) { mutableStateOf(nickname) }
    var crestDialog by remember { mutableStateOf(false) }

    val avatarType = TimerType.entries.firstOrNull { it.name == avatar }

    if (crestDialog) {
        AlertDialog(
            onDismissRequest = { crestDialog = false },
            title = { Text(tr("Scegli il tuo stemma", "Choose your crest")) },
            text = {
                // Griglia 4 per riga con elmi GRANDI (prima erano minuscoli).
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimerType.entries.filter { !it.configuredFromHub }
                        .chunked(4)
                        .forEach { rowTypes ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                rowTypes.forEach { type ->
                                    Card(
                                        onClick = {
                                            ProfileRepository.setAvatar(context, type.name)
                                            crestDialog = false
                                        },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.background
                                        ),
                                        border = if (avatar == type.name) {
                                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                        } else {
                                            null
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .padding(vertical = 12.dp)
                                                .fillMaxWidth(),
                                        ) {
                                            TimerShapeIcon(type, Modifier.size(52.dp))
                                        }
                                    }
                                }
                                // Riga incompleta: riempi per non allargare le card.
                                repeat(4 - rowTypes.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { crestDialog = false }) { Text(tr("Chiudi", "Close")) }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
            Text(
                tr("Profilo", "Profile"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        // ------------------- lo stemma grande, centrale, sopra il nome ------
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Niente IconButton: il suo ritaglio circolare mozzava gli angoli
            // delle forme (la punta del triangolo, le braccia della stella).
            // Un Box cliccabile senza clip lascia lo stemma integro.
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clickable(
                        interactionSource = remember {
                            androidx.compose.foundation.interaction.MutableInteractionSource()
                        },
                        indication = null,
                    ) { crestDialog = true },
                contentAlignment = Alignment.Center,
            ) {
                if (avatarType != null) {
                    TimerShapeIcon(avatarType, Modifier.size(84.dp))
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = tr("Scegli lo stemma", "Choose your crest"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // Nome NUDO, centrato ed elegante: nessun box, autosave a ogni tasto.
            androidx.compose.foundation.text.BasicTextField(
                value = nameField,
                onValueChange = {
                    nameField = it
                    ProfileRepository.setNickname(context, it)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.primary,
                ),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                        if (nameField.isEmpty()) {
                            Text(
                                tr("Il tuo nome", "Your name"),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // La Barra della Condotta ora vive SOLO in home (accanto allo
            // stemma): dal profilo è stata rimossa su richiesta dell'utente.
        }

        // --------------- OBIETTIVO GIORNALIERO: subito sotto il nome (19.1)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                var sliderMin by remember(dailyGoal) {
                    mutableStateOf(if (dailyGoal > 0) dailyGoal else 120)
                }
                // Titolo + valore su una riga, con la "i" a destra: compatto.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("Obiettivo: ", "Goal: "),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        formatMs(sliderMin * 60_000L),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    // La "i" mostra un micro-tooltip non bloccante che va alle guide.
                    Box {
                        var tip by remember { mutableStateOf(false) }
                        IconButton(onClick = { tip = true }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = tr("Come scegliere", "How to choose"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (tip) {
                            androidx.compose.ui.window.Popup(
                                alignment = Alignment.TopEnd,
                                offset = androidx.compose.ui.unit.IntOffset(0, -110),
                                onDismissRequest = { tip = false },
                                properties = androidx.compose.ui.window.PopupProperties(
                                    focusable = false,
                                ),
                            ) {
                                Card(
                                    onClick = { tip = false; onOpenGoalGuide() },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                                    ),
                                ) {
                                    Text(
                                        tr(
                                            "Per stabilire il miglior obiettivo " +
                                                "giornaliero consulta le guide",
                                            "To set your best daily goal, check the guides",
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.inverseOnSurface,
                                        modifier = Modifier
                                            .width(220.dp)
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Slider(
                    value = sliderMin.toFloat(),
                    onValueChange = { sliderMin = (it / 30f).roundToInt() * 30 },
                    valueRange = 30f..480f,
                    steps = 14,
                )
                // Il Salva compare SOLO se lo slider è stato spostato davvero.
                val changed = sliderMin != dailyGoal && !(dailyGoal == 0 && sliderMin == 120)
                if (changed) {
                    TextButton(
                        onClick = { ProfileRepository.setDailyGoalMinutes(context, sliderMin) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(tr("Salva l'obiettivo", "Save the goal"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ------- solo streak + media storica, con il nuovo lessico esteso
        val streak by androidx.compose.runtime.produceState(0, dailyGoal) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.guardians.app.data.ConductRepository
                    .focusStreak(context, dailyGoal * 60_000L)
            }
        }
        val avgMs = UsageHistoryRepository.dailyAverageMs()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile(
                big = "🔥 $streak",
                label = tr(
                    "Giorni consecutivi obiettivo raggiunto",
                    "Consecutive days goal met",
                ),
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                big = if (avgMs > 0L) formatMs(avgMs) else "—",
                label = tr(
                    "Media d'uso giornaliera storica",
                    "Historical daily usage average",
                ),
                modifier = Modifier.weight(1f),
            )
        }

        // ------------------------------------------------ bacheca dei record
        val topGuardian = stats.entries
            .maxByOrNull { it.value.totalBlocks + it.value.totalRejected }
            ?.value
            ?.takeIf { it.totalBlocks + it.totalRejected > 0 }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("Bacheca", "Highlights"), fontWeight = FontWeight.Bold)
                if (topGuardian == null) {
                    Text(
                        tr(
                            "Quando un guardiano ti fermerà, qui comparirà il tuo " +
                                "\"più severo\".",
                            "When a guardian stops you, your \"strictest\" one shows up here.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        topGuardian.type?.let {
                            TimerShapeIcon(it, Modifier.size(34.dp))
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                tr("Il guardiano più severo", "Your strictest guardian"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                topGuardian.name +
                                    (topGuardian.type?.let { " · ${it.shortName}" } ?: ""),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            "${topGuardian.totalBlocks + topGuardian.totalRejected}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            tr("volte", "times"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ------------------------ gli altri record della bacheca (9)
                UsageHistoryRepository.load(context)
                val bacheca by androidx.compose.runtime.produceState<List<Pair<String, String>>>(
                    initialValue = emptyList(),
                ) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val out = mutableListOf<Pair<String, String>>()
                        val bestStreak = com.guardians.app.data.ConductRepository
                            .bestFocusStreak(context)
                        if (bestStreak > 0) {
                            out.add(
                                tr(
                                    "Obiettivo rispettato di fila (record)",
                                    "Goal met in a row (record)",
                                ) to tr(
                                    if (bestStreak == 1) "1 giorno" else "$bestStreak giorni",
                                    if (bestStreak == 1) "1 day" else "$bestStreak days",
                                ),
                            )
                        }
                        val longestFreeze = com.guardians.app.data.SpellsRepository
                            .longestFreezeMs(context)
                        if (longestFreeze >= 60_000L) {
                            out.add(
                                tr("Congelamento più lungo", "Longest freeze") to
                                    formatMs(longestFreeze),
                            )
                        }
                        val hist = UsageHistoryRepository.history.value
                            .filterValues { it > 5L * 60_000L }   // via i giorni-spezzone
                        hist.maxByOrNull { it.value }?.let { (_, ms) ->
                            out.add(
                                tr(
                                    "Giorno più intenso al telefono",
                                    "Heaviest phone day",
                                ) to formatMs(ms),
                            )
                        }
                        hist.minByOrNull { it.value }?.let { (_, ms) ->
                            out.add(
                                tr(
                                    "Giorno più leggero al telefono",
                                    "Lightest phone day",
                                ) to formatMs(ms),
                            )
                        }
                        out
                    }
                }
                bacheca.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // L'Araldo e il sonno ora vivono nella pagina SONNO (card in home).

        // ------------------------------------------- collegamento al perché
        Card(
            onClick = onOpenWhy,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Il tuo perché", "Your why"), fontWeight = FontWeight.Bold)
                    Text(
                        if (goals.isEmpty()) {
                            tr(
                                "Nessun obiettivo ancora: scrivi perché vuoi staccare.",
                                "No goals yet: write down why you want to unplug.",
                            )
                        } else {
                            tr(
                                "${goals.size} obiettivi che i guardiani ti ricordano",
                                "${goals.size} goals your guardians remind you of",
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Riquadro con un valore grande e una didascalia (Focus Streak, media…). */
@Composable
private fun MetricTile(big: String, label: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                big,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Il colore del gradiente della barra alla posizione [good] (0=rosso, 1=verde). */
internal fun conductColorAt(good: Float): androidx.compose.ui.graphics.Color {
    val red = androidx.compose.ui.graphics.Color(0xFFC62828)
    val amber = androidx.compose.ui.graphics.Color(0xFFF2B01E)
    val green = androidx.compose.ui.graphics.Color(0xFF2E9E5B)
    val g = good.coerceIn(0f, 1f)
    return when {
        g <= 0.50f -> androidx.compose.ui.graphics.lerp(red, amber, g / 0.50f)
        g <= 0.85f -> androidx.compose.ui.graphics.lerp(amber, green, (g - 0.50f) / 0.35f)
        else -> green
    }
}

/** Valutazione a parole della condotta (7 livelli) dal punteggio 0..100. */
internal fun conductRating(score: Int): String = when {
    score >= 90 -> tr("Eccellente", "Excellent")
    score >= 75 -> tr("Ottima", "Great")
    score >= 60 -> tr("Buona", "Good")
    score >= 45 -> tr("Bilanciata", "Balanced")
    score >= 30 -> tr("Instabile", "Unstable")
    score >= 15 -> tr("Grave", "Serious")
    else -> tr("Pessima", "Poor")
}

/**
 * La Barra della Condotta: gradiente Rosso (sinistra) → Verde (destra), con la
 * porzione verde più ampia per dare margine di manovra. [good] 0..1 indica la
 * posizione del cursore (1 = massimo verde, a destra). Il cursore è una linea
 * verticale leggera. Nessun numero, solo un riflesso dei fatti.
 */
@Composable
private fun ConductBar(good: Float) {
    val green = androidx.compose.ui.graphics.Color(0xFF2E9E5B)
    val amber = androidx.compose.ui.graphics.Color(0xFFF2B01E)
    val red = androidx.compose.ui.graphics.Color(0xFFC62828)
    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxWidth()
            .height(18.dp),
    ) {
        val r = size.height / 2f
        // Rosso a sinistra, giallo CENTRATO, verde a destra: rosso→ambra fino a
        // metà barra, poi ambra→verde, verde pieno nell'ultimo tratto.
        drawRoundRect(
            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                0f to red, 0.5f to amber, 0.85f to green, 1f to green,
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
        // Cursore: linea verticale bianca, leggermente più spessa.
        val cx = good.coerceIn(0f, 1f) * size.width
        val x = cx.coerceIn(3f, size.width - 3f)
        val w = 6f
        drawRoundRect(
            color = androidx.compose.ui.graphics.Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(x - w / 2f, -3f),
            size = androidx.compose.ui.geometry.Size(w, size.height + 6f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
        )
        drawRoundRect(
            color = androidx.compose.ui.graphics.Color(0x55000000),
            topLeft = androidx.compose.ui.geometry.Offset(x - w / 2f, -3f),
            size = androidx.compose.ui.geometry.Size(w, size.height + 6f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
            style = Stroke(width = 1f),
        )
    }
}
