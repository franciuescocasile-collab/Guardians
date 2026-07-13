package com.guardians.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guardians.app.data.ProfileRepository
import com.guardians.app.data.tr

/** Profilo: gli obiettivi per cui limitare il telefono. I popup li richiamano. */
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val goals by ProfileRepository.goals.collectAsState()
    var newGoal by remember { mutableStateOf("") }

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
                tr("Il tuo perché", "Your why"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            tr(
                "Scrivi i motivi per cui vuoi limitare il telefono (un hobby, la famiglia, " +
                    "lo studio, lo sport…). Quando un guardiano ti bloccherà, ti ricorderà " +
                    "questi obiettivi invece di lasciarti solo un divieto.",
                "Write the reasons why you want to limit your phone (a hobby, family, " +
                    "studying, sport…). When a guardian blocks you, it will remind you " +
                    "of these goals instead of just saying no.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = newGoal,
                onValueChange = { newGoal = it },
                label = { Text(tr("Nuovo obiettivo", "New goal")) },
                placeholder = { Text(tr("Es. suonare la chitarra", "E.g. playing guitar")) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    if (newGoal.isNotBlank()) {
                        ProfileRepository.setGoals(context, goals + newGoal)
                        newGoal = ""
                    }
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi obiettivo")
            }
        }

        if (goals.isEmpty()) {
            Text(
                tr(
                    "Ancora nessun obiettivo. Aggiungine uno qui sopra!",
                    "No goals yet. Add one above!",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        goals.forEach { goal ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(goal, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { ProfileRepository.setGoals(context, goals - goal) },
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Rimuovi",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
