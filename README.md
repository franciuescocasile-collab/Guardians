# Guardians 🛡

App Android che limita l'uso del telefono con dei "guardiani" personalizzabili:

- **La Sentinella** (▲ triangolo giallo): dopo X tempo di uso **continuo** di un'app
  ti butta fuori (torni alla home con un popup) e ti impone una **pausa
  obbligatoria**: finché la pausa non è finita, ogni tentativo di rientrare ti
  rimanda alla home con il popup che dice quanto manca. Finita la pausa il
  conteggio riparte da zero. Vale anche prima del limite: se esci di tua
  iniziativa per almeno la durata della pausa, il conteggio riparte da zero
  (le pause più brevi invece non lo azzerano).
- **Il Guardiano** (■ quadrato rosso): dopo X tempo **totale nella giornata**
  l'app resta "bloccata" fino a mezzanotte: ogni volta che la apri vieni rimandato
  alla home con il popup.
- **Il Custode** (● cerchio blu): in una **fascia oraria** preimpostata (anche a
  cavallo della notte, es. 22:00 → 07:00) le app sorvegliate non si possono usare.
- **Il Gendarme** (◆ rombo viola): limita **quante volte al giorno** puoi aprire
  le app sorvegliate; superate le aperture consentite, blocca fino a domani
  (indipendentemente dal tempo passato dentro).
- **Il Portinaio** (⬣ esagono verde): lo **attivi tu** dal pulsante nella lista
  dei guardiani; da quel momento blocca le app per la durata scelta, senza
  possibilità di fermarlo in anticipo.

Il blocco non è a livello di sistema operativo: è un popup in sovraimpressione +
ritorno alla schermata home, come richiesto.

Ogni timer è personalizzabile: nome, tipo, durata (in **secondi, minuti o ore**)
o fascia oraria, app sorvegliate (una, molte o tutto il telefono). Puoi creare
quanti timer vuoi, anche più timer sulle stesse app: i guardiani lavorano insieme.
La pausa della Sentinella può essere 0: in quel caso butta fuori ma lascia
rientrare subito.

La schermata principale è un **hub**: da lì si va ai guardiani, alle
**Statistiche** (tempo al telefono con grafico degli ultimi 7 giorni, medie su
7/30/365 giorni, app più usate, blocchi e rientri respinti per guardiano — con
doppia conferma per azzerarle), alle **App escluse** (app che nessun guardiano
può bloccare, oltre a telefonate e impostazioni sempre al sicuro) e alle
**Impostazioni** (tema scuro/chiaro — il chiaro è bianco e blu — e vibrazione
degli avvisi). Nella scelta delle app c'è il pulsante "Seleziona tutte", che
rispetta la ricerca.

Il progetto è pensato per aggiungere **nuovi tipi di guardiano** in futuro: le
istruzioni passo-passo sono nel commento in cima a
`app/src/main/java/com/guardians/app/model/GuardianTimer.kt` (in breve: si
aggiunge una voce all'enum con nome, descrizione e colore, la sua forma nelle
icone e la sua regola nel servizio di monitoraggio — le schermate si adattano
da sole).

## Come vedere l'app sul PC (senza telefono)

1. Doppio click su **`1-avvia-emulatore.bat`** → si apre un telefono virtuale
   (la prima accensione è lenta, poi va veloce).
2. Doppio click su **`2-compila-e-installa.bat`** → compila l'app, la installa
   sull'emulatore e la avvia.
3. Dopo ogni modifica al codice: ripeti solo il passo 2.

## Come installarla sul telefono vero

**Via USB:** attiva "Debug USB" sul telefono (Impostazioni → Info sul telefono →
tocca 7 volte "Numero build" → poi Opzioni sviluppatore → Debug USB), collega il
cavo, chiudi l'emulatore e lancia `2-compila-e-installa.bat`.

**Via file:** copia `app\build\outputs\apk\debug\app-debug.apk` sul telefono
(Telegram/Drive/cavo) e aprilo. Consenti l'installazione da origini sconosciute.

## Al primo avvio dell'app

L'app chiede due permessi speciali (compaiono come card gialle in alto):

1. **Accesso ai dati di utilizzo** → serve per sapere quale app è aperta.
2. **Sovrapposizione su altre app** → serve per il popup di blocco.

Toccale e attiva "Guardians" nelle impostazioni che si aprono. Senza il primo
permesso i timer non possono funzionare.

## Struttura del progetto

- `app/src/main/java/com/guardians/app/`
  - `model/GuardianTimer.kt` — il modello del timer (tipo, minuti, app…)
  - `data/TimerRepository.kt` — salvataggio dei timer sul dispositivo
  - `service/MonitorService.kt` — il cuore: controlla ogni secondo l'app in
    primo piano e applica le regole di Sentinella e Guardiano
  - `service/OverlayManager.kt` — il popup in sovraimpressione
  - `ui/` — le schermate (lista timer, modifica, scelta app)
- `1-avvia-emulatore.bat` / `2-compila-e-installa.bat` — script rapidi
- Strumenti (JDK, SDK Android, emulatore, Gradle): installati in `C:\AndroidSdk`

## Limiti noti (versione 1)

- Se il telefono viene riavviato il monitoraggio riparte da solo, ma il tempo
  usato mentre il servizio era spento non viene contato.
- Telefonate, Impostazioni e la schermata home non vengono mai bloccate
  (per sicurezza), nemmeno con "Tutto il telefono".
- Il popup si può chiudere con "HO CAPITO", ma il Guardiano ricompare ogni volta
  che riapri un'app bloccata.
