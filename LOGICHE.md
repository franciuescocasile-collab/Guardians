# LE LOGICHE DI GUARDIANS — spiegate semplici

Questo documento spiega, in italiano corrente, COME ragiona l'app: cosa
conta, cosa giudica, cosa salva. Viene aggiornato a ogni modifica delle
regole. (Ultimo aggiornamento: v3.11.0)

---

## 1. Come si conta il tempo al telefono
- L'app legge gli **eventi di sistema** (quale app va in primo piano e
  quando) una volta al secondo mentre il telefono è acceso.
- Una sola app alla volta è "in primo piano"; gli intervalli si chiudono
  quando lo schermo si spegne o il telefono si blocca.
- **Non contano mai**: Guardians stesso, la schermata home (launcher), le
  impostazioni di sistema, la barra di sistema, il telefono (chiamate) e
  **Android Auto** (va in primo piano da solo quando colleghi l'auto).
- Il "giorno" inizia all'orario scelto in Personalizzazione ("Inizio del
  giorno"), non per forza a mezzanotte.
- I guardiani controllano **ogni secondo**. In risparmio batteria profondo
  i motori si spengono e un controllo leggero riparte ogni **30 secondi**.

## 2. Cosa viene salvato (e per quanto)
- **Totale giornaliero** di ogni giorno (pochi byte al giorno).
- **Istantanea dell'obiettivo**: a fine giornata si scrive UNA volta se sei
  rimasto sotto l'obiettivo e QUALE obiettivo valeva quel giorno. Cambiare
  l'obiettivo domani NON ricolora il passato.
- Statistiche dei guardiani (blocchi, rientri), orari di nanna/risveglio
  imparati dall'Araldo, momenti del "distacco" serale (ultimi 14).
- **Regola concordata**: si tiene TUTTO per 10 anni; solo oltre i 10 anni
  si valuterà una memorizzazione "a strati". (Peso reale: ~1 MB per 5 anni.)

## 3. La Condotta (la barra rosso→verde)
- Valore interno 0..100 (mai mostrato come numero), parte da 80.
- **Cosa NON pesa mai**: le app "neutre" (macro Utility: mappe/navigatore,
  messaggistica, utilità). Un'ora di Google Maps per guidare non è
  dipendenza.
- **Veto salute**: oltre **5 ore** al giorno di tempo "che pesa" la barra
  flette verso il rosso (pendenza dolce).
- **Tetto di tossicità**: oltre **45 minuti** su Social/Giochi parte un
  drenaggio dedicato (più dolce di prima: ~0,08 punti al minuto).
- **A fine giornata**: se hai sforato obiettivo o veti, la condotta scende
  (mai più di **12 punti in un giorno**: una giornata-no non azzera tutto);
  se la giornata è pulita, risale di **7 punti**.
- **Incantesimi**: lanciare un'Ombra costa un malus secco (15) che pesa per
  10 giorni; interrompere un Grande Congelamento costa fino a 10.
- La scritta in home ("Condotta: Buona") è la traduzione a parole della
  posizione della barra (7 livelli da Eccellente a Pessima).

## 4. Il tempo scorre SEMPRE
Con Ombra, Viaggio o giorni di riposo i guardiani **non bloccano ma i
conteggi corrono**: finito l'incantesimo, se nel frattempo hai superato un
limite, il guardiano ti aspetta al varco.

## 5. Streak (la fiamma) 🔥
- Conta i giorni **già chiusi** consecutivi in cui hai rispettato
  l'obiettivo. Oggi non conta: si aggiorna domani.
- In bacheca c'è anche il **record** di sempre.

## 6. Il Sonno
- **Distacco (Punto A)**: quando posi il telefono la sera (schermo spento a
  lungo, rilevato dall'Araldo).
- **Addormentamento (Punto B)**: letto da Health Connect (Samsung Health /
  smartwatch).
- **Voto della notte (0..100)**: fino a 70 punti dalla DURATA (pieni tra 7 e
  9 ore) + fino a 30 dalle FASI (profondo ~20%, REM ~22% del totale). Senza
  smartwatch, vale la sola durata riscalata.
- **Sveglia intelligente**: cicli da 90 minuti + ~15 minuti per
  addormentarsi, calcolati da quando la attivi. Suona a fine ciclo (sonno
  leggero), anche sopra il blocco schermo. Può ripetersi nei giorni scelti
  (es. weekend): dopo lo "Spegni" si riarma da sola alla stessa ora.
- **Araldo in standby**: nascondendo la card Sonno dalla home l'Araldo
  smette di bloccare, ma i dati continuano a raccogliersi.

## 7. I guardiani (in breve)
| Guardiano | Cosa fa |
|---|---|
| Sentinella | Dopo X di uso continuo espelle e impone una pausa |
| Guardiano | Limite giornaliero totale, poi blocco fino a domani |
| Custode | Blocco in una fascia oraria |
| Gendarme | Limita le APERTURE (una vale se l'app era via da >20s) |
| Vedetta | Presta un potere solo vicino a un luogo (GPS) |
| Messaggero | Non blocca: notifiche a insistenza crescente (fino a "Insopportabile") |
| Esattore | Pedaggio di respiro a ogni ingresso |
| Araldo | Protegge risveglio e pre-nanna con gli orari imparati |

## 8. Dove stanno i numeri "magici"
Tutti i coefficienti della Condotta vivono in UN file:
`app/src/main/java/com/guardians/app/data/ConductConfig.kt` — commentato
riga per riga. Cambiare la taratura = cambiare quei numeri, senza toccare
la logica.
