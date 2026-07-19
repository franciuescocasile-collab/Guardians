package com.guardians.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Le Novità mostrate dalla campanella in home: annunci e note di versione,
 * scritti direttamente nell'app a ogni build. Il pallino sulla campanella
 * appare finché ci sono voci non ancora lette (conteggio salvato nelle prefs).
 */
object NewsRepository {

    private const val PREFS = "guardians_prefs"
    private const val KEY_READ_COUNT = "news_read_count"

    /** Un annuncio: versione di riferimento + titolo e corpo bilingui. */
    class NewsItem(
        val version: String,
        val titleIt: String,
        val titleEn: String,
        val bodyIt: String,
        val bodyEn: String,
    ) {
        val title: String get() = tr(titleIt, titleEn)
        val body: String get() = tr(bodyIt, bodyEn)
    }

    /** Dal più recente al più vecchio. Aggiungere IN CIMA la voce di ogni build. */
    val items: List<NewsItem> = listOf(
        NewsItem(
            "3.16.0",
            "Sveglia davvero intelligente, condotta sistemata e mille correzioni",
            "Truly smart alarm, fixed conduct and a thousand fixes",
            "CONDOTTA: corretto un bug per cui una sola interruzione di " +
                "congelamento faceva crollare la condotta al rosso per giorni. " +
                "Ora riparte pulita. SVEGLIA INTELLIGENTE rifatta: scegli i cicli " +
                "coi pulsanti (niente più slider ostico), poi vai a dormire quando " +
                "vuoi — l'app si accorge da sola quando ti addormenti e ti sveglia " +
                "a fine ciclo. CONGELAMENTO: il cerchio resta grande (via il " +
                "quadrato), lo switch resta durante la sessione, e puoi farti " +
                "notificare a fine timer con messaggio o suoneria; la pagina non " +
                "scorre più. GRAFICI: sfondo allineato alle colonne, tocca una " +
                "barra per il dettaglio, nuovo grafico Media Giornaliera " +
                "dell'ultimo mese, numeri grandi leggibili oltre le 100h. Il " +
                "Castellano ora ha il suo elmo e i giorni stanno su una riga. " +
                "TRAGUARDI: sbloccati solo a giornata conclusa e solo dopo " +
                "l'installazione (niente più falsi sblocchi); puoi mettere " +
                "immagini tue al posto delle emoji. Profilo: scritte sopra i " +
                "valori. E altro ancora.",
            "CONDUCT: fixed a bug where a single freeze interruption tanked your " +
                "conduct to red for days. It now restarts clean. SMART ALARM " +
                "redone: pick cycles with buttons (no more fiddly slider), then " +
                "go to bed whenever — the app notices when you fall asleep and " +
                "wakes you at the end of a cycle. FREEZE: the circle stays big " +
                "(no more square), the switch stays during the session, and you " +
                "can be notified at the end with a message or a ringtone; the " +
                "page no longer scrolls. CHARTS: background aligned to columns, " +
                "tap a bar for details, new Daily Average chart for the last " +
                "month, big readable numbers beyond 100h. The Castellan now has " +
                "its helm and the days fit on one row. ACHIEVEMENTS: unlocked " +
                "only at day's end and only after install (no more false " +
                "unlocks); you can use your own images instead of emoji. " +
                "Profile: labels above the values. And more.",
        ),
        NewsItem(
            "3.15.0",
            "Grafici rivoluzionati, il Castellano e i Traguardi",
            "Revolutionized charts, the Castellan and Achievements",
            "Tempo al telefono rifatto: due sezioni, UTILIZZO (totali) e MEDIA, " +
                "ognuna con Giornaliero/Settimanale/Mensile/Annuale e il VIAGGIO " +
                "NEL TEMPO — scorri o usa le frecce per rivedere ieri, la " +
                "settimana scorsa, gli anni passati. Ora ogni grafico ha i " +
                "riferimenti orari a lato ben distanziati. Nuovo guardiano: il " +
                "CASTELLANO, che sigilla le app in certi giorni della settimana " +
                "(es. lun-ven bloccate, weekend liberi). Nuovi TRAGUARDI: spille " +
                "colorate che sblocchi con le tue imprese, alcune segrete (vedi " +
                "lo stemma ma non la missione). Nel profilo, i due riquadri " +
                "media/giorni hanno la stessa dimensione. La Sentinella ora ha " +
                "una grazia di 30 secondi: se torni dopo tanto, il conteggio " +
                "riparte. Home: puoi mettere le card DOVE VUOI (anche statistiche " +
                "sotto sonno). Congelamento: tasto \"Interrompi\" più pulito, " +
                "niente scritte al centro, la neve resta calma come quando la " +
                "imposti. Squadre senza incantesimi. Editor: scegli il tipo e " +
                "scendi alle opzioni, con una barra divisoria. Scegli le app e " +
                "torni dov'eri. Lingua nell'angolo in alto a destra all'avvio. " +
                "Nuova guida sullo shopping online.",
            "Screen time redone: two sections, USAGE (totals) and AVERAGE, each " +
                "with Daily/Weekly/Monthly/Yearly and TIME TRAVEL — swipe or use " +
                "the arrows to revisit yesterday, last week, past years. Every " +
                "chart now has well-spaced side hour marks. New guardian: the " +
                "CASTELLAN, which seals apps on certain weekdays (e.g. Mon-Fri " +
                "blocked, weekends free). New ACHIEVEMENTS: colorful pins you " +
                "unlock with your feats, some secret (you see the crest but not " +
                "the mission). In the profile, the average/days tiles are the " +
                "same size. The Sentinel now has a 30-second grace: come back " +
                "after a while and the count restarts. Home: put cards WHEREVER " +
                "you want (even stats under sleep). Freeze: cleaner \"Break it\" " +
                "button, no center text, snow stays calm like when you set it. " +
                "Teams without spells. Editor: pick a type and scroll to its " +
                "options, with a divider. Pick apps and return where you were. " +
                "Language in the top-right corner at first launch. New online " +
                "shopping guide.",
        ),
        NewsItem(
            "3.14.0",
            "Trascina e le card fanno spazio (e molto altro)",
            "Drag and the cards make room (and much more)",
            "Nella home, mentre trascini una card le altre SCIVOLANO per farle " +
                "spazio. Il grafico del tempo ha i riferimenti orari a lato (2h, " +
                "4h…), colonne distinguibili e l'obiettivo nascosto di default " +
                "(lo accendi con un tocco). La torta rispetta le TUE categorie, " +
                "anche quelle create da te, e il dialogo mostra il logo dell'app. " +
                "Nell'editor: scegli il tipo e la pagina scende alle opzioni, " +
                "PIÙ preavvisi col pulsante Aggiungi, e le app scelte si vedono " +
                "come etichette. Il selettore app ora si apre SUBITO (icone in " +
                "arrivo mentre scorri). All'inizio l'app chiede \"Come ti " +
                "chiami?\", l'obiettivo si imposta con la barra a passi di 30 " +
                "minuti (anche 3h30) e spiega a cosa serve. Nuova guida sullo " +
                "shopping online. Nel sonno: giorni sopra, voto attaccato alla " +
                "barra.",
            "On home, while you drag a card the others SLIDE to make room. The " +
                "time chart gains side hour marks (2h, 4h…), distinguishable " +
                "columns and the goal hidden by default (turn it on with a " +
                "tap). The pie respects YOUR categories, including the ones you " +
                "created, and the dialog shows the app logo. In the editor: " +
                "pick a type and the page scrolls to its options, MULTIPLE " +
                "warnings via the Add button, and chosen apps show as chips. " +
                "The app picker now opens INSTANTLY (icons load as you " +
                "scroll). Onboarding asks \"What's your name?\", the goal uses " +
                "a 30-minute-step slider (3h30 works) and explains its purpose. " +
                "New online-shopping guide. In sleep: days on top, score " +
                "attached to the bar.",
        ),
        NewsItem(
            "3.13.0",
            "Slide tra le pagine, categorie tue e promemoria ricorrenti",
            "Page slides, your own categories and recurring reminders",
            "L'app ora SCORRE: entri in una pagina e arriva da destra, torni " +
                "indietro e scivola via. Il tocco sulle card si sente davvero " +
                "(schiaccia e rimbalza, anche sul tap veloce) e il trascinamento " +
                "delle card segue il dito, spostandosi di più posizioni in un " +
                "colpo. In \"Tutte le app di oggi\" tocchi un'app e le CAMBI " +
                "categoria — o ne CREI una tutta tua, con nome e colore. Il " +
                "Notificatore impara i promemoria RICORRENTI (\"bevi acqua ogni " +
                "2 ore\": lo imposti una volta sola). La card Sonno è pulita: " +
                "ultima dormita, distacco di stanotte e distacco medio; il voto " +
                "del sonno ora premia la QUALITÀ (fasi) più della durata. La " +
                "neve è tornata a velocità umana, la fiamma animata vive anche " +
                "nel profilo e l'icona dell'app ha lo scudo stondato.",
            "The app now SLIDES: enter a page and it comes from the right, go " +
                "back and it slips away. Card presses truly feel like presses " +
                "(squash and bounce, even on quick taps) and dragging cards " +
                "follows your finger, jumping several positions at once. In " +
                "\"All of today's apps\" tap an app to CHANGE its category — or " +
                "CREATE your own, with a name and color. The Notifier learns " +
                "RECURRING reminders (\"drink water every 2 hours\": set it " +
                "once). The Sleep card is clean: last night, tonight's detach " +
                "and average detach; the sleep score now rewards QUALITY " +
                "(stages) over duration. Snow is back to human speed, the " +
                "animated flame lives in the profile too and the app icon got " +
                "its rounded shield.",
        ),
        NewsItem(
            "3.12.0",
            "Colore dell'app, nuova icona e tanti ritocchi",
            "App color, new icon and lots of polish",
            "Ora puoi scegliere il COLORE dell'app (Personalizzazione → Colore " +
                "dell'app): la palette di un guardiano, o quella del tuo stemma con " +
                "un interruttore. La nuova icona dell'app ha i tre elmi (Sentinella, " +
                "Guardiano, Custode) dentro lo scudo. Il fuocherello ora è una vera " +
                "fiamma in movimento col numero al centro, e la Condotta sta sopra " +
                "la barra. La neve del Congelamento accelera davvero col timer. " +
                "Sistemi la home direttamente lì: tieni premuto, trascina con le " +
                "lineette, spegni le sezioni con lo switch (restano lì, sbiadite, " +
                "pronte a tornare). I grafici sono tutti della stessa altezza e la " +
                "sveglia si può ripetere a giorni. E lo storico ora si conserva per " +
                "10 anni (poi a medie mensili).",
            "You can now pick the APP COLOR (Personalization → App color): a " +
                "guardian's palette, or your crest's with a switch. The new app " +
                "icon shows the three helms (Sentinel, Guardian, Keeper) inside the " +
                "shield. The little flame is now a real moving fire with the number " +
                "in its center, and Conduct sits above the bar. The Freeze snow " +
                "truly speeds up with the timer. You arrange the home right there: " +
                "long-press, drag with the handles, switch sections off (they stay, " +
                "faded, ready to return). Charts are all the same height and the " +
                "alarm can repeat on days. And history is now kept for 10 years " +
                "(then monthly averages).",
        ),
        NewsItem(
            "3.11.0",
            "La home si sistema da sola (e nevica)",
            "Rearrange home in place (and it snows)",
            "Tieni premuto su una card della home: compaiono le lineette per " +
                "trascinarla dove vuoi e lo switch per nasconderla; in fondo, " +
                "\"Aggiungi una card\" per rimettere le sezioni nascoste. Le card " +
                "ora si SCHIACCIANO quando le premi, come pulsanti veri. Nel " +
                "Congelamento cadono i fiocchi con una leggera brezza (più tempo " +
                "= più neve e più vento) e i raggi agli angoli sono spariti. La " +
                "sveglia si può ripetere nei giorni scelti (es. solo weekend). Il " +
                "commentino del calendario mostra l'obiettivo DI QUEL giorno, il " +
                "calendario è più compatto e i grafici lasciano vuoto il futuro, " +
                "non il passato. Fiamma finalmente leggibile, categorie delle app " +
                "corrette e in Impostazioni trovi LOGICHE.md e la cartella Icone.",
            "Long-press a home card: drag handles appear to move it and a " +
                "switch to hide it; at the bottom, \"Add a card\" brings hidden " +
                "sections back. Cards now SQUISH when pressed, like real buttons. " +
                "In Freeze, snowflakes fall with a light breeze (more time = more " +
                "snow and wind) and the corner rays are gone. The alarm can " +
                "repeat on chosen days (e.g. weekends only). The calendar tooltip " +
                "shows THAT day's goal, the calendar is more compact and charts " +
                "leave the future empty, not the past. The flame is finally " +
                "readable, app categories are fixed, and you'll find LOGICHE.md " +
                "and the Icons folder in the project.",
        ),
        NewsItem(
            "3.10.0",
            "La Sveglia Intelligente e i voti al sonno",
            "The Smart Alarm and sleep scores",
            "Nella pagina Sonno arriva la SVEGLIA INTELLIGENTE: scegli i cicli " +
                "di sonno sulla mezzaluna (consigliati 5 o 6) e lei suona a fine " +
                "ciclo, anche sopra il blocco schermo, con lo Spegni a pressione " +
                "prolungata e il Rimanda di 9 minuti. Sotto, la settimana di " +
                "sonno: un voto per ogni notte (durata + fasi dallo smartwatch) e " +
                "quanto avevi staccato il telefono prima di dormire. E poi: il " +
                "risparmio batteria ricontrolla ogni 30 secondi (non più 5 " +
                "minuti), le squadre hanno icone personalizzabili, la bacheca " +
                "guadagna i record (striscia migliore, congelamento più lungo, " +
                "giorno più intenso e più leggero), il ghiaccio del Congelamento " +
                "cresce mentre giri il disco, gli stemmi si scelgono in grande e " +
                "la gestione della homepage vive in Personalizzazione.",
            "The Sleep page gains the SMART ALARM: pick your sleep cycles on the " +
                "crescent (5 or 6 recommended) and it rings at the end of a " +
                "cycle, even over the lock screen, with long-press Stop and a " +
                "9-minute Snooze. Below, your week of sleep: a score for each " +
                "night (duration + smartwatch stages) and how long you'd put the " +
                "phone down before sleeping. Also: battery saver rechecks every " +
                "30 seconds (no longer 5 minutes), teams get custom icons, the " +
                "highlights board gains records (best streak, longest freeze, " +
                "heaviest and lightest day), the Freeze ice grows as you turn " +
                "the dial, crests are picked big, and home-page management moved " +
                "into Personalization.",
        ),
        NewsItem(
            "3.9.0",
            "I grafici rifatti come si deve",
            "Charts redone properly",
            "Il grafico \"Tempo al telefono\" è nuovo: barre colorate più alte e " +
                "tre guide in scala di grigio — la media di QUEL giorno della " +
                "settimana (linea con tratteggio sotto), la media generale " +
                "(tratteggiata) e l'obiettivo (continua) — con la legenda. I " +
                "pulsanti Mese/Anno sono spariti: tutto vive nel Dettaglio, dove " +
                "il carosello ha ora 4 grafici (Giornaliero a linea con pallini " +
                "TOCCABILI che mostrano i minuti dell'ora, Settimanale, Mensile " +
                "con le settimane S1…S52 e Annuale) e le linguette si cliccano " +
                "oltre allo swipe. Nel calendario dell'obiettivo, tocca un giorno " +
                "per vedere uso, obiettivo e di quanto hai sforato o sei sotto. " +
                "E il Messaggero e la Vedetta hanno i loro nuovi elmi.",
            "The \"Screen time\" chart is new: taller colored bars plus three " +
                "grayscale guides — THAT weekday's average (line with hatching " +
                "below), the overall average (dashed) and the goal (solid) — with " +
                "a legend. The Month/Year buttons are gone: everything lives in " +
                "Details, where the carousel now has 4 charts (Daily as a line " +
                "with TAPPABLE dots showing each hour's minutes, Weekly, Monthly " +
                "with S1…S52 weeks and Yearly) and the tabs are clickable besides " +
                "swiping. In the goal calendar, tap a day to see usage, goal and " +
                "how much you went over or stayed under. And the Messenger and " +
                "Lookout got their new helms.",
        ),
        NewsItem(
            "3.8.0",
            "Sonno, homepage su misura e vetro ghiacciato",
            "Sleep, tailored home and frosted glass",
            "Nuova card SONNO in home: dentro ci sono l'ultima dormita, il legame " +
                "Distacco→Sonno e l'Araldo coi suoi orari (via dal profilo, dove " +
                "l'obiettivo è salito sotto il nome). Da Impostazioni → \"La " +
                "homepage\" scegli quali card vedere e in che ordine; nascondendo " +
                "il Sonno l'Araldo va in standby (i dati continuano a raccogliersi). " +
                "Il tempo ora SCORRE anche sotto incantesimo o viaggio: niente " +
                "blocchi, ma i conteggi corrono. Il Messaggero guadagna il livello " +
                "Insopportabile. Nelle statistiche: app più usate in cima (tocca " +
                "per vedere OGNI app, col pallino della categoria). La fiamma " +
                "dello streak è più grande col numero dentro, le impostazioni " +
                "hanno la ricerca, il filtro notturno del Notificatore vive nei " +
                "3 puntini e il Congelamento indossa il vetro ghiacciato.",
            "New SLEEP card on home: last night's sleep, the Detach→Sleep link " +
                "and the Herald with its times (moved out of the profile, where " +
                "the goal now sits under your name). From Settings → \"The home " +
                "page\" choose which cards to show and their order; hiding Sleep " +
                "puts the Herald on standby (data keeps being collected). Time now " +
                "FLOWS even under spells or travel: no blocks, but counters run. " +
                "The Messenger gains the Unbearable pace. In statistics: most-used " +
                "apps on top (tap to see EVERY app, with its category dot). The " +
                "streak flame is bigger with the number inside, settings got a " +
                "search bar, the Notifier's night filter lives in the 3-dot menu " +
                "and the Freeze wears frosted glass.",
        ),
        NewsItem(
            "3.7.0",
            "Sagome ritagliate e condotta in prima fila",
            "Cut-out silhouettes and conduct up front",
            "Gli elmi dei guardiani ora sono sagome ritagliate (niente più " +
                "riquadro blu) e con le teste della stessa misura. In home, al " +
                "posto del saluto, c'è la valutazione della Condotta a colori; la " +
                "barra è stata tolta dal profilo. Android Auto e la schermata home " +
                "non contano più nel tempo di utilizzo, e le app \"neutre\" come " +
                "Google Maps non pesano più sulla condotta: un'ora di navigatore " +
                "non è dipendenza. L'app resta sempre verticale. Impostazioni " +
                "riordinate: batteria, sigillo e app escluse in alto, e le " +
                "preferenze di gusto in una nuova pagina \"Personalizzazione\". " +
                "Il popup del Congelamento mostra il fiocco di neve, le Squadre " +
                "hanno un'icona di gruppo e la ricerca ignora gli spazi.",
            "Guardian helms are now cut-out silhouettes (no more blue box) with " +
                "evenly sized heads. On home, the greeting gave way to the colored " +
                "Conduct rating; the bar left the profile. Android Auto and the " +
                "launcher no longer count as screen time, and \"neutral\" apps like " +
                "Google Maps no longer weigh on conduct: an hour of navigation " +
                "isn't addiction. The app stays portrait. Settings reordered: " +
                "battery, seal and excluded apps on top, taste preferences in a new " +
                "\"Personalization\" page. The Freeze popup shows a snowflake, " +
                "Teams got a group icon, and search ignores spaces.",
        ),
        NewsItem(
            "3.6.0",
            "I guardiani indossano l'elmo",
            "The guardians don their helms",
            "I guardiani ora hanno le loro vere icone: gli elmi che hai disegnato, " +
                "uno per tipo, in tutta l'app e come stemma del profilo (il " +
                "Messaggero aspetta ancora il suo). Le impostazioni della batteria " +
                "hanno una pagina dedicata con \"Risparmio batteria\" e \"Tienimi " +
                "sempre attivo\" (ora un interruttore che ti porta a concedere il " +
                "permesso). E nelle Statistiche le app più usate ora sono le 4 " +
                "principali, più pulite.",
            "The guardians now have their real icons: the helms you designed, one " +
                "per type, across the app and as your profile crest (the Messenger " +
                "still awaits its own). Battery settings get their own page with " +
                "\"Battery saver\" and \"Keep me always running\" (now a switch that " +
                "takes you to grant the permission). And in Statistics the most-used " +
                "apps are now the top 4, cleaner.",
        ),
        NewsItem(
            "3.5.0",
            "Distacco → Sonno e telefono sempre di guardia",
            "Detach → Sleep and always on duty",
            "Nuovo grafico nelle Statistiche: incrocia il momento in cui posi il " +
                "telefono la sera con l'ora in cui ti addormenti (letta da Health " +
                "Connect) e ti mostra, sui tuoi dati, quanto dormi di più quando " +
                "stacchi almeno mezz'ora prima. E nelle Impostazioni un tasto " +
                "\"Tienimi sempre attivo\" ti porta a esentare Guardians " +
                "dall'ottimizzazione batteria, così i telefoni che lo mettono a " +
                "dormire durante i giochi non lo fermano più.",
            "New chart in Statistics: it crosses the moment you put the phone down " +
                "in the evening with the time you fall asleep (read from Health " +
                "Connect) and shows, on your own data, how much more you sleep when " +
                "you detach at least half an hour earlier. And in Settings a \"Keep " +
                "me always running\" button takes you to exempt Guardians from " +
                "battery optimization, so phones that put it to sleep during games " +
                "can't stop it anymore.",
        ),
        NewsItem(
            "3.4.0",
            "Condotta più giusta e ritocchi vari",
            "Fairer conduct and assorted fixes",
            "La Condotta è più indulgente: la soglia di salute sale a 5 ore, i cali " +
                "sono più lenti e nessuna singola giornata-no può far crollare la " +
                "barra. La condotta riparte pulita con questa taratura. Il giallo " +
                "della barra è ora centrato. Lo streak conta solo i giorni già " +
                "chiusi: oggi non conta, sale da domani. Il resoconto settimanale " +
                "arriva la mattina quando ti svegli, non più a mezzanotte. La card " +
                "del Notificatore è nascosta di default e si riattiva dalle " +
                "Impostazioni (i promemoria restano salvati). Nella pausa della " +
                "Sentinella ora vedi anche i secondi. E se l'app si chiude da sola, " +
                "salva di nascosto il motivo per poterlo poi correggere.",
            "Conduct is gentler: the health threshold rises to 5 hours, drops are " +
                "slower, and no single bad day can crash the bar. Conduct restarts " +
                "clean with this tuning. The bar's yellow is now centered. The " +
                "streak only counts already-closed days: today doesn't count, it " +
                "rises tomorrow. The weekly report arrives in the morning when you " +
                "wake up, no longer at midnight. The Notifier card is hidden by " +
                "default and can be re-enabled in Settings (reminders stay saved). " +
                "The Sentinel's break now shows seconds too. And if the app closes " +
                "on its own, it quietly saves why, so it can be fixed later.",
        ),
        NewsItem(
            "3.3.0",
            "Grafici più puliti e profilo più ricco",
            "Cleaner charts and a richer profile",
            "Le barre dei grafici ora partono tutte dalla stessa base, con i " +
                "minuti sopra e l'ora/il giorno su un'unica riga sotto. I grafici a " +
                "linea sono diventati curve morbide, con le etichette sull'asse e " +
                "senza più la scritta \"max\". Lo streak si calcola dal vivo " +
                "sull'obiettivo: se oggi lo superi, riparte da zero. Nel profilo " +
                "arriva la bacheca del guardiano che ti ha fermato di più. La " +
                "sezione obiettivo è più compatta e la Modalità Viaggio conta tutto " +
                "normalmente: serve solo a mettere in pausa i blocchi. Nelle squadre " +
                "interruttore e giorni di servizio ora stanno in un'unica card, e il " +
                "resoconto settimanale parte dal tuo primo giorno della settimana.",
            "Chart bars now all start from the same baseline, with minutes on top " +
                "and the hour/day on a single line below. Line charts became smooth " +
                "curves, with axis labels and no more \"max\" caption. The streak is " +
                "computed live against your goal: go over it today and it resets to " +
                "zero. Your profile gains a board of the guardian that blocked you " +
                "most. The goal section is more compact, and Travel Mode counts " +
                "everything normally: it only pauses the blocks. In teams the switch " +
                "and on-duty days now share a single card, and the weekly report " +
                "starts on your chosen first day of the week.",
        ),
        NewsItem(
            "3.2.0",
            "Grafici, condotta e viaggi su misura",
            "Charts, conduct and tailored travel",
            "Barra della condotta invertita (rosso a sinistra, verde a destra) " +
                "anche in home accanto al nome. Il grafico giornaliero mostra ora " +
                "le vere ultime 24 ore, con toggle barre/linea chiaro e niente " +
                "barre vuote. WhatsApp e la messaggistica non penalizzano più la " +
                "condotta. La Modalità Viaggio ora conta nelle medie (ma pesa meno " +
                "sulla condotta) ed è personalizzabile a ore o giorni. Notifiche " +
                "in una pagina dedicata, con l'opzione per nascondere quella fissa.",
            "Conduct bar flipped (red left, green right), now also on the home " +
                "next to your name. The daily chart shows the real last 24 hours, " +
                "with a clear bar/line toggle and no empty bars. WhatsApp and " +
                "messaging no longer hurt your conduct. Travel Mode now counts in " +
                "your averages (but weighs less on conduct) and is customizable in " +
                "hours or days. Notifications get their own page, with an option to " +
                "hide the persistent one.",
        ),
        NewsItem(
            "3.1.0",
            "Rifinitura visiva della Condotta",
            "Conduct visual refinement",
            "La Barra della Condotta ora dice a parole come stai andando " +
                "(da Eccellente a Pessima), con più verde a disposizione e un " +
                "cursore sottile. Nel profilo etichette più chiare e un tocco " +
                "\"i\" con tooltip leggero. La card Statistiche in home è tornata " +
                "fissa e pulita, il grafico dettagliato ha di nuovo il selettore " +
                "barre/linea, e la Modalità Viaggio si trova ora nelle Impostazioni.",
            "The Conduct Bar now tells you in words how you're doing (from " +
                "Excellent to Poor), with more green room and a slim cursor. " +
                "Clearer labels in the profile and a light \"i\" tooltip. The home " +
                "Statistics card is static and clean again, the detailed chart has " +
                "its bar/line switch back, and Travel Mode now lives in Settings.",
        ),
        NewsItem(
            "3.0.0",
            "Il Motore della Condotta",
            "The Conduct Engine",
            "Guardians non conta più solo il tempo: ne legge la qualità. Nel " +
                "Profilo arriva la Barra della Condotta (Verde→Rosso, nessun " +
                "voto): un output passivo dei tuoi fatti reali, con veto " +
                "biologico oltre le 4 ore e il calcolo del tempo risparmiato. " +
                "Nuova Modalità Viaggio (sospende i guardiani e isola le " +
                "statistiche), promemoria usa-e-getta con filtro notturno, " +
                "servizio a prova di crash e grafici premium (linea, indicatori, " +
                "calendario navigabile).",
            "Guardians no longer just counts time: it reads its quality. Your " +
                "Profile gains the Conduct Bar (Green→Red, no grades): a passive " +
                "output of your real facts, with a biological veto past 4 hours " +
                "and a time-saved estimate. New Travel Mode (pauses guardians and " +
                "isolates stats), one-time reminders with a night filter, a " +
                "crash-proof service and premium charts (line, indicators, a " +
                "navigable calendar).",
        ),
        NewsItem(
            "2.0.0",
            "Il Risveglio dei Guardiani",
            "The Awakening of the Guardians",
            "La prima major release! Nuova home con il tuo profilo in cima e le " +
                "descrizioni sotto ogni card. Android Auto non conta più come " +
                "tempo di schermo. Statistiche su tre livelli: grafico a linea, " +
                "carosello e calendario dell'obiettivo (istantanee immutabili). " +
                "Arriva Il Notificatore per i promemoria, la pianificazione " +
                "settimanale delle squadre, il congelamento con effetto brina e " +
                "senso orario, e i suggerimenti intelligenti dentro le Guide.",
            "The first major release! New home with your profile on top and a " +
                "description under every card. Android Auto no longer counts as " +
                "screen time. Three-level statistics: line chart, carousel and " +
                "goal calendar (immutable snapshots). Meet The Notifier for " +
                "reminders, weekly team scheduling, the frost-effect clockwise " +
                "Freeze, and smart recommendations inside the Guides.",
        ),
        NewsItem(
            "1.9.1",
            "Il Cerchio del Gelo",
            "The Circle of Frost",
            "Il Congelamento ha la sua pagina: scorri il dito lungo il cerchio " +
                "per scegliere da 10 a 120 minuti (oltre, con Personalizza). E " +
                "con \"Continua a contare dopo la scadenza\" allo zero parte il " +
                "cronometro: quanti minuti extra riesci a conquistare? Sistemati " +
                "anche lo stemma tagliato nel profilo e la linea obiettivo, ora " +
                "discreta e tratteggiata.",
            "Freeze got its own page: slide your finger around the circle to " +
                "pick 10 to 120 minutes (beyond that, use Customize). And with " +
                "\"Keep counting after time's up\", a stopwatch starts at zero: " +
                "how many extra minutes can you conquer? Also fixed the clipped " +
                "crest in the profile and the goal line, now subtle and dashed.",
        ),
        NewsItem(
            "1.9.0",
            "L'Ordine dei Guardiani",
            "The Order of the Guardians",
            "Android Auto non conta più come tempo di schermo: vale solo il " +
                "telefono. Congelamento con la sua card in home, Ombra e " +
                "azioni rapide dal menù delle Squadre, spegnere una squadra ora " +
                "chiede se preferisci un'Ombra temporanea. Profilo ridisegnato " +
                "con stemma grande e obiettivo a slider, linea obiettivo nel " +
                "grafico, \"Inizio del giorno\" nelle Impostazioni (protetto " +
                "dal Sigillo) e la nuova Guida per un buon sonno.",
            "Android Auto no longer counts as screen time: only the phone " +
                "does. Freeze got its own home card, Shadow and quick actions " +
                "live in the Teams menu, and turning a team off now asks if " +
                "you'd rather cast a temporary Shadow. Redesigned profile with " +
                "a big crest and slider goal, goal line on the chart, \"Start " +
                "of the day\" in Settings (Seal-protected) and the new Guide " +
                "to good sleep.",
        ),
        NewsItem(
            "1.8.0",
            "La Bussola e la Mente",
            "The Compass and the Mind",
            "Nuovo benvenuto al primo avvio: nome, stemma, obiettivo di tempo e " +
                "orari di sonno — così l'Araldo protegge già dal primo giorno. " +
                "Selettori orari nativi (addio blocco alle 22!), icone vere " +
                "nelle app più usate, mini-grafico in home e la sezione " +
                "\"Il legame Mente-Sonno\" in arrivo con Health Connect.",
            "New welcome flow on first launch: name, crest, time goal and " +
                "sleep schedule — so the Herald protects you from day one. " +
                "Native time pickers (goodbye 10 PM scroll bug!), real app " +
                "icons in the most-used list, a mini chart on the home and the " +
                "upcoming \"Mind-Sleep bond\" section powered by Health Connect.",
        ),
        NewsItem(
            "1.7.0",
            "È arrivato l'Araldo",
            "The Herald has arrived",
            "Un guardiano che non guarda l'orologio ma TE: riconosce il vero " +
                "risveglio (schermo spento 4+ ore) e protegge i primi minuti " +
                "della giornata; la sera impara quando vai a dormire e blocca " +
                "le app in anticipo. In più: Sigillo nelle Impostazioni e " +
                "\"Il tuo perché\" dentro il Profilo.",
            "A guardian that doesn't watch the clock but YOU: it recognises " +
                "your true wake-up (screen off 4+ hours) and guards the first " +
                "minutes of the day; in the evening it learns your bedtime and " +
                "blocks apps ahead of it. Plus: the Seal moved into Settings " +
                "and \"Your why\" now lives inside the Profile.",
        ),
        NewsItem(
            "1.6.0",
            "Risparmio batteria totale e Profilo",
            "Total battery saver and Profile",
            "Sotto la soglia di batteria i motori ora si spengono davvero: " +
                "blocchi e pedaggi rimossi, zero consumi, ripresa automatica in " +
                "carica. La classifica dei guardiani mostra solo la Top 5. " +
                "Nuova card Profilo e questa campanella delle novità!",
            "Below the battery threshold the engines now truly shut down: " +
                "blocks and tolls removed, zero drain, automatic resume while " +
                "charging. The guardian ranking now shows only the Top 5. " +
                "New Profile card and this news bell!",
        ),
        NewsItem(
            "1.5.0",
            "Giorni, cicli e l'Esattore più furbo",
            "Days, cycles and a smarter Tollkeeper",
            "Durate anche in giorni ovunque. Il Guardiano ha le Opzioni " +
                "avanzate: limite giornaliero, settimanale o mensile. " +
                "L'Esattore ha il tempo di riattivazione e non si fa più " +
                "aggirare con \"lascia perdere\". Sigillo con massimo di 5 " +
                "minuti per sicurezza. App escluse nelle Impostazioni.",
            "Durations in days everywhere. The Guardian gained Advanced " +
                "options: daily, weekly or monthly limit. The Tollkeeper got a " +
                "reactivation time and can no longer be dodged with \"never " +
                "mind\". Seal capped at 5 minutes for safety. Excluded apps " +
                "moved into Settings.",
        ),
        NewsItem(
            "1.4.x",
            "L'Esattore, le squadre e la navigazione",
            "The Tollkeeper, teams and navigation",
            "È arrivato l'Esattore (esagono bronzo): un pedaggio di attesa a " +
                "ogni apertura. Squadre con menù rapidi, interruttore generale " +
                "e squadre vuote. Ombra con durata a scelta. Navigazione a " +
                "pila: freccia e tasto indietro fanno sempre la stessa cosa.",
            "The Tollkeeper arrived (bronze hexagon): a waiting toll at every " +
                "open. Teams with quick menus, master switch and empty teams. " +
                "Shadow with custom duration. Stack navigation: arrow and back " +
                "button always do the same thing.",
        ),
    )

    /** Massimo di novità mostrate nella campana (le più recenti). */
    private const val MAX_SHOWN = 5

    /** Le novità visibili: solo le [MAX_SHOWN] più recenti (le vecchie si nascondono). */
    fun recentItems(): List<NewsItem> = items.take(MAX_SHOWN)

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        refreshUnread(context)
    }

    private fun refreshUnread(context: Context) {
        val read = prefs(context).getInt(KEY_READ_COUNT, 0)
        _unread.value = (items.size - read).coerceIn(0, MAX_SHOWN)
    }

    /** Da chiamare quando si apre la schermata delle novità. */
    fun markAllRead(context: Context) {
        prefs(context).edit().putInt(KEY_READ_COUNT, items.size).apply()
        _unread.value = 0
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
