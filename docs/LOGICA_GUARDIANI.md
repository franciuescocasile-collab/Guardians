# La logica dei Guardiani

> File di riferimento nel codice:
> - **Tipi e campi**: `app/src/main/java/com/guardians/app/model/GuardianTimer.kt`
> - **Regole di blocco (il motore)**: `app/src/main/java/com/guardians/app/service/MonitorService.kt`
>   (dentro `tick()`, il grande `when (effectiveType)`)
> - **Editor (come li crei)**: `app/src/main/java/com/guardians/app/ui/EditScreen.kt`
>   e i campi della bozza in `MainActivity.kt` (`TimerDraft`)

---

## Come funziona il motore, in generale
Un servizio in primo piano (`MonitorService`) controlla **ogni secondo** quale
app è in primo piano (tramite il permesso "accesso ai dati di utilizzo"). Per
ogni guardiano attivo applica la sua regola. Se la regola dice "blocca", Guardians
ti riporta alla home e mostra un popup.

- Le app **escluse** (telefono, impostazioni, il launcher, Guardians stessa) non
  vengono mai conteggiate né bloccate.
- Un guardiano agisce solo sulle app che gli assegni (o su "tutto il telefono").
- Se **disattivi** un guardiano (o la sua squadra), non interviene; ma il
  Guardiano giornaliero **continua a sapere** quanto hai usato le app (legge
  l'uso reale dal sistema), così se lo riattivi e avevi già sforato, blocca.

---

## I 9 tipi

### 1. La Sentinella (triangolo giallo)
**Uso continuo + pausa.** Ti butta fuori dopo troppo uso **di fila**.
- Campo `limitAmount`/`limitUnit`: quanto uso continuo è consentito.
- Campo `resetAmount`/`resetUnit`: la pausa obbligatoria prima di rientrare
  (0 = nessuna pausa, ti butta fuori ma puoi rientrare subito).
- **Grazia di 30 secondi**: se lasci l'app per più di 30s il conteggio riparte
  da zero ("continuo" vuol dire senza pause vere).

### 2. Il Guardiano (quadrato rosso)
**Tempo totale giornaliero, poi blocco.** Superato il tempo totale del giorno,
blocca fino al reset.
- Campo `limitAmount`/`limitUnit`: il tetto di tempo.
- Campo `resetCycle`: ogni giorno / ogni settimana / ogni mese.
- Il conteggio giornaliero è letto dall'**uso reale di sistema** (robusto).

### 3. Il Custode (cerchio blu)
**Fascia oraria protetta.** In certi orari non ti fa usare le app.
- Campi `startMinuteOfDay`/`endMinuteOfDay`: la fascia (anche a cavallo della
  mezzanotte, es. 23:00 → 07:00).

### 4. Il Gendarme (rombo viola)
**Limita le APERTURE giornaliere.** Non conta il tempo, ma quante volte apri.
- Campo `maxOpensPerDay`: aperture consentite al giorno.
- Campo `reopenCooldownMinutes`: attesa minima tra un'apertura e l'altra.
- Campo `notifyAfterOpens`: manda una notifica dopo tot aperture.
- Una riapertura entro 20 secondi non conta come "nuova apertura".

### 5. Il Messaggero (pentagono arancione)
**Non blocca: avvisa con insistenza crescente** mentre continui a usare l'app.
- Campo `limitAmount`: dopo quanto uso continuo parte il primo avviso.
- Campo `pace` (ritmo): Programmabile (cadenza fissa `resetAmount`) o
  Gentile/Media/Incalzante/Insopportabile (gli intervalli si stringono da soli).
- Campo `maxNotices`: tetto agli avvisi (0 = illimitati).
- Campo `messages`: messaggi personalizzati (vuoto = quelli predefiniti).

### 6. L'Araldo (semicerchio indaco)
**Protegge risveglio e sera imparando i tuoi ritmi.** Vedi
[LOGICA_ARALDO.md](LOGICA_ARALDO.md).

### 7. L'Esattore (esagono bronzo)
**Pedaggio d'ingresso.** A ogni apertura fa pagare un'attesa (una schermata di
respiro col countdown), per spezzare le aperture compulsive.
- Campo `limitAmount`: la durata del pedaggio (tip. 30–60 secondi).
- Campo `resetAmount`: finestra di tolleranza per uscire e rientrare senza
  ripagare (0 = pedaggio a ogni rientro).

### 8. La Vedetta (stella verde acqua)
**Agisce solo in un LUOGO.** Blocca le app solo quando sei entro un raggio da un
punto scelto.
- Campi `latitude`/`longitude`/`radiusMeters`: il luogo e il raggio.
- Campo `innerType`: il potere "prestato" (la Vedetta si comporta come un altro
  guardiano, ma solo lì).

### 9. Il Castellano (torre merlata blu-grigia)
**Sigilla le app in certi GIORNI della settimana.**
- Campo `blockedDays` (1=lun … 7=dom): nei giorni scelti le app sono del tutto
  chiuse; negli altri libere. Es. lun-ven bloccate, weekend libero.

---

## Il Custode speciale: l'Incantesimo di Congelamento
Il Congelamento è un "Custode sintetico" temporaneo che blocca **tutto** il
telefono per la durata scelta. Vedi [LOGICA_CONGELAMENTO.md](LOGICA_CONGELAMENTO.md).

---

## Dove intervenire
- **Comportamento di un tipo**: nel `when (effectiveType)` di `MonitorService.tick()`.
- **Campi/opzioni di un tipo**: `GuardianTimer.kt` (il dato) + `EditScreen.kt`
  (la UI) + `TimerDraft` in `MainActivity.kt` (la bozza).
- **Aggiungere un tipo nuovo**: c'è la checklist in cima all'enum `TimerType`.
