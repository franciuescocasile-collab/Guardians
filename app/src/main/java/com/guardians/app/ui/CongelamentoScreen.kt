package com.guardians.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guardians.app.data.SpellsRepository
import com.guardians.app.data.tr
import com.guardians.app.model.formatMs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Il cerchio va da 10 a 120 minuti; oltre si passa da "Personalizza". */
private const val CIRCLE_MIN = 10
private const val CIRCLE_MAX = 120

/** Tetto invalicabile per l'inserimento manuale: 300 minuti (6 ore). */
private const val CUSTOM_MAX = 300

/**
 * Il Cerchio del Gelo: la pagina del Congelamento. Scorri il dito lungo il
 * cerchio per scegliere quanto isolarti; con "Continua a contare dopo la
 * scadenza" il countdown diventa cronometro (+mm) finché non termini tu.
 */
@Composable
fun CongelamentoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val freezeUntil by SpellsRepository.freezeUntil.collectAsState()
    val startedAt by SpellsRepository.freezeStartedAt.collectAsState()
    val overtime by SpellsRepository.freezeOvertime.collectAsState()
    val notify by SpellsRepository.freezeNotify.collectAsState()
    val notifyRing by SpellsRepository.freezeNotifyRing.collectAsState()

    // Ticker locale: il countdown/cronometro scorre anche senza servizio.
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowTick = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }
    val now = nowTick
    val sessionActive = freezeUntil > 0L && (now < freezeUntil || overtime)

    var minutes by remember { mutableStateOf(30) }
    var customDialog by remember { mutableStateOf(false) }
    var confirmBreak by remember { mutableStateOf(false) }

    if (customDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { customDialog = false },
            title = { Text(tr("Personalizza", "Customize")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr(
                            "Di quanti minuti vuoi che sia il congelamento?",
                            "How many minutes should the freeze last?",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isDigit() } },
                        label = { Text(tr("Minuti", "Minutes")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    (text.toIntOrNull())?.let {
                        if (it > CUSTOM_MAX) {
                            Text(
                                tr("Massimo $CUSTOM_MAX minuti.", "Max $CUSTOM_MAX minutes."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                val v = text.toIntOrNull() ?: 0
                TextButton(
                    enabled = v in 1..CUSTOM_MAX,
                    onClick = {
                        minutes = v.coerceAtMost(CUSTOM_MAX)
                        customDialog = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { customDialog = false }) {
                    Text(tr("Annulla", "Cancel"))
                }
            },
        )
    }

    if (confirmBreak) {
        AlertDialog(
            onDismissRequest = { confirmBreak = false },
            title = { Text(tr("Interrompere il Congelamento?", "Break the Freeze?")) },
            text = {
                Text(
                    tr("Mancano ancora ", "There are still ") +
                        formatMs((freezeUntil - now).coerceAtLeast(1000L)) +
                        tr(
                            ". Sei sicuro di volerti arrendere adesso?",
                            " left. Are you sure you want to give up now?",
                        )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // Interruzione anticipata di un Grande Congelamento: penale
                    // proporzionale ai minuti rimasti (solo se >30 min totali).
                    com.guardians.app.data.ConductRepository.registerFreezeInterruption(
                        context,
                        remainingMs = (freezeUntil - now).coerceAtLeast(0L),
                        totalMs = (freezeUntil - startedAt).coerceAtLeast(0L),
                    )
                    SpellsRepository.breakFreeze(context)
                    confirmBreak = false
                }) { Text(tr("Mi arrendo", "I give up")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBreak = false }) {
                    Text(tr("Resisto", "I'll hold on"))
                }
            },
        )
    }

    // Intensità della brina/neve: cresce con la durata scelta. In sessione
    // NON salta a un valore fisso alto (prima 0.85 = tempesta), ma resta la
    // STESSA di quando l'hai impostata, ricavata dalla durata scelta (3).
    val frost = if (sessionActive) {
        val sessionMin = ((freezeUntil - startedAt) / 60_000L).toInt()
        ((sessionMin - CIRCLE_MIN).toFloat() / (CIRCLE_MAX - CIRCLE_MIN).toFloat())
            .coerceIn(0f, 1f)
    } else {
        ((minutes - CIRCLE_MIN).toFloat() / (CIRCLE_MAX - CIRCLE_MIN).toFloat())
            .coerceIn(0f, 1f)
    }

    Box(Modifier.fillMaxSize()) {
    // GLASSMORPHISM (20): il fondale ghiacciato SFOCATO c'è SEMPRE — mentre
    // giri il disco cresce con i minuti scelti, in sessione è pieno (il blur
    // vero richiede Android 12+; sotto, resta il fondale morbido).
    IceBackdrop(intensity = frost, modifier = Modifier.fillMaxSize())
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
            // Stessa icona gialla della Home: coerenza estetica, niente emoji.
            Icon(
                Icons.Default.AcUnit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                tr("Congelamento", "Freeze"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        if (sessionActive) {
            // ------------------------------- sessione in corso
            val inOvertime = now >= freezeUntil
            val totalMs = (freezeUntil - startedAt).coerceAtLeast(1L)
            val remainingFrac = if (inOvertime) 0f
            else ((freezeUntil - now).toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
            // Il cerchio resta GROSSO come in impostazione: niente più quadrato
            // di vetro attorno (15). Prende lo spazio disponibile.
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                FreezeCircle(
                    progress = if (inOvertime) 1f else 1f - remainingFrac,
                    enabled = false,
                    onMinutesChange = {},
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (inOvertime) {
                                tr("più ", "") + formatMs((now - freezeUntil).coerceAtLeast(1000L))
                            } else {
                                formatMs((freezeUntil - now).coerceAtLeast(1000L))
                            },
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                        if (inOvertime) {
                            Text(
                                tr("tempo extra conquistato", "extra time conquered"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            // Lo switch resta disponibile anche in sessione (15.1): decidi se
            // farla continuare oltre la scadenza anche dopo averla avviata.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    tr(
                        "Continua a contare dopo la scadenza",
                        "Keep counting after time's up",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = overtime,
                    onCheckedChange = { SpellsRepository.setFreezeOvertime(context, it) },
                )
            }
            Button(
                onClick = {
                    // Prima dello zero è una resa (conferma); dopo, una vittoria.
                    if (now < freezeUntil) confirmBreak = true
                    else SpellsRepository.breakFreeze(context)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    if (inOvertime) {
                        tr("Termina la sessione", "End the session")
                    } else {
                        tr("Interrompi", "Break it")
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            // ------------------------------------------ scelta della durata
            // Cerchio al centro dello spazio libero (niente scroll, 15.4).
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                FreezeCircle(
                    progress = ((minutes - CIRCLE_MIN).toFloat() /
                        (CIRCLE_MAX - CIRCLE_MIN).toFloat()).coerceIn(0f, 1f),
                    enabled = true,
                    onMinutesChange = { minutes = it },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$minutes",
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            tr("minuti", "minutes"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            TextButton(
                onClick = { customDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(tr("Personalizza i minuti", "Customize minutes"))
            }

            // -------- impostazioni compatte (stanno senza scrollare, 15.4)
            // Continua a contare dopo la scadenza.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    tr(
                        "Continua a contare dopo la scadenza",
                        "Keep counting after time's up",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = overtime,
                    onCheckedChange = { SpellsRepository.setFreezeOvertime(context, it) },
                )
            }
            // Notificami quando finisce il timer (15.2).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    tr("Notificami quando finisce", "Notify me when it ends"),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = notify,
                    onCheckedChange = { SpellsRepository.setFreezeNotify(context, it) },
                )
            }
            // Scelta tra notifica-messaggio e suoneria (15.3), solo se acceso.
            if (notify) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    NotifyChoice(
                        label = tr("Messaggio", "Message"),
                        selected = !notifyRing,
                        modifier = Modifier.weight(1f),
                    ) { SpellsRepository.setFreezeNotifyRing(context, false) }
                    NotifyChoice(
                        label = tr("Suoneria", "Ringtone"),
                        selected = notifyRing,
                        modifier = Modifier.weight(1f),
                    ) { SpellsRepository.setFreezeNotifyRing(context, true) }
                }
            }
            Button(
                onClick = {
                    SpellsRepository.castFreeze(
                        context, System.currentTimeMillis() + minutes * 60_000L,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    tr("Congela per ", "Freeze for ") + formatMs(minutes * 60_000L),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
        // Overlay "brina": aloni ghiacciati sui bordi che si infittiscono
        // man mano che aumenti la durata. Il Canvas non intercetta i tocchi.
        FrostOverlay(intensity = frost, modifier = Modifier.fillMaxSize())
        // La NEVE: fiocchi e brezza che crescono con il tempo scelto (5).
        SnowfallOverlay(intensity = frost, modifier = Modifier.fillMaxSize())
    }
}

/**
 * Fondale "vetro ghiacciato": grandi aloni azzurro ghiaccio SFOCATI (blur
 * reale su Android 12+, altrimenti restano morbidi). [intensity] 0..1: mentre
 * giri il disco cresce coi minuti, in sessione è pieno. Non intercetta tocchi.
 */
@Composable
private fun IceBackdrop(intensity: Float, modifier: Modifier = Modifier) {
    if (intensity <= 0.02f) return
    val ice = androidx.compose.ui.graphics.Color(0xFFE0F7FA)
    val k = intensity.coerceIn(0f, 1f)
    Canvas(modifier.blur(48.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(ice.copy(alpha = 0.19f * k), radius = w * 0.55f, center = Offset(w * 0.15f, h * 0.20f))
        drawCircle(ice.copy(alpha = 0.14f * k), radius = w * 0.65f, center = Offset(w * 0.95f, h * 0.45f))
        drawCircle(ice.copy(alpha = 0.16f * k), radius = w * 0.50f, center = Offset(w * 0.30f, h * 0.90f))
    }
}

/** Cornice di brina progressiva sui bordi dello schermo (puramente decorativa). */
@Composable
private fun FrostOverlay(intensity: Float, modifier: Modifier = Modifier) {
    if (intensity <= 0.02f) return
    val frostColor = androidx.compose.ui.graphics.Color(0xFFBBDEFB)
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val edge = (h * 0.16f) * intensity
        // Aloni tenui lungo i quattro bordi.
        val a = 0.10f + 0.22f * intensity
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to frostColor.copy(alpha = a),
                1f to frostColor.copy(alpha = 0f),
                startY = 0f, endY = edge,
            ),
            size = androidx.compose.ui.geometry.Size(w, edge),
        )
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to frostColor.copy(alpha = 0f),
                1f to frostColor.copy(alpha = a),
                startY = h - edge, endY = h,
            ),
            topLeft = Offset(0f, h - edge),
            size = androidx.compose.ui.geometry.Size(w, edge),
        )
        // I "raggi" dagli angoli sono stati RIMOSSI (5.1): al loro posto c'è
        // la neve animata di SnowfallOverlay.
    }
}

/**
 * NEVE ANIMATA (5): fiocchi che cadono con una brezza laterale. Più è lungo il
 * timer, più fiocchi, più VELOCI e con più vento: a 10 min nevischio lento, a
 * 120 min tempesta. La velocità cresce davvero perché l'orologio è manuale
 * (con InfiniteTransition la durata era fissa). Non intercetta i tocchi.
 */
@Composable
private fun SnowfallOverlay(intensity: Float, modifier: Modifier = Modifier) {
    // Base 0.12: anche col timer al minimo c'è un po' di neve (non zero).
    val k = (0.12f + 0.88f * intensity).coerceIn(0f, 1f)
    // Orologio in secondi che avanza a velocità DIPENDENTE dall'intensità.
    var clock by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameMillis { ms ->
                if (last != 0L) clock += (ms - last) / 1000f
                last = ms
            }
        }
    }
    // Semi FISSI per ogni fiocco (posizione, velocità, taglia, fase).
    val flakes = remember {
        List(90) { i ->
            val r = java.util.Random(i.toLong() * 7919)
            floatArrayOf(
                r.nextFloat(),               // x di partenza (0..1)
                0.6f + r.nextFloat() * 0.8f, // velocità relativa di caduta
                1.6f + r.nextFloat() * 2.8f, // raggio in px-base
                r.nextFloat(),               // fase
            )
        }
    }
    val snow = androidx.compose.ui.graphics.Color(0xFFE0F7FA)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val count = (8 + 82 * k).toInt().coerceAtMost(flakes.size)
        // La VELOCITÀ di caduta cresce con k, ma con tetto DIMEZZATO: la massima
        // (timer al massimo) è quella che prima si aveva a ~60 min (1). Quantità,
        // vento e brina restano invariati.
        val fallSpeed = 0.18f + 0.62f * k
        // La brezza: deriva laterale e oscillazione, più forti col timer.
        val drift = w * (0.10f + 0.9f * k)
        val swayAmp = w * (0.02f + 0.07f * k)
        val swaySpeed = 1.2f + 3.5f * k
        for (i in 0 until count) {
            val f = flakes[i]
            val progress = (clock * fallSpeed * f[1] + f[3]) % 1f
            val y = progress * (h + 40f) - 20f
            val x = ((f[0] * w) +
                drift * progress +
                swayAmp * kotlin.math.sin(
                    (clock * swaySpeed + f[3] * 6.28f).toDouble(),
                ).toFloat() +
                2f * w) % w
            drawCircle(
                color = snow.copy(alpha = 0.22f + 0.5f * k),
                radius = f[2] * (0.7f + 0.7f * k),
                center = Offset(x, y),
            )
        }
    }
}

/** Pillola di scelta (Messaggio / Suoneria) per la notifica di fine timer. */
@Composable
private fun NotifyChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(10.dp),
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Il selettore circolare: un anello trascinabile (10→120 minuti in senso
 * orario dalle ore 12). Il contenuto centrale è libero ([center]).
 */
@Composable
private fun FreezeCircle(
    progress: Float,
    enabled: Boolean,
    onMinutesChange: (Int) -> Unit,
    center: @Composable () -> Unit,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val arcColor = MaterialTheme.colorScheme.primary

    // Valore corrente del cerchio, per impedire il salto all'indietro (0→max):
    // se il dito "scavalca" le ore 12 tornando indietro, si resta agli estremi.
    var lastCircleValue = CIRCLE_MIN

    fun minutesFromPosition(pos: Offset, size: androidx.compose.ui.geometry.Size): Int {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val angle = Math.toDegrees(
            atan2((pos.y - cy).toDouble(), (pos.x - cx).toDouble())
        )
        // 0° = ore 3; portiamo lo zero alle ore 12, in senso orario.
        val clock = ((angle + 90.0) + 360.0) % 360.0
        val raw = CIRCLE_MIN + (clock / 360.0) * (CIRCLE_MAX - CIRCLE_MIN)
        val candidate = ((raw / 5.0).roundToInt() * 5).coerceIn(CIRCLE_MIN, CIRCLE_MAX)
        // Anti-wrap: un salto oltre metà scala è un attraversamento delle ore 12,
        // non un movimento reale → resta all'estremo più vicino nel verso orario.
        val span = CIRCLE_MAX - CIRCLE_MIN
        val result = when {
            candidate - lastCircleValue > span / 2 -> CIRCLE_MIN
            lastCircleValue - candidate > span / 2 -> CIRCLE_MAX
            else -> candidate
        }
        lastCircleValue = result
        return result
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .aspectRatio(1f),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (enabled) {
                        Modifier
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        onMinutesChange(
                                            minutesFromPosition(
                                                pos,
                                                androidx.compose.ui.geometry.Size(
                                                    size.width.toFloat(),
                                                    size.height.toFloat(),
                                                ),
                                            )
                                        )
                                    },
                                ) { change, _ ->
                                    change.consume()
                                    onMinutesChange(
                                        minutesFromPosition(
                                            change.position,
                                            androidx.compose.ui.geometry.Size(
                                                size.width.toFloat(),
                                                size.height.toFloat(),
                                            ),
                                        )
                                    )
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { pos ->
                                    onMinutesChange(
                                        minutesFromPosition(
                                            pos,
                                            androidx.compose.ui.geometry.Size(
                                                size.width.toFloat(),
                                                size.height.toFloat(),
                                            ),
                                        )
                                    )
                                }
                            }
                    } else {
                        Modifier
                    }
                ),
        ) {
            val stroke = 26f
            val radius = (size.minDimension - stroke) / 2f
            val topLeft = Offset(
                (size.width - radius * 2f) / 2f,
                (size.height - radius * 2f) / 2f,
            )
            val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
            // Anello di sfondo.
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Arco del tempo scelto (o del progresso della sessione).
            val sweep = 360f * progress.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            // Pomello alla fine dell'arco (solo quando si può trascinare).
            if (enabled) {
                val rad = Math.toRadians((-90f + sweep).toDouble())
                val cx = size.width / 2f + radius * cos(rad).toFloat()
                val cy = size.height / 2f + radius * sin(rad).toFloat()
                drawCircle(color = arcColor, radius = stroke * 0.9f, center = Offset(cx, cy))
            }
        }
        center()
    }
}
