package com.guardians.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guardians.app.data.ProfileRepository
import com.guardians.app.data.tr
import com.guardians.app.model.TimeUnit
import com.guardians.app.model.TimerType

/**
 * Primo avvio: la Bussola. Chiede nome, stemma, obiettivo di tempo e gli
 * orari indicativi di sonno/risveglio (la base dell'Araldo dal giorno 1).
 * Tutto è facoltativo e resta modificabile dal Profilo.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(ProfileRepository.nickname.value) }
    var avatar by remember { mutableStateOf(ProfileRepository.avatar.value) }
    var goalValue by remember { mutableStateOf("") }
    var goalUnit by remember { mutableStateOf(TimeUnit.HOURS) }
    var bedMinute by remember {
        mutableStateOf(ProfileRepository.usualBedMinute.value.takeIf { it >= 0 } ?: (23 * 60))
    }
    var wakeMinute by remember {
        mutableStateOf(ProfileRepository.usualWakeMinute.value.takeIf { it >= 0 } ?: (7 * 60))
    }

    fun finish() {
        if (name.isNotBlank()) ProfileRepository.setNickname(context, name)
        if (avatar.isNotBlank()) ProfileRepository.setAvatar(context, avatar)
        goalValue.toIntOrNull()?.let { v ->
            if (v > 0) {
                ProfileRepository.setDailyGoalMinutes(
                    context, (v * goalUnit.seconds / 60L).toInt(),
                )
            }
        }
        ProfileRepository.setUsualBedMinute(context, bedMinute)
        ProfileRepository.setUsualWakeMinute(context, wakeMinute)
        ProfileRepository.setOnboarded(context)
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            tr("Benvenuto in Guardians", "Welcome to Guardians"),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            tr(
                "Quattro domande per preparare i tuoi guardiani. Puoi cambiare " +
                    "tutto in ogni momento dal Profilo.",
                "Four questions to get your guardians ready. You can change " +
                    "everything anytime from the Profile.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ------------------------------------------------------------ 1. nome
        OnboardingCard(title = tr("1 · Come ti chiamiamo?", "1 · What should we call you?")) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(tr("Il tuo nome", "Your name")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ---------------------------------------------------------- 2. stemma
        OnboardingCard(
            title = tr("2 · Scegli il tuo stemma", "2 · Choose your crest"),
            subtitle = tr(
                "Il simbolo di un guardiano diventa il tuo avatar.",
                "A guardian's symbol becomes your avatar.",
            ),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TimerType.entries.filter { !it.configuredFromHub }.forEach { type ->
                    Card(
                        onClick = { avatar = type.name },
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
                            modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                        ) {
                            TimerShapeIcon(type, Modifier.size(26.dp))
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------- 3. obiettivo
        OnboardingCard(
            title = tr("3 · Il tuo obiettivo di tempo", "3 · Your screen-time goal"),
            subtitle = tr(
                "Quanto vorresti usare il telefono al giorno, al massimo?",
                "How much would you like to use the phone per day, at most?",
            ),
        ) {
            DurationField(
                label = tr("Tempo al giorno (facoltativo)", "Time per day (optional)"),
                value = goalValue,
                unit = goalUnit,
                onValueChange = { goalValue = it },
                onUnitChange = { goalUnit = it },
            )
        }

        // ------------------------------------------------------------ 4. sonno
        OnboardingCard(
            title = tr("4 · I tuoi orari di sonno", "4 · Your sleep schedule"),
            subtitle = tr(
                "Servono all'Araldo per proteggerti fin dal primo giorno: poi " +
                    "imparerà da solo i tuoi orari veri.",
                "The Herald uses them to protect you from day one: then it " +
                    "learns your real schedule by itself.",
            ),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeOfDayPicker(
                    label = tr("Vado a dormire verso le", "I go to sleep around"),
                    minuteOfDay = bedMinute,
                    onChange = { bedMinute = it },
                    modifier = Modifier.weight(1f),
                )
                TimeOfDayPicker(
                    label = tr("Mi sveglio verso le", "I wake up around"),
                    minuteOfDay = wakeMinute,
                    onChange = { wakeMinute = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Button(
            onClick = { finish() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(tr("Cominciamo!", "Let's begin!"), fontWeight = FontWeight.Bold)
        }
        TextButton(
            onClick = {
                ProfileRepository.setOnboarded(context)
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                tr("Salta per ora", "Skip for now"),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnboardingCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}
