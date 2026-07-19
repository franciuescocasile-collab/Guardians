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

Se ti alzi nel cuore della notte e poi ti riaddormenti, il conteggio riparte dal
nuovo addormentamento (ti sveglia dopo cicli **interi** dal sonno vero).

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

Se hai la batteria molto bassa e NON sei in carica, il risparmio energetico
aggressivo del telefono può fermare il rilevamento: in quel caso conviene
mettere il telefono in carica prima di dormire.

## Le manopole che puoi cambiare in `SmartAlarmRepository.kt`
- `CYCLE_MS` (90 min) — durata di un ciclo.
- `SLEEP_ONSET_MS` (20 min) — quanto schermo spento serve per dire "si è
  addormentato". Più alto = più prudente (meno falsi allarmi da telefono
  posato); più basso = parte prima.
- `FALL_ASLEEP_MS` (15 min) — margine per addormentarsi (usato solo nel calcolo
  di anteprima, non nella modalità intelligente vera).
