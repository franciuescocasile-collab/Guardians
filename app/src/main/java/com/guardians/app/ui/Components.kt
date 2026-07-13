package com.guardians.app.ui

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.guardians.app.model.TimeUnit
import com.guardians.app.model.TimerType

/**
 * Icona del guardiano. Se per quel tipo esiste il PNG dell'elmo (forniti
 * dall'utente), mostra quello; altrimenti disegna la forma geometrica di
 * riserva (il Messaggero non ha ancora un elmo → resta il pentagono).
 */
@Composable
fun TimerShapeIcon(type: TimerType, modifier: Modifier = Modifier) {
    val res = guardianDrawable(type)
    if (res != null) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(res),
            contentDescription = null,
            modifier = modifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
        return
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val color = Color(type.colorArgb)
        when (type) {
            TimerType.MESSAGGERO -> drawPath(pentagonPath(w, h), color)
            // Forme di riserva (usate solo se un PNG mancasse).
            TimerType.SENTINELLA -> {
                val path = Path().apply {
                    moveTo(w / 2f, 0f); lineTo(w, h * 0.95f); lineTo(0f, h * 0.95f); close()
                }
                drawPath(path, color)
            }
            TimerType.GUARDIANO -> drawRoundRect(
                color = color, cornerRadius = CornerRadius(w * 0.15f, w * 0.15f),
            )
            TimerType.CUSTODE -> drawCircle(color, w * 0.46f, Offset(w / 2f, h / 2f))
            TimerType.GENDARME -> {
                val path = Path().apply {
                    moveTo(w / 2f, 0f); lineTo(w, h / 2f); lineTo(w / 2f, h); lineTo(0f, h / 2f); close()
                }
                drawPath(path, color)
            }
            TimerType.VEDETTA -> drawPath(starPath(w, h), color)
            TimerType.ESATTORE -> drawPath(hexagonPath(w, h), color)
            TimerType.ARALDO -> {
                drawArc(
                    color = color, startAngle = 180f, sweepAngle = 180f, useCenter = true,
                    topLeft = Offset(w * 0.1f, h * 0.22f),
                    size = androidx.compose.ui.geometry.Size(w * 0.8f, w * 0.8f),
                )
                drawRoundRect(
                    color = color, topLeft = Offset(0f, h * 0.7f),
                    size = androidx.compose.ui.geometry.Size(w, h * 0.13f),
                    cornerRadius = CornerRadius(h * 0.065f, h * 0.065f),
                )
            }
        }
    }
}

/** Il PNG dell'elmo per il tipo, o null se non c'è (Messaggero → forma). */
private fun guardianDrawable(type: TimerType): Int? = when (type) {
    TimerType.SENTINELLA -> com.guardians.app.R.drawable.guardian_sentinella
    TimerType.GUARDIANO -> com.guardians.app.R.drawable.guardian_guardiano
    TimerType.CUSTODE -> com.guardians.app.R.drawable.guardian_custode
    TimerType.GENDARME -> com.guardians.app.R.drawable.guardian_gendarme
    TimerType.VEDETTA -> com.guardians.app.R.drawable.guardian_vedetta
    TimerType.ESATTORE -> com.guardians.app.R.drawable.guardian_esattore
    TimerType.ARALDO -> com.guardians.app.R.drawable.guardian_araldo
    TimerType.MESSAGGERO -> null
}

/** Esagono (punta in alto) inscritto nell'area [w]×[h] (usato dall'Esattore). */
private fun hexagonPath(w: Float, h: Float): Path {
    val cx = w / 2f
    val cy = h / 2f
    val r = w * 0.5f
    return Path().apply {
        for (i in 0 until 6) {
            val angle = Math.toRadians((-90 + i * 60).toDouble())
            val x = cx + (r * kotlin.math.cos(angle)).toFloat()
            val y = cy + (r * kotlin.math.sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/** Pentagono (punta in alto) inscritto nell'area [w]×[h] (usato dal Messaggero). */
private fun pentagonPath(w: Float, h: Float): Path {
    val cx = w / 2f
    val cy = h / 2f
    val r = w * 0.52f
    return Path().apply {
        for (i in 0 until 5) {
            val angle = Math.toRadians((-90 + i * 72).toDouble())
            val x = cx + (r * kotlin.math.cos(angle)).toFloat()
            val y = cy + (r * kotlin.math.sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/** Stella a 5 punte inscritta nell'area [w]×[h] (usata dalla Vedetta). */
private fun starPath(w: Float, h: Float): Path {
    val cx = w / 2f
    val cy = h / 2f
    val outer = w * 0.5f
    val inner = w * 0.21f
    return Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val angle = Math.toRadians((-90 + i * 36).toDouble())
            val x = cx + (r * kotlin.math.cos(angle)).toFloat()
            val y = cy + (r * kotlin.math.sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/** Campo durata: numero + menù a tendina per l'unità (secondi/minuti/ore). */
@Composable
fun DurationField(
    label: String,
    value: String,
    unit: TimeUnit,
    onValueChange: (String) -> Unit,
    onUnitChange: (TimeUnit) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue -> onValueChange(newValue.filter { it.isDigit() }) },
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Box {
            var expanded by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { expanded = true }) {
                Text(unit.displayName)
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Cambia unità di tempo")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                TimeUnit.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = {
                            onUnitChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun DropdownPickerButton(
    text: String,
    options: List<String>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        var expanded by remember { mutableStateOf(false) }
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    },
                )
            }
        }
    }
}

data class PermissionsStatus(
    val usageAccess: Boolean,
    val overlay: Boolean,
)

/** Stato dei permessi speciali, ricontrollato ogni volta che si torna all'app. */
@Composable
fun rememberPermissionsState(): PermissionsStatus {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(refresh) {
        PermissionsStatus(
            usageAccess = hasUsageAccess(context),
            overlay = Settings.canDrawOverlays(context),
        )
    }
}

fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= 29) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
