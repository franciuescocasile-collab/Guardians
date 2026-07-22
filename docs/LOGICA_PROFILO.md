# La logica del Profilo

> File di riferimento nel codice:
> - **Dati**: `app/src/main/java/com/guardians/app/data/ProfileRepository.kt`
> - **Schermata**: `app/src/main/java/com/guardians/app/ui/UserProfileScreen.kt`
> - **Traguardi**: `app/src/main/java/com/guardians/app/data/BadgesRepository.kt`
> - **Streak/record**: `ConductRepository.focusStreak` / `bestFocusStreak`

---

## Cosa contiene
- **Nome e stemma** (l'elmo scelto come avatar) — dall'onboarding, modificabili.
- **Obiettivo giornaliero** (minuti): il metro di giudizio personale. Sotto =
  giorno "verde" (calendario + fiamma), sopra = pesa sulla Condotta.
- **Orari indicativi** di nanna/risveglio: la base dell'Araldo finché non
  impara i tuoi orari veri.
- **Il tuo perché**: gli obiettivi scritti a mano (motivazione).

## I due riquadri
- **Giorni consecutivi obiettivo raggiunto** (fiamma): giorni PASSATI di fila
  sotto l'obiettivo. Oggi non conta (la giornata è in corso): il numero sale
  domani, a giornata chiusa. Da `focusStreak` sulle istantanee immutabili.
- **Media d'uso giornaliera storica**: media dei totali giornalieri registrati
  (da `UsageHistoryRepository.dailyAverageMs`).

## La Bacheca (i record)
- Guardiano più severo (più blocchi+respinte, da StatsRepository).
- Obiettivo rispettato di fila (record, `bestFocusStreak`).
- Congelamento più lungo (`SpellsRepository.longestFreezeMs`).
- Giorno più intenso / più leggero (dallo storico, giorni-spezzone <5min esclusi).

## I Traguardi (spille)
10 traguardi, 4 segreti (si vede lo stemma "?", non la missione). Valutati
SOLO su giorni COMPLETATI e solo dall'installazione in poi. "Senza guardiani"
esclude i giorni in cui almeno un guardiano era attivo. Immagini custom:
`res/drawable/badge_<nome>.png` sostituisce l'emoji.

## Dove intervenire
- Soglie dei traguardi: `BadgesRepository.evaluate`.
- Cosa mostra la bacheca: `UserProfileScreen.kt` (blocco `bacheca`).
