# La logica del Sonno e del "Distacco dal telefono"

> File di riferimento nel codice:
> - **Dati di sonno**: `app/src/main/java/com/guardians/app/data/HealthConnectManager.kt`
> - **Nanne/risvegli imparati**: `app/src/main/java/com/guardians/app/data/AraldoData.kt`
> - **Schermata**: `app/src/main/java/com/guardians/app/ui/SleepScreen.kt`

---

## Da dove vengono i dati di sonno
Le **dormite** (durata e fasi: leggero/profondo/REM) arrivano da **Health
Connect**, il magazzino dati di salute di Android, se hai un'app tipo Samsung
Health/Fitbit che ci scrive i dati. Guardians li **legge** soltanto.

Il **distacco** invece lo calcola Guardians incrociando due cose:
- il **posare il telefono** = quando lo schermo si è spento per la nanna
  (registrato dall'Araldo, vedi [LOGICA_ARALDO.md](LOGICA_ARALDO.md));
- l'**addormentamento** = l'ora di inizio della dormita secondo Health Connect.

**Distacco = (inizio sonno secondo Health Connect) − (schermo spento).**

## ⚠️ Perché ti diceva "49 minuti" (1.1)
Qui sta il punto delicato. Quel numero **non** è "quanto prima hai posato il
telefono": è il tempo tra **quando hai spento lo schermo** e **quando Health
Connect dice che ti sei addormentato**. Se hai usato il telefono fino a un attimo
prima, lo schermo si è spento subito prima di dormire, quindi quel valore è in
pratica la tua **latenza del sonno** — quanto ci hai messo ad addormentarti dopo
aver posato il telefono, secondo il rilevatore di sonno (che ti considera
"addormentato" solo quando sei davvero immobile e i battiti calano). 49 minuti
possono voler dire semplicemente che il tuo tracker ti ha marcato "addormentato"
49 minuti dopo aver spento lo schermo.

In breve: **il valore non è un bug del conteggio**, è che l'etichetta "distacco"
è fuorviante — misura più la latenza del sonno che un vero "stacco prima di
dormire". Con il rilevamento migliorato dell'Araldo (un'occhiata breve non conta
più) il "posare il telefono" è ora più preciso, ma la differenza con l'orario di
Health Connect resta la tua latenza.

### Come vuoi che funzioni?
Dimmi tu quale ti serve e lo sistemo:
1. **Rinominarlo** in "Tempo per addormentarti dopo aver posato il telefono"
   (più onesto rispetto a quello che misura oggi).
2. **Cambiarlo** in un vero "distacco": minuti tra l'**ultimo uso reale** di
   un'app e l'addormentamento (serve leggere l'ultimo evento d'uso, non solo lo
   schermo spento).
3. **Toglierlo** del tutto se non ti convince.

## La sveglia intelligente
È spiegata a parte: [LOGICA_SVEGLIA_INTELLIGENTE.md](LOGICA_SVEGLIA_INTELLIGENTE.md).

## Dove intervenire
- **Calcolo del distacco/latenza**: `HealthConnectManager.weeklyNightScores` /
  `windDownNights`.
- **Voto della notte** (durata + fasi): `HealthConnectManager.sleepScore`.
- **Grafico della settimana**: `SleepScreen.kt`.
