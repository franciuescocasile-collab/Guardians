package com.guardians.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.guardians.app.model.TimerType

// Colori dei guardiani: uguali in entrambi i temi (sono il "marchio" di ogni tipo).
val AmberSentinella = Color(TimerType.SENTINELLA.colorArgb)
val RedGuardiano = Color(TimerType.GUARDIANO.colorArgb)

// Tema scuro (predefinito): notte blu.
private val DarkColors = darkColorScheme(
    primary = AmberSentinella,
    onPrimary = Color(0xFF1A1300),
    secondary = RedGuardiano,
    onSecondary = Color.White,
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFE8EAF2),
    surface = Color(0xFF141B2E),
    onSurface = Color(0xFFE8EAF2),
    surfaceVariant = Color(0xFF1D2640),
    onSurfaceVariant = Color(0xFF9AA5C0),
    error = RedGuardiano,
)

// Tema chiaro: bianco e blu (stessa famiglia di colori, non bianco e nero).
private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    secondary = Color(0xFF1E88E5),
    onSecondary = Color.White,
    background = Color(0xFFF2F6FC),
    onBackground = Color(0xFF16233A),
    surface = Color.White,
    onSurface = Color(0xFF16233A),
    surfaceVariant = Color(0xFFDCE6F5),
    onSurfaceVariant = Color(0xFF54678A),
    error = RedGuardiano,
)

@Composable
fun GuardiansTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
