package com.guardians.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
                            "Per le sessioni oltre i 120 minuti del cerchio: " +
                                "scrivi i minuti che vuoi (massimo $CUSTOM_MAX = 6 ore).",
                            "For sessions beyond the circle's 120 minutes: " +
                                "type the minutes you want (max $CUSTOM_MAX = 6 hours).",
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

    // Intensità della brina: cresce con la durata scelta (o è piena in sessione).
    val frost = if (sessionActive) 0.85f
    else ((minutes - CIRCLE_MIN).toFloat() / (CIRCLE_MAX - CIRCLE_MIN).toFloat())
        .coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize()) {
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
            // ------------------------------------------ sessione in corso
            val inOvertime = now >= freezeUntil
            val totalMs = (freezeUntil - startedAt).coerceAtLeast(1L)
            val remainingFrac = if (inOvertime) 0f
            else ((freezeUntil - now).toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
            FreezeCircle(
                progress = if (inOvertime) 1f else 1f - remainingFrac,
                enabled = false,
                onMinutesChange = {},
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (inOvertime) {
                            "+" + formatMs((now - freezeUntil).coerceAtLeast(1000L))
                        } else {
                            formatMs((freezeUntil - now).coerceAtLeast(1000L))
                        },
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        if (inOvertime) {
                            tr("tempo extra conquistato", "extra time conquered")
                        } else {
                            tr("al disgelo", "to the thaw")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (inOvertime) {
                Text(
                    tr(
                        "Il tempo è scaduto ma la sessione continua: stai " +
                            "dimostrando di poterne fare a meno. Termina quando vuoi.",
                        "Time's up but the session goes on: you're proving you " +
                            "can do without it. End whenever you want.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
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
                        tr("Interrompi (mi arrendo)", "Break it (I give up)")
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            // ------------------------------------------ scelta della durata
            Text(
                tr(
                    "Scorri il dito lungo il cerchio per decidere quanto stare " +
                        "tassativamente lontano dal telefono.",
                    "Slide your finger around the circle to decide how long to " +
                        "stay strictly away from the phone.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
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
            TextButton(
                onClick = { customDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(tr("Personalizza", "Customize"))
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

            // ------------------------------------- impostazioni avanzate
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        tr("Impostazioni avanzate", "Advanced settings"),
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                tr(
                                    "Continua a contare dopo la scadenza",
                                    "Keep counting after time's up",
                                )
                            )
                            Text(
                                tr(
                                    "Allo 00:00 la sessione non finisce: parte un " +
                                        "cronometro (+minuti) e il blocco regge finché " +
                                        "non la termini tu. Quanto riesci a resistere?",
                                    "At 00:00 the session doesn't end: a stopwatch " +
                                        "(+minutes) starts and the lock holds until you " +
                                        "end it. How long can you hold out?",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = overtime,
                            onCheckedChange = {
                                SpellsRepository.setFreezeOvertime(context, it)
                            },
                        )
                    }
                }
            }
        }
    }
        // Overlay "brina": una cornice ghiacciata sui bordi che si infittisce
        // man mano che aumenti la durata. Il Canvas non intercetta i tocchi.
        FrostOverlay(intensity = frost, modifier = Modifier.fillMaxSize())
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
        // Cristalli: raggi corti dagli angoli, sempre più lunghi e numerosi.
        val crystals = (6 + 26 * intensity).toInt()
        val len = edge * 0.9f
        val stroke = 2f
        val corners = listOf(
            Offset(0f, 0f), Offset(w, 0f), Offset(0f, h), Offset(w, h),
        )
        corners.forEach { c ->
            for (i in 0 until crystals) {
                val ang = (i.toFloat() / crystals) * Math.PI.toFloat() / 2f +
                    (if (c.x > 0) Math.PI.toFloat() / 2f else 0f)
                val dx = kotlin.math.cos(ang) * len * (if (c.x > 0) -1f else 1f)
                val dy = kotlin.math.sin(ang) * len * (if (c.y > 0) -1f else 1f)
                drawLine(
                    color = frostColor.copy(alpha = 0.25f * intensity),
                    start = c,
                    end = Offset(c.x + dx, c.y + dy),
                    strokeWidth = stroke,
                )
            }
        }
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
