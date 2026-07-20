# La logica della Sveglia Intelligente

> File di riferimento nel codice:
> - **Logica**: `app/src/main/java/com/guardians/app/data/SmartAlarmRepository.kt`
> - **Rilevazione del sonno**: `app/src/main/java/com/guardians/app/service/MonitorService.kt`
>   (cerca `SVEGLIA INTELLIGENTE`)
> - **Suono/notifica di sveglia**: `app/src/main/java/com/guardians/app/service/AlarmReceiver.kt`
> - **Schermata di sveglia**: `AlarmActivity`

---

## In una frase (nuova logica di questa versione)

Non imposti un orario: imposti quanti **cicli di sonno** vuoi, poi vai a letto
quando ti pare. L'app **si accorge da sola quando ti addormenti** e da lì conta
i cicli, così ti sveglia **a fine ciclo**, nel sonno leggero (svegliarsi nel
sonno profondo è ciò che ti fa sentire distrutto).

## I cicli
Un ciclo di sonno dura circa **90 minuti** (`CYCLE_MS`). Consigliati:
- **5 cicli** ≈ 7 ore e mezza
- **6 cicli** ≈ 9 ore

## Come funziona, passo per passo
1. Scegli i cicli (pulsanti 3–7) e premi **"Attiva la sveglia"**. La sveglia
   diventa **armata**, ma **non ha ancora un orario**: è "in ascolto".
2. Vai a dormire. Quando lo schermo resta **spento in modo continuo per almeno
   20 minuti** (`SLEEP_ONSET_MS`), l'app considera che ti sei **addormentato**.
3. Da quel momento (l'istante in cui hai spento lo schermo) parte il conto dei
   cicli: **orario di sveglia = inizio sonno + (cicli × 90 minuti)**.
4. Alla fine dei cicli suona — anche a schermo bloccato — con lo **spegni** a
   pressione prolungata.

**I risvegli notturni NON spostano la sveglia** (2.1): l'orario viene fissato al
PRIMO addormentamento della notte e non cambia più. Se ti alzi alle 4 per la
pipì e ti riaddormenti, la sveglia resta all'orario calcolato quando sei andato
a letto — così chi va a letto a mezzanotte con 6 cicli si sveglia verso le 9, non
alle 13.

## Ripetizione per giorni (opzionale)
- **Vuoto** = una volta sola (dopo che suona, si disarma).
- **Con giorni scelti** (es. sab, dom) = resta armata e ti sveglia solo se il
  risveglio cade in uno di quei giorni; dopo aver suonato riparte l'attesa per
  la notte successiva.

## Notifica / suono
Quando suona parte una **suoneria d'allarme** insistente (canale `USAGE_ALARM`,
bypassa il "non disturbare"), con vibrazione, finché non premi Spegni.

## È "realizzabile" davvero? Sì, con un limite onesto
La rilevazione dell'addormentamento si basa sullo **schermo spento a lungo**.
Perché funzioni bene:
- il telefono deve poter far girare il servizio di Guardians di notte (di solito
  sì, soprattutto **in carica**);
- se stai col telefono acceso a letto, il conteggio parte solo da quando lo
  posi e resta spento ~20 minuti.

**La sveglia NON è influenzata dal risparmio energetico** (2.3): usa
`setAlarmClock`, l'API delle sveglie vere, che suona anche in Doze e a batteria
bassissima. In più, finché la sveglia è "armata" e non ha ancora agganciato
l'orario, il rilevamento del sonno resta acceso **anche** sotto la soglia di
risparmio batteria di Guardians, così non perde il momento in cui ti addormenti.

## Smartwatch (2.2) — è possibile?
Al momento **no, non in modo affidabile**. Health Connect (il magazzino dati di
salute di Android) fornisce le dormite **a posteriori**, non "in diretta" mentre
ti addormenti, quindi non può innescare la sveglia in tempo reale. Servirebbe una
app companion sul watch (Wear OS) che rilevi il sonno e avvisi il telefono: è un
progetto a parte. Per ora vale la regola dei 20 minuti di schermo spento. Se in
futuro colleghi un watch che espone il sonno in tempo reale, si può aggiungere.

## Le manopole (2.4) — chi le può cambiare?
Sono costanti **nel codice** (`SmartAlarmRepository.kt`): le cambio **io/tu come
sviluppatori** ricompilando l'app; **non** sono impostazioni per l'utente finale.
Se vuoi renderne qualcuna regolabile dall'utente (es. la soglia dei 20 minuti),
dimmelo e la porto nelle Impostazioni dell'app.
- `CYCLE_MS` (90 min) — durata di un ciclo.
- `SLEEP_ONSET_MS` (20 min) — quanto schermo spento serve per dire "si è
  addormentato". Più alto = più prudente; più basso = parte prima.
- `FALL_ASLEEP_MS` (15 min) — margine di addormentamento (solo per l'anteprima).
