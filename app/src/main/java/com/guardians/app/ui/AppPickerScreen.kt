package com.guardians.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.guardians.app.data.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

@Composable
fun AppPickerScreen(
    selected: Set<String>,
    onDone: (Set<String>) -> Unit,
    title: String? = null,
    subtitle: String? = null,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf(selected) }

    // VELOCE (6): prima si mostra subito la lista dei NOMI (leggeri), le icone
    // arrivano riga per riga in background e restano in cache — la seconda
    // apertura è istantanea.
    val apps by produceState<List<AppInfo>?>(initialValue = appListCache) {
        value = appListCache ?: withContext(Dispatchers.IO) { loadInstalledApps(context) }
        // Ricarica comunque in sottofondo (per app installate/rimosse da poco).
        value = withContext(Dispatchers.IO) { loadInstalledApps(context) }
            .also { appListCache = it }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Button(
                onClick = { onDone(selection) },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .height(52.dp),
            ) {
                Text(
                    tr(
                        "Fatto (${selection.size} selezionate)",
                        "Done (${selection.size} selected)",
                    ),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding),
        ) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp),
                )
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 4.dp, 16.dp, 0.dp),
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(tr("Cerca app", "Search apps")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 8.dp),
            )

            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Gli spazi ai bordi non devono rompere la ricerca ("maps " deve
                // trovare Maps): il confronto usa la query ripulita.
                val q = query.trim()
                // Le app GIÀ SELEZIONATE stanno IN CIMA (altro 5): quando
                // modifichi un guardiano le ritrovi subito, senza cercarle.
                // L'ordine si calcola una volta all'apertura (initialSelection),
                // così spuntare/togliere una spunta non fa saltare le righe.
                val initialSelection = remember(list) { selection }
                val filtered = list
                    .filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }
                    .sortedBy { it.packageName !in initialSelection }
                // "Seleziona tutte" agisce sulle app elencate (quindi rispetta la ricerca).
                val allSelected = filtered.isNotEmpty() &&
                    filtered.all { selection.contains(it.packageName) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Text(
                        "${filtered.size} app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            val packages = filtered.map { it.packageName }
                            selection = if (allSelected) {
                                selection - packages.toSet()
                            } else {
                                selection + packages
                            }
                        },
                    ) {
                        Text(
                            if (allSelected) {
                                tr("Deseleziona tutte", "Deselect all")
                            } else {
                                tr("Seleziona tutte", "Select all")
                            }
                        )
                    }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.packageName }) { app ->
                        val isChecked = selection.contains(app.packageName)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selection = if (isChecked) {
                                        selection - app.packageName
                                    } else {
                                        selection + app.packageName
                                    }
                                }
                                .padding(16.dp, 10.dp),
                        ) {
                            // Icona caricata pigramente e tenuta in cache (6).
                            val cachedIcon = iconCache[app.packageName]
                            if (cachedIcon == null && !iconCache.containsKey(app.packageName)) {
                                androidx.compose.runtime.LaunchedEffect(app.packageName) {
                                    withContext(Dispatchers.IO) {
                                        val bmp = try {
                                            context.packageManager
                                                .getApplicationIcon(app.packageName)
                                                .toBitmap(96, 96).asImageBitmap()
                                        } catch (_: Exception) {
                                            null
                                        }
                                        iconCache[app.packageName] = bmp
                                    }
                                }
                            }
                            if (cachedIcon != null) {
                                Image(
                                    bitmap = cachedIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                )
                            } else {
                                Spacer(Modifier.size(40.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Checkbox(checked = isChecked, onCheckedChange = null)
                        }
                    }
                }
            }
        }
    }
}

// Cache di processo: lista app e icone sopravvivono tra un'apertura e l'altra
// del selettore — la prima volta è veloce (solo nomi), le successive istantanee.
private var appListCache: List<AppInfo>? = null
private val iconCache =
    androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.graphics.ImageBitmap?>()

private fun loadInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    return pm.queryIntentActivities(launcherIntent, 0)
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != context.packageName }
        .map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val label = resolveInfo.loadLabel(pm)?.toString() ?: packageName
            // NIENTE icone qui (erano il collo di bottiglia): arrivano dopo,
            // riga per riga, dalla iconCache.
            AppInfo(packageName, label, null)
        }
        .sortedBy { it.label.lowercase() }
}
