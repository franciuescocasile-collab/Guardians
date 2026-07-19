# La logica della Condotta

> File di riferimento nel codice:
> - **Logica**: `app/src/main/java/com/guardians/app/data/ConductRepository.kt`
> - **Numeri regolabili (le manopole)**: `app/src/main/java/com/guardians/app/data/ConductConfig.kt`
>
> Puoi modificare i valori in `ConductConfig.kt` senza toccare la logica: sono
> tutti raccolti lì apposta. Dopo aver cambiato un numero, dimmelo e ricompilo.

---

## In una frase

La Condotta è un **voto di reputazione** da 0 a 100 (mostrato come una barra
rossa→verde, mai come numero). Non è il voto di *oggi*: è un valore che si
muove **piano nel tempo**, come la reputazione vera. Ogni notte fa un bilancio
del giorno appena chiuso; durante la giornata la barra si muove "dal vivo" in
base a quello che stai facendo adesso.

## I due pezzi

### 1. Il valore interno (0..100) — la memoria lenta
Parte da **80** (`START_VALUE`). Ogni notte, a giornata chiusa
(`dailyRollover`):

- **Se hai sforato** l'obiettivo (o il tetto salute di 5 ore), la barra **cala**
  in proporzione ai minuti di troppo. Il tempo su app social/gaming pesa molto
  di più (`ALPHA_TOXIC = 0.08` per minuto) di quello su app produttive
  (`ALPHA_PRODUCTIVE = 0.003` per minuto).
- **Un solo giorno-no non ti rovina**: il calo massimo in un giorno è limitato
  (`MAX_DAILY_DRAIN = 12`).
- **Se la giornata è pulita** (niente sforamenti), la barra **risale** di
  `DAILY_RECOVERY = 7` punti.

### 2. Il cursore dal vivo — l'oggi in tempo reale (`liveCursor`)
Parte dal valore interno e ci **sovrappone i fatti di oggi**:

- **Veto biologico**: oltre **5 ore** totali (`HEALTH_CEILING_MS`) di schermo
  "che pesa", la barra flette verso l'arancione — è salute, non disciplina.
- **Tetto di tossicità**: oltre **45 minuti** (`TOXICITY_CEILING_MS`) su
  social/gaming, la barra scivola verso il rosso.
- Il tempo **neutro** (mappe, messaggi, utilità = categoria UTILITY, peso 0)
  **non conta**: un'ora di Google Maps in auto non è dipendenza.

## I pesi delle categorie (`displacementWeight`)
Quanto "pesa" un minuto in ogni categoria:

| Categoria         | Peso |
|-------------------|------|
| Social / Gaming   | 1.0  |
| Intrattenimento   | 0.6  |
| Produttività      | 0.1  |
| Utility (neutro)  | 0.0  |

## I malus (le infrazioni gravi)
Due gesti "tolgono potere" alla Condotta subito:
- **Lanciare un Incantesimo/Ombra** (bypass della protezione): `SPELL_MALUS = 15`.
- **Interrompere un Grande Congelamento** prima della fine: fino a
  `BIG_FREEZE_MAX_MALUS = 10`, proporzionale ai minuti che mancavano (solo per
  congelamenti sopra i 30 minuti, `BIG_FREEZE_MIN_MS`).

Il malus viene applicato **una volta sola**, nel momento dell'evento, e poi la
Condotta **recupera da sola** nei giorni puliti.

### ⚠️ Bug corretto in questa versione
Prima il malus veniva sottratto **una volta subito E POI di nuovo ogni notte**
per 10 giorni: bastava interrompere UN congelamento per far crollare la Condotta
al rosso e tenercela incollata per più di una settimana, anche se ti comportavi
benissimo. **Questo era il motivo per cui la tua Condotta era "PESSIMA" pur
essendo sotto l'obiettivo e sotto le 4 ore.** Ora il malus si applica una sola
volta. In più, con questa versione la Condotta di tutti **riparte pulita da 80**
(ricalibrazione del motore).

## Le manopole che puoi cambiare in `ConductConfig.kt`
- `START_VALUE` (80) — da dove parte la barra.
- `HEALTH_CEILING_MS` (5 ore) — soglia salute.
- `TOXICITY_CEILING_MS` (45 min) — soglia social/gaming.
- `ALPHA_TOXIC` / `ALPHA_PRODUCTIVE` — quanto punge lo sforamento al minuto.
- `MAX_DAILY_DRAIN` (12) — calo massimo in un giorno.
- `DAILY_RECOVERY` (7) — recupero nei giorni puliti.
- `SPELL_MALUS` (15), `BIG_FREEZE_MAX_MALUS` (10), `MALUS_WINDOW_DAYS` (10).
- `displacementWeight` — i pesi delle categorie.

Se vuoi una Condotta **più indulgente**: alza `DAILY_RECOVERY`, abbassa
`ALPHA_TOXIC`, alza `TOXICITY_CEILING_MS`. Per una **più severa**: il contrario.
