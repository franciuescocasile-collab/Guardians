package com.guardians.app.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.guardians.app.data.NewsRepository
import com.guardians.app.data.ProfileRepository
import com.guardians.app.data.SpellsRepository
import com.guardians.app.data.TimerRepository
import com.guardians.app.data.tr
import com.guardians.app.data.UsageStateStore
import com.guardians.app.model.TimerType
import com.guardians.app.model.formatMs
import com.guardians.app.service.MonitorService
import com.guardians.app.ui.theme.AmberSentinella

/**
 * Schermata principale: da qui si raggiungono le varie sezioni dell'app
 * (guardiani, statistiche, app escluse, impostazioni).
 */
@Composable
fun HubScreen(
    onCreateTimer: () -> Unit,
    onOpenTeams: () -> Unit,
    onOpenGuides: () -> Unit,
    onOpenUserProfile: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenFreeze: () -> Unit,
    onOpenNotifier: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val timers by TimerRepository.timers.collectAsState()
    val usage by UsageStateStore.state.collectAsState()
    val permissions = rememberPermissionsState()

    // Richiesta del permesso notifiche (Android 13+), serve per il servizio in foreground.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // La protezione è "attiva" se il permesso c'è e almeno un guardiano è acceso.
    val protectionActive = permissions.usageAccess && timers.any { it.enabled }

    // Avvia il monitoraggio se serve, fermalo quando non ci sono più guardiani.
    LaunchedEffect(permissions.usageAccess, timers) {
        if (protectionActive) MonitorService.start(context)
        else if (timers.none { it.enabled }) MonitorService.stop(context)
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
        Column {
            NewsRepository.load(context)
            val nickname by ProfileRepository.nickname.collectAsState()
            val avatar by ProfileRepository.avatar.collectAsState()
            val avatarType = TimerType.entries.firstOrNull { it.name == avatar }
            val unreadNews by NewsRepository.unread.collectAsState()
            // Header-profilo: stemma + nome, cliccabile → Profilo. Sostituisce il
            // vecchio titolo statico "Guardians".
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember {
                                androidx.compose.foundation.interaction.MutableInteractionSource()
                            },
                            indication = null,
                        ) { onOpenUserProfile() },
                ) {
                    Box(
                        Modifier
                            .size(58.dp)
                            .background(
                                MaterialTheme.colorScheme.surface, CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarType != null) {
                            TimerShapeIcon(avatarType, Modifier.size(40.dp))
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        // La valutazione della Condotta al posto del saluto: è il
                        // "voto di reputazione" sempre in vista (10.1).
                        val goalMin = ProfileRepository.dailyGoalMinutes.collectAsState().value
                        val hdr by androidx.compose.runtime.produceState(
                            Triple(0L, emptyMap<com.guardians.app.model.MacroCategory, Long>(), 0),
                            goalMin,
                        ) {
                            value = kotlinx.coroutines.withContext(
                                kotlinx.coroutines.Dispatchers.IO,
                            ) {
                                val z = java.time.ZoneId.systemDefault()
                                val s = java.time.LocalDate.now().atStartOfDay(z)
                                    .toInstant().toEpochMilli()
                                val pm = com.guardians.app.data.UsageAnalytics
                                    .perMacroMs(context, s, System.currentTimeMillis())
                                val total = pm.values.sum()
                                val st = com.guardians.app.data.ConductRepository
                                    .focusStreak(context, goalMin * 60_000L)
                                Triple(total, pm, st)
                            }
                        }
                        val streak = hdr.third
                        val good = (1f - com.guardians.app.data.ConductRepository
                            .liveCursor(hdr.first, hdr.second)).coerceIn(0f, 1f)
                        // La scritta STA SOPRA la barra, stessa larghezza (9); il
                        // fuoco a destra è alto quanto scritta+barra insieme.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        tr("Condotta: ", "Conduct: "),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                    Text(
                                        conductRating((good * 100).toInt()),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = conductColorAt(good),
                                        maxLines = 1,
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                MiniConductBar(good, Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.width(10.dp))
                            StreakFlame(streak, Modifier.size(46.dp))
                        }
                    }
                }
                // Campanella e ingranaggio vicini (10): tocchi più stretti (40dp).
                IconButton(onClick = onOpenNews, modifier = Modifier.size(40.dp)) {
                    BadgedBox(
                        badge = { if (unreadNews > 0) Badge() },
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = tr("Novità", "What's new"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(2.dp))
                // Le Impostazioni si aprono da qui: niente card dedicata in lista.
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = tr("Impostazioni", "Settings"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // La riga "Protezione attiva" è stata rimossa (richiesta utente):
            // lo stato si capisce già dalla condotta e dalle card.
        }

        if (!permissions.usageAccess) {
            PermissionCard(
                title = tr("Accesso ai dati di utilizzo", "Usage access"),
                description = tr(
                    "Indispensabile: serve a Guardians per capire quale app " +
                        "stai usando. Tocca qui e attiva \"Guardians\".",
                    "Essential: Guardians needs it to know which app you are " +
                        "using. Tap here and enable \"Guardians\".",
                ),
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
            )
        }
        if (!permissions.overlay) {
            PermissionCard(
                title = tr("Sovrapposizione su altre app", "Display over other apps"),
                description = tr(
                    "Serve per mostrare il popup quando un guardiano chiude " +
                        "un'app. Tocca qui e consenti la sovrapposizione.",
                    "Needed to show the popup when a guardian closes an app. " +
                        "Tap here and allow the overlay.",
                ),
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                },
            )
        }

        Spacer(Modifier.height(4.dp))

        // ORDINE E VISIBILITÀ delle card: si sistemano DIRETTAMENTE QUI (2).
        // Pressione prolungata su una card → modalità "sistema la home":
        // a sinistra compaiono le tre lineette (trascina per spostare), a
        // destra lo switch per nascondere (solo Guide/Notificatore/Sonno).
        com.guardians.app.data.HomeConfigRepository.load(context)
        val cardOrder by com.guardians.app.data.HomeConfigRepository.order.collectAsState()
        val hiddenCards by com.guardians.app.data.HomeConfigRepository.hidden.collectAsState()
        var homeEdit by remember { mutableStateOf(false) }
        var confirmHideSleep by remember { mutableStateOf(false) }

        if (confirmHideSleep) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmHideSleep = false },
                title = { Text(tr("Nascondere il Sonno?", "Hide Sleep?")) },
                text = {
                    Text(
                        tr(
                            "Nascondendo la sezione Sonno, anche l'Araldo andrà in " +
                                "standby e tutti i suoi blocchi orari notturni verranno " +
                                "temporaneamente disattivati. Vuoi procedere?\n\n" +
                                "(I dati sul sonno continuano a essere raccolti: " +
                                "rimostrando la card, l'Araldo si riattiva da solo.)",
                            "Hiding the Sleep section will also put the Herald on " +
                                "standby, temporarily disabling its nighttime blocks. " +
                                "Proceed?\n\n(Sleep data keeps being collected: show " +
                                "the card again and the Herald wakes up.)",
                        ),
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            com.guardians.app.data.HomeConfigRepository.setHidden(
                                context,
                                com.guardians.app.data.HomeConfigRepository.CARD_SLEEP,
                                true,
                            )
                            confirmHideSleep = false
                        },
                    ) { Text(tr("Nascondi e metti in standby", "Hide and stand by")) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { confirmHideSleep = false },
                    ) { Text(tr("Annulla", "Cancel")) }
                },
            )
        }

        if (homeEdit) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Sistema la tua home", "Arrange your home"),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        tr(
                            "Trascina ≡ per spostare, spegni per disattivare",
                            "Drag ≡ to move, switch off to disable",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.TextButton(onClick = { homeEdit = false }) {
                    Text(tr("Fatto", "Done"), fontWeight = FontWeight.Bold)
                }
            }
        }

        for (cardKey in cardOrder) {
            val isOff = cardKey in hiddenCards
            // Fuori dalla modifica modifica, le card disattivate non si vedono;
            // in modifica ci sono TUTTE (le spente restano lì, grigie, con lo
            // switch, così le puoi riaccendere) — 7.
            if (!homeEdit && isOff) continue
            HomeEditableRow(
                cardKey = cardKey,
                editMode = homeEdit,
                active = !isOff,
                hideable = cardKey in com.guardians.app.data.HomeConfigRepository.HIDEABLE,
                onDragMove = { delta ->
                    com.guardians.app.data.HomeConfigRepository
                        .move(context, cardKey, delta)
                },
                onToggleActive = { on ->
                    if (!on && cardKey == com.guardians.app.data.HomeConfigRepository.CARD_SLEEP) {
                        confirmHideSleep = true
                    } else {
                        com.guardians.app.data.HomeConfigRepository
                            .setHidden(context, cardKey, !on)
                    }
                },
            ) {
            when (cardKey) {
                com.guardians.app.data.HomeConfigRepository.CARD_GUARDIANI -> HubCard(
                    title = tr("Nuovo guardiano", "New guardian"),
                    subtitle = tr(
                        "Crea un timer e assegnalo a una squadra",
                        "Create a timer and assign it to a team",
                    ),
                    icon = Icons.Default.Shield,
                    onClick = onCreateTimer,
                    onLongClick = { homeEdit = true },
                )

                com.guardians.app.data.HomeConfigRepository.CARD_SQUADRE -> HubCard(
                    title = tr("Squadre", "Teams"),
                    subtitle = tr(
                        "Raggruppa i guardiani in cartelle per gestirli insieme e " +
                            "pianificarne i giorni",
                        "Group your guardians into folders to manage them together and " +
                            "schedule their days",
                    ),
                    // Un gruppo di figure insieme: l'idea della squadra.
                    icon = Icons.Default.Groups,
                    onClick = onOpenTeams,
                    onLongClick = { homeEdit = true },
                )

                com.guardians.app.data.HomeConfigRepository.CARD_FREEZE -> {
                    val freezeUntil by SpellsRepository.freezeUntil.collectAsState()
                    val freezeOvertime by SpellsRepository.freezeOvertime.collectAsState()
                    UsageStateStore.state.collectAsState().value // tick → countdown vivo
                    val nowMs = System.currentTimeMillis()
                    HubCard(
                        title = tr("Congelamento", "Freeze"),
                        subtitle = when {
                            freezeUntil > 0L && nowMs < freezeUntil ->
                                tr("Telefono congelato ancora per ", "Phone frozen for ") +
                                    formatMs((freezeUntil - nowMs).coerceAtLeast(1000L))
                            freezeUntil > 0L && freezeOvertime ->
                                tr("Oltre il tempo: più ", "Past the time: ") +
                                    formatMs((nowMs - freezeUntil).coerceAtLeast(1000L)) +
                                    tr(" — grande!", " more — great!")
                            else -> tr(
                                "Isolamento totale: decidi tu quanto stare lontano dal telefono",
                                "Total isolation: you decide how long to stay off the phone",
                            )
                        },
                        icon = Icons.Default.AcUnit,
                        onClick = onOpenFreeze,
                        onLongClick = { homeEdit = true },
                    )
                }

                com.guardians.app.data.HomeConfigRepository.CARD_NOTIFIER -> {
                    com.guardians.app.data.NotifierRepository.load(context)
                    val reminders by com.guardians.app.data.NotifierRepository
                        .reminders.collectAsState()
                    HubCard(
                        title = tr("Il Notificatore", "The Notifier"),
                        subtitle = if (reminders.isEmpty()) {
                            tr(
                                "Promemoria usa-e-getta: bevi acqua, alzati, respira…",
                                "One-time reminders: drink water, stand up, breathe…",
                            )
                        } else {
                            tr(
                                plural(reminders.size, "promemoria in programma", "promemoria in programma"),
                                plural(reminders.size, "reminder scheduled", "reminders scheduled"),
                            )
                        },
                        icon = Icons.Default.Alarm,
                        onClick = onOpenNotifier,
                        onLongClick = { homeEdit = true },
                    )
                }

                com.guardians.app.data.HomeConfigRepository.CARD_STATS -> {
                    val todayUsage by androidx.compose.runtime.produceState<Long?>(
                        initialValue = null,
                    ) {
                        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            todayUsageMs(context)
                        }
                    }
                    StatsHubCard(
                        todayMs = todayUsage,
                        onClick = onOpenStats,
                        onLongClick = { homeEdit = true },
                    )
                }

                com.guardians.app.data.HomeConfigRepository.CARD_SLEEP -> HubCard(
                    title = tr("Sonno", "Sleep"),
                    subtitle = tr(
                        "L'ultima dormita, il legame col telefono e l'Araldo",
                        "Last night's sleep, the phone link and the Herald",
                    ),
                    icon = Icons.Default.Bedtime,
                    onClick = onOpenSleep,
                    onLongClick = { homeEdit = true },
                )

                com.guardians.app.data.HomeConfigRepository.CARD_GUIDE -> HubCard(
                    title = tr("Guide", "Guides"),
                    subtitle = tr(
                        "Articoli e consigli su misura per te",
                        "Articles and tips tailored to you",
                    ),
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    onClick = onOpenGuides,
                    onLongClick = { homeEdit = true },
                )
            }
            }
        }
    }
}

/**
 * Vera in modalità "sistema la home": le HubCard la leggono per NASCONDERE la
 * freccia ">" e disattivare il click (6), così non si entra nelle pagine.
 */
val LocalHomeEditMode = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * Una riga della home in modalità "sistema": a sinistra le TRE LINEETTE da
 * trascinare per spostare la card, a destra lo switch per attivarla/disattivarla
 * (solo le non essenziali). Le disattivate restano visibili ma sbiadite (7).
 * Fuori dalla modalità, la card è nuda.
 */
@Composable
private fun HomeEditableRow(
    cardKey: String,
    editMode: Boolean,
    active: Boolean,
    hideable: Boolean,
    onDragMove: (Int) -> Unit,
    onToggleActive: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    if (!editMode) {
        content()
        return
    }
    // La card SEGUE il dito mentre trascini, e riordina UNA VOLTA al rilascio:
    // così non si "inceppa" a ogni scavalco (2). Il passo è l'altezza vera della
    // riga (misurata) più la spaziatura della lista.
    var dragY by remember(cardKey) { mutableStateOf(0f) }
    var rowH by remember(cardKey) { mutableStateOf(0f) }
    val dragging = dragY != 0f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .onGloballyPositioned { rowH = it.size.height.toFloat() }
            .offset { IntOffset(0, dragY.roundToInt()) }
            .zIndex(if (dragging) 1f else 0f),
    ) {
        Icon(
            Icons.Default.DragHandle,
            contentDescription = tr("Trascina per spostare", "Drag to move"),
            tint = if (dragging) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(34.dp)
                .pointerInput(cardKey) {
                    val spacing = 12.dp.toPx()
                    detectVerticalDragGestures(
                        onDragEnd = {
                            val step = (if (rowH > 0f) rowH else 88.dp.toPx()) + spacing
                            val steps = (dragY / step).roundToInt()
                            val dir = if (steps > 0) 1 else -1
                            repeat(kotlin.math.abs(steps)) { onDragMove(dir) }
                            dragY = 0f
                        },
                        onDragCancel = { dragY = 0f },
                    ) { change, dy ->
                        change.consume()
                        dragY += dy
                    }
                },
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .alpha(if (active) 1f else 0.4f),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalHomeEditMode provides true,
            ) { content() }
        }
        if (hideable) {
            Spacer(Modifier.width(8.dp))
            Switch(checked = active, onCheckedChange = { onToggleActive(it) })
        } else {
            // Le essenziali non si spengono: spazio per allineare le righe.
            Spacer(Modifier.width(52.dp))
        }
    }
}

/** "1 squadra attiva" / "3 squadre attive": singolare/plurale puliti. */
private fun plural(n: Int, one: String, many: String): String =
    "$n " + if (n == 1) one else many

/** Mini barra della condotta per l'header (rosso sx → verde dx, cursore sottile). */
@Composable
private fun MiniConductBar(good: Float, modifier: Modifier = Modifier) {
    val green = Color(0xFF2E9E5B)
    val amber = Color(0xFFF2B01E)
    val red = Color(0xFFC62828)
    androidx.compose.foundation.Canvas(modifier.height(8.dp)) {
        val r = size.height / 2f
        drawRoundRect(
            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                0f to red, 0.5f to amber, 0.85f to green, 1f to green,
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
        val x = (good.coerceIn(0f, 1f) * size.width).coerceIn(2f, size.width - 2f)
        drawRoundRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(x - 2f, -2f),
            size = androidx.compose.ui.geometry.Size(4f, size.height + 4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
        )
    }
}

/**
 * Card Statistiche (L1): FISSA, senza swipe né puntini. Mostra il tempo di
 * schermo di oggi in GRIGIO, con la label esplicita.
 */
@Composable
private fun StatsHubCard(
    todayMs: Long?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val edit = LocalHomeEditMode.current
    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Icon(
                Icons.Default.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Statistiche", "Statistics"), fontWeight = FontWeight.Bold)
                Text(
                    if (todayMs != null && todayMs > 0L) {
                        tr(
                            "Tempo di schermo odierno: ${formatMs(todayMs)}",
                            "Today's screen time: ${formatMs(todayMs)}",
                        )
                    } else {
                        tr("Tempo al telefono, app e blocchi", "Screen time, apps and blocks")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!edit) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (edit) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            content = body,
        )
    } else {
        BouncyCard(
            onClick = onClick,
            onLongClick = onLongClick,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            content = body,
        )
    }
}

@Composable
private fun HubCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    shape: TimerType? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val edit = LocalHomeEditMode.current
    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            if (shape != null) {
                TimerShapeIcon(shape, Modifier.size(28.dp))
            } else if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // In modalità "sistema" la freccia sparisce e la card non è cliccabile (6).
            if (!edit) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (edit) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            content = body,
        )
    } else {
        // BouncyCard: effetto pressione (1) + long-press → modalità "sistema".
        BouncyCard(
            onClick = onClick,
            onLongClick = onLongClick,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            content = body,
        )
    }
}

/** Tempo di schermo di oggi (dalla mezzanotte), calcolato dagli eventi reali. */
private fun todayUsageMs(context: android.content.Context): Long {
    if (!hasUsageAccess(context)) return 0L
    val usm = context.getSystemService(android.content.Context.USAGE_STATS_SERVICE)
        as android.app.usage.UsageStatsManager
    val zone = java.time.ZoneId.systemDefault()
    val start = java.time.LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
    val now = System.currentTimeMillis()
    var total = 0L
    var curStart = 0L
    var inForeground = false
    try {
        val events = usm.queryEvents(start, now)
        val e = android.app.usage.UsageEvents.Event()
        fun close(at: Long) {
            if (inForeground) {
                val d = at - curStart
                if (d in 1 until 2L * 3600_000L) total += d
                inForeground = false
            }
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    close(e.timeStamp)
                    curStart = e.timeStamp
                    inForeground = true
                }
                android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> close(e.timeStamp)
                16, 17, 26 -> close(e.timeStamp)
            }
        }
        close(now)
    } catch (_: Exception) {
    }
    return total.coerceAtMost(24L * 3600_000L)
}

@Composable
fun PermissionCard(title: String, description: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = AmberSentinella.copy(alpha = 0.15f)
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = AmberSentinella,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
