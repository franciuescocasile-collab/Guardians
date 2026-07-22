# La logica delle Statistiche

> File di riferimento nel codice:
> - **Lettura dell'uso**: `app/src/main/java/com/guardians/app/data/UsageAnalytics.kt`
> - **Schermata L1/L2**: `app/src/main/java/com/guardians/app/ui/StatsScreen.kt`
> - **Dettaglio (Utilizzo/Media, calendario)**: `app/src/main/java/com/guardians/app/ui/StatsExtras.kt`
> - **Storico**: `app/src/main/java/com/guardians/app/data/UsageHistoryRepository.kt`

---

## Da dove vengono i numeri (RISPOSTA alla domanda 2)
Guardians fa **entrambe le cose**:
1. **Legge dal sistema Android** (`UsageStatsManager`, lo stesso archivio che
   alimenta "Benessere digitale"): gli eventi "app X in primo piano alle
   HH:MM". Da lì calcola oggi, le ore, le app, le categorie. NON passa
   dall'app Benessere: parla direttamente col sistema.
2. **Memorizza il suo storico**: ogni giorno chiuso salva il TOTALE del giorno
   (pochi byte) in `UsageHistoryRepository`, più l'istantanea dell'obiettivo.
   Questo storico è SUO, parte dall'installazione e sopravvive nel tempo (il
   sistema Android invece dimentica i dettagli dopo pochi giorni/settimane).

"Uso reale di sistema (robusto)" per il Guardiano giornaliero significa: il
conteggio non si basa su un accumulatore interno fragile (che si azzererebbe
se spegni/riaccendi il guardiano), ma viene RILETTO dagli eventi di sistema
di oggi — così anche dopo una pausa il guardiano sa quanto hai usato davvero.

## Cosa è escluso dal conteggio
Guardians stesso, launcher (home), impostazioni, systemui, Android Auto.
Le app nelle **Esclusioni** dell'utente non vengono né contate né bloccate
(anche se sorvegliate da un guardiano: l'esclusione vince).

## Il grafico della home (WeekChart2)
Barre della settimana corrente + due guide grigie: la media generale
(tratteggiata) e la "media relativa al giorno" (per giorno della settimana, in
secondo piano). Tocca una barra per il tempo esatto. Riferimenti orari a
sinistra (1h/2h/4h a seconda della scala).

## Il Dettaglio: UTILIZZO e MEDIA
- **Utilizzo** (totali): Giornaliero (linea per ora, oggi/ieri/l'altro ieri),
  Settimanale (questa/scorsa), Mensile (ultime 5 settimane), Annuale (per
  mese, fino a 2 anni indietro). Swipe/frecce = viaggio nel tempo.
- **Media**: Giornaliero (andamento medio della giornata su 4 settimane),
  Settimanale (media per giorno su 4 settimane), Mensile (media a settimana),
  Annuale (media giornaliera per mese, 2 anni indietro).
- Tocca una barra per il valore preciso; oltre 100h solo il numero ("in ore").

## Il calendario dell'obiettivo
Verde = giorno sotto l'obiettivo, rosso = sopra. Ogni giorno è
un'ISTANTANEA scritta una sola volta a fine giornata con l'obiettivo di QUEL
giorno: cambiare l'obiettivo non ricolora il passato.

## Categorie e torta
Ogni app ha una categoria (auto-riconosciuta) che puoi CAMBIARE, anche con
categorie create da te (`CategoryRepository`). La torta degli ultimi 7 giorni
rispetta le tue scelte.

## Dove intervenire
- Serie dei grafici: `StatsExtras.kt` (hourlySeriesFor, weekUsageSeries, …).
- Esclusioni fisse: `UsageAnalytics.ignored`.
- Ritenzione storico: `UsageHistoryRepository` (vedi LOGICA_DATI.md).
