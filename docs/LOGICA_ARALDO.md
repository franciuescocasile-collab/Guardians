# La logica dell'Araldo

> File di riferimento nel codice:
> - **Regola di blocco**: `app/src/main/java/com/guardians/app/service/MonitorService.kt`
>   (cerca `TimerType.ARALDO ->` e la funzione `trackScreenForAraldo`)
> - **Dati imparati (risveglio/nanna)**: `app/src/main/java/com/guardians/app/data/AraldoData.kt`

---

## In una frase

L'Araldo è il guardiano che **protegge i momenti fragili della giornata** — il
risveglio al mattino e la sera prima di dormire — **senza orari fissi**: impara
i tuoi ritmi osservando quando spegni e riaccendi lo schermo.

## Come impara i tuoi orari
L'app osserva le transizioni dello schermo FISICO (`trackScreenForAraldo`):

- Quando lo schermo resta **spento per almeno 4 ore consecutive**
  (`WAKE_GAP_MS`), quel periodo viene considerato una **dormita**:
  - l'inizio dello spegnimento = la tua **nanna**;
  - la riaccensione = il tuo **risveglio**.
- La **nanna abituale** è la **mediana** degli ultimi orari di addormentamento
  (servono almeno 3 notti). Finché non c'è abbastanza storico, l'Araldo usa gli
  orari **indicativi** del profilo e/o la tua **sveglia di sistema** (vedi sotto).

### Un'occhiata breve NON ti sveglia (3, 3.3)
Una notifica o uno sguardo veloce (per vedere l'ora, un messaggio, o per la
pipì notturna) **non** azzera il conteggio del sonno. Il risveglio è considerato
vero solo se lo schermo resta acceso per almeno **10 minuti**
(`ARALDO_BRIEF_WAKE_MS`); sotto quella soglia la dormita continua. Così un
messaggio in piena notte non "resetta le 4 ore".

### Un digiuno diurno NON è una dormita (3.1)
Se stai 4 ore senza toccare il telefono ma sei sveglio (studio, passeggiata…),
NON viene contato come sonno: la nanna deve **iniziare in orario notturno**
(finestra 21:00–05:00). Nota: il telefono da solo non "sa" se sei sveglio; con
uno smartwatch o i passi (Health Connect) si potrebbe capire meglio — è un
possibile miglioramento futuro, oggi ci affidiamo alla finestra notturna.

### La tua sveglia di sistema (3.2)
Sì, l'app **può leggere la prossima sveglia** che hai impostato
(`AlarmManager.getNextAlarmClock`, senza permessi speciali). L'Araldo la usa per
stimare l'ora del risveglio quando non ha ancora rilevato una dormita: se hai la
sveglia alle 07:00, sa che il blocco mattutino ha senso da lì.

## Le due fasi (le attivi tu quando crei l'Araldo)

### Fase MATTINO (`araldoMorning`)
Dal momento del **vero risveglio** blocca le app sorvegliate per la durata che
hai scelto (`limitAmount`, es. "i primi 30 minuti della giornata").
- Vale solo se il risveglio cade dentro la finestra oraria che hai impostato
  (di default 05:00–12:00).
- Il primo giorno, senza dati, parte dall'orario di risveglio indicativo del
  profilo — così protegge fin da subito.

### Fase SERA (`araldoEvening`)
Blocca le app da **un po' prima della nanna** (l'anticipo che scegli,
`resetAmount`) **fino alle 04:00**.
- Esempio: nanna abituale alle 23:30, anticipo 30 minuti → blocca dalle 23:00.

## Standby automatico
Se **nascondi la card Sonno** dalla home, l'Araldo va in **standby**: non blocca
più nulla, ma continua a raccogliere i dati di sonno. Rimostri la card e si
riattiva con lo storico intatto.

## Cosa puoi regolare
- **Nella creazione dell'Araldo**: le due fasi (mattino/sera), la durata del
  blocco mattutino, l'anticipo serale, la finestra oraria del mattino.
- **Nel codice** (`MonitorService.kt`): `WAKE_GAP_MS` (le 4 ore che definiscono
  una "dormita"), la finestra serale fissa (240 minuti = fino alle 04:00).

## Limiti onesti
- L'Araldo si basa sullo schermo spento. Se dormi col telefono acceso/in carica
  con lo schermo che si accende spesso, la rilevazione della dormita è meno
  precisa.
- La mediana della nanna si stabilizza dopo qualche notte: i primi giorni usa
  gli orari indicativi del profilo.
