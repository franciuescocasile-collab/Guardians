# La logica del Congelamento

> File di riferimento nel codice:
> - **Stato e persistenza**: `app/src/main/java/com/guardians/app/data/SpellsRepository.kt`
> - **Schermata**: `app/src/main/java/com/guardians/app/ui/CongelamentoScreen.kt`
> - **Blocco totale**: `MonitorService.kt` (cerca `isFreezeSessionActive`)
> - **Notifica di fine**: `app/src/main/java/com/guardians/app/service/FreezeEndReceiver.kt`

---

## In una frase
Il Congelamento **isola totalmente il telefono** per il tempo che scegli: mentre
è attivo, qualsiasi app (tranne telefono/impostazioni) viene bloccata. È un
"Custode temporaneo" che vale per tutto.

## Come funziona
- Scegli i minuti col **cerchio** (10–120) o con **Personalizza** (fino a 6 ore).
- Premi "Congela per X": si salva `freezeUntil = adesso + minuti`
  (`SpellsRepository.castFreeze`). Da lì il motore blocca tutto finché non scade.
- Il countdown scorre anche a schermo bloccato; l'effetto neve/ghiaccio cresce
  con la durata scelta e resta uguale anche quando la sessione parte.

## Le opzioni
- **Continua a contare dopo la scadenza** (overtime): allo 00:00 la sessione non
  finisce, parte un cronometro (+minuti) e il blocco regge finché non lo termini
  tu. Lo switch resta disponibile anche a sessione avviata. (Impostazione
  ricordata.)
- **Notificami quando finisce**: spento di default, ma la scelta è ricordata.
  Puoi scegliere tra **notifica-messaggio** o **suoneria** (tipo sveglia). La
  notifica è programmata con `AlarmManager` su `FreezeEndReceiver`.

## Interruzione
- Prima dello zero, "Interrompi" chiede conferma (è una resa).
- **Non ci sono più penali/malus** sulla Condotta per l'interruzione: la Condotta
  valuta solo il tempo speso al telefono (vedi [LOGICA_CONDOTTA.md](LOGICA_CONDOTTA.md)).
- Viene comunque aggiornato il **record** del congelamento più lungo (bacheca del
  profilo) e sbloccati eventuali **traguardi** di congelamento.

## Dove intervenire
- **Durate min/max**: `CIRCLE_MIN`/`CIRCLE_MAX`/`CUSTOM_MAX` in `CongelamentoScreen.kt`.
- **Comportamento del blocco**: la parte "freeze" in `MonitorService.tick()`.
- **Suono/testo della notifica di fine**: `FreezeEndReceiver.kt`.
