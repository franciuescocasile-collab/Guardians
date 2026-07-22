# La logica delle Squadre

> File di riferimento nel codice:
> - **Dati**: `app/src/main/java/com/guardians/app/data/TeamsRepository.kt`
> - **Schermata**: `app/src/main/java/com/guardians/app/ui/TeamsScreen.kt`
> - **Uso nel motore**: `MonitorService.kt` (filtro `TeamsRepository.isTeamActiveToday`)

---

## In una frase
Una squadra è una **cartella di guardiani** che gestisci insieme: puoi
accenderli/spegnerli tutti in un colpo, dare loro un'icona e programmare in quali
**giorni della settimana** sono attivi.

## Come funziona
- Ogni guardiano appartiene a una squadra (il campo `team`); vuoto = **"Squadra
  Generale"** (la cartella di default).
- Una squadra esiste anche se è **vuota** (viene registrata quando la crei).
- Lo **switch** sulla riga della squadra accende/spegne tutti i suoi guardiani.
- I **giorni attivi** (Lun–Dom) decidono in quali giorni i guardiani della
  squadra intervengono: `TeamsRepository.isTeamActiveToday()` viene controllato
  dal motore. Fuori da quei giorni, i guardiani della squadra non bloccano (ma
  il Guardiano giornaliero continua a contare l'uso).
- **Icona** personalizzata per squadra (cartella, gruppo, o un elmo).

## La PAUSA (sostituisce del tutto gli incantesimi)
Sopra la lista delle squadre ci sono **due pulsanti**:
- **Sinistra ("Pausa…")**: chiede ogni volta quanti minuti.
- **Destra (preimpostata)**: applica subito la pausa salvata (es. 10 min);
  **pressione prolungata** per cambiarla; la prima volta chiede i minuti.

Durante la pausa i pulsanti lasciano il posto a un **banner col conto alla
rovescia** ("Pausa attiva: 08:45") e un tasto "Termina". Allo scadere ESATTO i
guardiani si riattivano **da soli** (lo stato è solo una scadenza: niente da
ricordare, niente da riaccendere). Tetto massimo: **12 ore**.

Regola importante: in pausa i guardiani NON intervengono ma **i conteggi
continuano a correre** (il Guardiano giornaliero legge l'uso reale di sistema):
appena la pausa finisce, se hai già sforato, blocca subito.

> Codice: `data/PauseRepository.kt` (stato/preset) + `PauseControls` in
> `TeamsScreen.kt` + il filtro in `MonitorService.tick()`.

## Cosa è stato RIMOSSO
Gli **incantesimi** (l'Ombra) non esistono più: al loro posto c'è la Pausa qui
sopra. Sulle squadre restano solo **cambia icona** ed **elimina** (pressione
prolungata).

## Dove intervenire
- **Giorni attivi / logica**: `TeamsRepository.kt`.
- **Aspetto e menù**: `TeamsScreen.kt`.
- **Come il motore rispetta i giorni**: il filtro `timers` in `MonitorService.tick()`.
