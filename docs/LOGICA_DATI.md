# La logica dei DATI (memorizzazione) e del RISPARMIO ENERGETICO

> File di riferimento nel codice:
> - **Storico e ritenzione**: `app/src/main/java/com/guardians/app/data/UsageHistoryRepository.kt`
> - **Persistenza generale**: tutti i repository in `data/` (SharedPreferences)
> - **Risparmio energetico**: `MonitorService.kt` (updateBatteryPause,
>   enterDeepSleep, maybeWakeFromDeepSleep) + `SettingsRepository` (soglia)

---

## MEMORIZZAZIONE DATI

### Dove vivono i dati
Tutto in **SharedPreferences** (`guardians_prefs`), un file XML locale
dell'app, in JSON. **Niente cloud, niente server**: i dati non lasciano mai il
telefono. Disinstallare l'app li cancella.

### Cosa viene salvato
| Dato | Cosa | Quanto pesa |
|---|---|---|
| Storico d'uso | totale ms per giorno (`usage_history`) | pochi byte/giorno |
| Istantanee obiettivo | per giorno: sotto/sopra + obiettivo di quel giorno | pochi byte/giorno |
| Guardiani | definizioni complete (JSON) | ~1 KB |
| Stato del motore | conteggi in corso (ogni 15s) | ~1 KB |
| Statistiche guardiani | blocchi/respinte per guardiano | ~1 KB |
| Araldo | nanne/risvegli imparati, distacchi (ultimi 14) | ~1 KB |
| Condotta | valore 0..100 + data ultimo rollover | pochi byte |
| Traguardi | sbloccati + data installazione + giorni con guardiani | pochi byte |
| Impostazioni/profilo | preferenze, nome, obiettivo, ecc. | ~1 KB |

### Ritenzione (per quanto si tiene)
- Dettaglio giornaliero per **10 anni** (`FULL_RETENTION_DAYS = 3653`).
- Oltre, o sopra le **80.000 righe** (~2 MB), i giorni più vecchi vengono
  compattati (strati). In pratica: non te ne accorgerai mai — 5 anni ≈ 1 MB.
- Il sonno NON viene copiato: si legge da Health Connect al volo.

### Cosa NON viene salvato
I minuti per-app storici (si rileggono dal sistema quando servono, entro la
finestra che Android conserva), le ore per-fascia dei giorni vecchi, i dati di
prima dell'installazione.

## RISPARMIO ENERGETICO

### Come consuma poco il motore
- Il controllo gira **1 volta al secondo** SOLO a schermo acceso; a schermo
  spento non legge nulla (non stai usando app).
- La posizione (Vedetta) si aggiorna al massimo ogni 45 secondi e solo se
  serve.

### La soglia batteria (Impostazioni → Batteria)
Se attivi il risparmio: sotto la soglia scelta (10–20%, default 15%) e NON in
carica, i motori entrano in **sonno profondo**: ticker fermo, zero CPU, blocchi
rimossi. Un controllo leggero ogni 30 secondi + gli eventi di sistema
(carica collegata, batteria su) li risvegliano.

### Le eccezioni al sonno profondo
- La **Sveglia intelligente** armata NON viene mai fermata: il rilevamento del
  sonno resta attivo anche sotto soglia, e l'orario di suono usa
  `setAlarmClock` (suona comunque, anche in Doze, anche all'1%).
- Le notifiche programmate (fine congelamento, promemoria) usano AlarmManager:
  scattano anche coi motori fermi.

### Dove intervenire
- Soglia e default: `SettingsRepository` (batteryThreshold 10–20).
- Comportamento del sonno profondo: `MonitorService.enterDeepSleep` /
  `maybeWakeFromDeepSleep`.
- Ritenzione storico: `UsageHistoryRepository` (FULL_RETENTION_DAYS, MAX_RECORDS).
