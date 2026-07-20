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

## Cosa è stato RIMOSSO
Gli **incantesimi** (l'Ombra che sospendeva una squadra) sono stati tolti dalle
squadre: niente più menù "Lancia Ombra" né indicatore "sospesa". Restano solo
**cambia icona** ed **elimina squadra** (pressione prolungata).

## Dove intervenire
- **Giorni attivi / logica**: `TeamsRepository.kt`.
- **Aspetto e menù**: `TeamsScreen.kt`.
- **Come il motore rispetta i giorni**: il filtro `timers` in `MonitorService.tick()`.
