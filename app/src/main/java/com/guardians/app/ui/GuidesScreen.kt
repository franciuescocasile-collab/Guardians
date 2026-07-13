package com.guardians.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guardians.app.data.TimerRepository
import com.guardians.app.data.tr
import com.guardians.app.model.GuardianTimer
import com.guardians.app.model.TimeUnit
import com.guardians.app.model.TimerType
import java.util.UUID

/** Le categorie di guide. */
private enum class GuideCategory { GUARDIANI, SQUADRE, BENESSERE }

/** Un articolo/guida, con un eventuale preset di squadra da aggiungere con un tocco. */
private class Guide(
    val category: GuideCategory,
    /** Chiave stabile per i suggerimenti mirati (es. "doom", "sleep", "goal"). */
    val key: String = "",
    val title: String,
    val summary: String,
    val body: String,
    /** Forma del guardiano da mostrare in cima all'articolo (opzionale). */
    val shape: TimerType? = null,
    /** Se non nullo, in fondo all'articolo c'è il pulsante "crea questo guardiano". */
    val createType: TimerType? = null,
    /** Se non nullo, aggiunge questa squadra (nome + guardiani) con un pulsante. */
    val presetTeam: String? = null,
    val presetTimers: () -> List<GuardianTimer> = { emptyList() },
)

/** App social comuni: usate dal preset anti-doomscrolling (quelle non installate restano innocue). */
private val SOCIAL_PACKAGES = listOf(
    "com.instagram.android",
    "com.zhiliaoapp.musically",   // TikTok
    "com.facebook.katana",
    "com.twitter.android",
    "com.snapchat.android",
    "com.reddit.frontpage",
    "com.google.android.youtube",
)

@Composable
private fun guides(): List<Guide> = listOf(
    Guide(
        category = GuideCategory.GUARDIANI,
        title = tr("Come funziona la Sentinella", "How the Sentinel works"),
        summary = tr("Il guardiano dell'uso continuo", "The continuous-use guardian"),
        shape = TimerType.SENTINELLA,
        createType = TimerType.SENTINELLA,
        body = tr(
            "La Sentinella (triangolo giallo) sorveglia l'uso CONTINUO di un'app.\n\n" +
                "Quando la usi senza sosta per il tempo che hai impostato, ti " +
                "\"butta fuori\": torni alla schermata home e compare un popup.\n\n" +
                "Puoi impostare una PAUSA: se la metti, dopo l'espulsione devi " +
                "aspettare quel tempo prima di poter rientrare (ogni tentativo " +
                "prima della fine viene respinto). Se la lasci a 0, invece, vieni " +
                "buttato fuori ma puoi rientrare subito — utile come semplice " +
                "\"scossa\" per interrompere lo scrolling automatico.\n\n" +
                "Il conteggio si azzera quando stai lontano dall'app abbastanza a " +
                "lungo. È il guardiano giusto per non perderti in sessioni infinite.",
            "The Sentinel (yellow triangle) watches the CONTINUOUS use of an app.\n\n" +
                "When you use it non-stop for the time you set, it \"kicks you " +
                "out\": you return to the home screen and a popup appears.\n\n" +
                "You can set a BREAK: if you do, after being kicked out you must " +
                "wait that time before coming back (any earlier attempt is " +
                "rejected). If you leave it at 0, you get kicked out but can come " +
                "right back — handy as a simple \"nudge\" to break autopilot " +
                "scrolling.\n\n" +
                "The counter resets when you stay away from the app long enough. " +
                "It's the right guardian to avoid endless sessions.",
        ),
    ),
    Guide(
        category = GuideCategory.GUARDIANI,
        title = tr("Come funziona il Guardiano", "How the Guardian works"),
        summary = tr("Il tetto di tempo giornaliero", "The daily time cap"),
        shape = TimerType.GUARDIANO,
        createType = TimerType.GUARDIANO,
        body = tr(
            "Il Guardiano (quadrato rosso) sorveglia il tempo TOTALE di utilizzo " +
                "nella giornata.\n\n" +
                "Puoi usare l'app quando vuoi, anche a piccole dosi: il Guardiano " +
                "somma tutto. Quando il totale raggiunge il tetto che hai " +
                "impostato, l'app viene bloccata fino a mezzanotte: ogni tentativo " +
                "di riaprirla viene respinto con un popup.\n\n" +
                "Se vuoi, attiva il PREAVVISO: una notifica ti avverte poco prima " +
                "che il tempo finisca, così puoi chiudere con calma.\n\n" +
                "Con le OPZIONI AVANZATE puoi cambiare il ciclo del limite: oltre " +
                "al tetto giornaliero (predefinito) puoi sceglierne uno " +
                "SETTIMANALE, che si azzera ogni lunedì, o MENSILE, che si azzera " +
                "il primo del mese. Es. \"7 ore di social a settimana\": le usi " +
                "come vuoi, ma finite quelle, stop fino a lunedì.\n\n" +
                "È il guardiano giusto per dare un tetto al tempo totale, ad " +
                "esempio \"massimo 1 ora di social al giorno\".",
            "The Guardian (red square) watches the TOTAL usage time in the day.\n\n" +
                "You can use the app whenever you want, even in small doses: the " +
                "Guardian adds it all up. When the total reaches the cap you set, " +
                "the app is blocked until midnight: every attempt to reopen it is " +
                "rejected with a popup.\n\n" +
                "If you want, enable the EARLY WARNING: a notification alerts you " +
                "shortly before time runs out, so you can wrap up calmly.\n\n" +
                "With the ADVANCED OPTIONS you can change the limit cycle: besides " +
                "the daily cap (default) you can pick a WEEKLY one, resetting " +
                "every Monday, or a MONTHLY one, resetting on the first of the " +
                "month. E.g. \"7 hours of social apps per week\": use them as you " +
                "like, but once they're gone, that's it until Monday.\n\n" +
                "It's the right guardian to cap your total time, e.g. \"at most 1 " +
                "hour of social apps per day\".",
        ),
    ),
    Guide(
        category = GuideCategory.GUARDIANI,
        title = tr("Come funziona il Custode", "How the Keeper works"),
        summary = tr("Il guardiano della fascia oraria", "The time-window guardian"),
        shape = TimerType.CUSTODE,
        createType = TimerType.CUSTODE,
        body = tr(
            "Il Custode (cerchio blu) protegge una FASCIA ORARIA.\n\n" +
                "Scegli un orario di inizio e uno di fine: durante il turno le app " +
                "sorvegliate non si possono usare e ogni apertura viene respinta " +
                "con un popup. Fuori dal turno il Custode riposa e non interviene " +
                "mai.\n\n" +
                "La fascia può anche scavalcare la mezzanotte (ad esempio dalle " +
                "23:00 alle 7:00): perfetta per proteggere il sonno, lo studio o " +
                "le ore di lavoro.\n\n" +
                "Con il PREAVVISO attivo, una notifica ti avvisa poco prima " +
                "dell'inizio del turno, così non vieni colto di sorpresa.",
            "The Keeper (blue circle) protects a TIME WINDOW.\n\n" +
                "Pick a start and an end time: during the shift the watched apps " +
                "cannot be used and every open is rejected with a popup. Outside " +
                "the shift the Keeper rests and never steps in.\n\n" +
                "The window can also cross midnight (for example from 11 PM to " +
                "7 AM): perfect to protect sleep, study or working hours.\n\n" +
                "With the EARLY WARNING on, a notification alerts you shortly " +
                "before the shift starts, so you're never caught by surprise.",
        ),
    ),
    Guide(
        category = GuideCategory.GUARDIANI,
        title = tr("Come funziona il Gendarme", "How the Gendarme works"),
        summary = tr("Il guardiano delle aperture", "The app-opens guardian"),
        shape = TimerType.GENDARME,
        createType = TimerType.GENDARME,
        body = tr(
            "Il Gendarme (rombo viola) conta le VOLTE che apri un'app, non il " +
                "tempo che ci passi.\n\n" +
                "Puoi combinare tre regole (basta impostarne una, 0 = spenta):\n\n" +
                "• APERTURE MASSIME al giorno: superata la soglia, l'app è " +
                "bloccata fino a domani;\n" +
                "• PAUSA DI RIAPERTURA: dopo aver lasciato l'app devi aspettare " +
                "X minuti prima di poterla riaprire;\n" +
                "• NOTIFICA dopo N aperture: un semplice avviso, senza blocchi.\n\n" +
                "Un'apertura conta solo se l'app era stata lasciata per almeno 20 " +
                "secondi: i cambi rapidi di schermata non contano.\n\n" +
                "È il guardiano giusto contro il controllo compulsivo: aprire " +
                "un'app \"solo un attimo\" quaranta volte al giorno.",
            "The Gendarme (purple diamond) counts the TIMES you open an app, not " +
                "the time you spend in it.\n\n" +
                "You can combine three rules (setting just one is enough, 0 = " +
                "off):\n\n" +
                "• MAX OPENS per day: past the threshold, the app is blocked " +
                "until tomorrow;\n" +
                "• REOPEN BREAK: after leaving the app you must wait X minutes " +
                "before opening it again;\n" +
                "• NOTIFICATION after N opens: a simple heads-up, no blocking.\n\n" +
                "An open only counts if the app had been left for at least 20 " +
                "seconds: quick screen switches don't count.\n\n" +
                "It's the right guardian against compulsive checking: opening an " +
                "app \"just for a second\" forty times a day.",
        ),
    ),
    Guide(
        category = GuideCategory.GUARDIANI,
        title = tr("Come funziona il Messaggero", "How the Messenger works"),
        summary = tr("Avvisi sempre più insistenti, mai blocchi", "Growing nudges, never blocks"),
        shape = TimerType.MESSAGGERO,
        createType = TimerType.MESSAGGERO,
        body = tr(
            "Il Messaggero (pentagono arancione) non blocca MAI: ti manda " +
                "notifiche.\n\n" +
                "Imposti dopo quanto uso continuo arriva il primo avviso; da lì " +
                "in poi gli avvisi continuano, sempre più ravvicinati, finché non " +
                "lasci l'app. Con l'INSISTENZA scegli il ritmo: Gentile, Media, " +
                "Incalzante, oppure Programmabile con un intervallo fisso deciso " +
                "da te.\n\n" +
                "Puoi dare un tetto al numero di avvisi e persino scrivere tu i " +
                "messaggi, dal più dolce al più severo. Ogni tanto il Messaggero " +
                "ti ricorda anche uno dei tuoi obiettivi del profilo \"Il tuo " +
                "perché\".\n\n" +
                "È il guardiano giusto se vuoi una coscienza al tuo fianco, non " +
                "un buttafuori.",
            "The Messenger (orange pentagon) NEVER blocks: it sends you " +
                "notifications.\n\n" +
                "You set how much continuous use triggers the first notice; from " +
                "then on the notices keep coming, closer and closer together, " +
                "until you leave the app. With INSISTENCE you pick the rhythm: " +
                "Gentle, Medium, Pressing, or Programmable with a fixed interval " +
                "of your choice.\n\n" +
                "You can cap the number of notices and even write the messages " +
                "yourself, from the sweetest to the sternest. Every now and then " +
                "the Messenger also reminds you of one of the goals from your " +
                "\"Your why\" profile.\n\n" +
                "It's the right guardian if you want a conscience by your side, " +
                "not a bouncer.",
        ),
    ),
    Guide(
        category = GuideCategory.GUARDIANI,
        title = tr("Come funziona l'Araldo", "How the Herald works"),
        summary = tr("Protegge risveglio e sera, senza orologio", "Guards morning and night, no clock"),
        shape = TimerType.ARALDO,
        createType = TimerType.ARALDO,
        body = tr(
            "L'Araldo (alba d'argento) protegge i due momenti più fragili della " +
                "giornata: il risveglio e la sera. Non usa orari fissi — osserva " +
                "il tuo comportamento reale e si adatta.\n\n" +
                "FASE MATTUTINA. Riconosce il tuo VERO risveglio: uno schermo " +
                "rimasto spento almeno 4 ore di fila e riacceso dentro la " +
                "finestra del mattino (regolabile, di base 05:00-12:00). Da " +
                "quel momento blocca le app scelte per la durata impostata: i " +
                "primi minuti della giornata restano tuoi, niente social prima " +
                "ancora di alzarti. Lo sblocco delle 3 di notte per vedere " +
                "l'ora e il pisolino del pomeriggio NON lo attivano.\n\n" +
                "FASE SERALE. L'Araldo impara quando vai a dormire: registra " +
                "l'ora in cui lo schermo inizia il suo lungo spegnimento " +
                "notturno e ne calcola l'orario tipico (la mediana delle " +
                "ultime due settimane, servono almeno 3 notti). Poi ti " +
                "anticipa: blocca le app dall'anticipo che scegli (es. 45 " +
                "minuti prima) fino alle 04:00, per accompagnarti verso il " +
                "sonno invece di rubartelo.\n\n" +
                "Tutto avviene osservando accensioni e spegnimenti dello " +
                "schermo: zero GPS, zero sensori, zero consumo extra.\n\n" +
                "È il guardiano giusto se le tue ore peggiori sono la prima e " +
                "l'ultima della giornata.",
            "The Herald (silver dawn) guards the two most fragile moments of " +
                "the day: waking up and the evening. It uses no fixed schedule " +
                "— it watches your real behaviour and adapts.\n\n" +
                "MORNING PHASE. It recognises your TRUE wake-up: a screen that " +
                "stayed off for at least 4 hours straight, turned back on " +
                "inside the morning window (adjustable, 05:00-12:00 by " +
                "default). From that moment it blocks the chosen apps for the " +
                "set duration: the first minutes of the day stay yours, no " +
                "social feeds before you even get up. The 3 AM check of the " +
                "clock and the afternoon nap do NOT trigger it.\n\n" +
                "EVENING PHASE. The Herald learns when you go to sleep: it " +
                "records when the screen starts its long night shutdown and " +
                "computes your typical bedtime (the median of the last two " +
                "weeks, at least 3 nights needed). Then it gets there first: " +
                "it blocks the apps from your chosen head start (e.g. 45 " +
                "minutes before) until 04:00, walking you toward sleep " +
                "instead of stealing it.\n\n" +
                "Everything works by watching the screen turning on and off: " +
                "no GPS, no sensors, no extra battery drain.\n\n" +
                "It's the right guardian if your worst hours are the first " +
                "and the last of the day.",
        ),
    ),
    Guide(
        category = GuideCategory.GUARDIANI,
        title = tr("Come funziona l'Esattore", "How the Tollkeeper works"),
        summary = tr("Un pedaggio di attesa a ogni apertura", "A waiting toll at every open"),
        shape = TimerType.ESATTORE,
        createType = TimerType.ESATTORE,
        body = tr(
            "L'Esattore (esagono bronzo) non vieta l'app: la fa \"costare\".\n\n" +
                "Ogni volta che la apri, ti presenta il conto: una schermata di " +
                "respiro con un conto alla rovescia (consigliati 30-60 secondi). " +
                "Solo alla fine si sblocca il pulsante ENTRA; in qualsiasi momento " +
                "puoi invece toccare LASCIA PERDERE e tornare alla home.\n\n" +
                "Quel piccolo attrito basta a spezzare le aperture automatiche: " +
                "se l'app la volevi davvero, aspetti; se l'avevi aperta senza " +
                "pensarci, lasci perdere.\n\n" +
                "Con il TEMPO DI RIATTIVAZIONE decidi la tolleranza: a 0 il " +
                "pedaggio si ripresenta a OGNI rientro, anche dopo un secondo; " +
                "con un valore (es. 10 secondi) puoi uscire e rientrare entro " +
                "quella finestra senza ripagare. E se tocchi LASCIA PERDERE, al " +
                "prossimo tentativo il pedaggio c'è comunque: niente scorciatoie.\n\n" +
                "È la via di mezzo che mancava tra il Messaggero (solo parole) e " +
                "i guardiani che bloccano davvero.",
            "The Tollkeeper (bronze hexagon) doesn't forbid the app: it makes it " +
                "\"cost\" something.\n\n" +
                "Every time you open it, the bill arrives: a breathing screen " +
                "with a countdown (30-60 seconds recommended). Only when it ends " +
                "does the ENTER button unlock; at any moment you can tap NEVER " +
                "MIND instead and go back home.\n\n" +
                "That little friction is enough to break automatic opens: if you " +
                "really wanted the app, you wait; if you opened it without " +
                "thinking, you let it go.\n\n" +
                "With the REACTIVATION TIME you set the tolerance: at 0 the toll " +
                "comes back at EVERY re-entry, even after one second; with a " +
                "value (e.g. 10 seconds) you can leave and come back within that " +
                "window without paying again. And if you tap NEVER MIND, the " +
                "toll shows up anyway on your next attempt: no shortcuts.\n\n" +
                "It's the missing middle ground between the Messenger (words " +
                "only) and the guardians that truly block.",
        ),
    ),
    Guide(
        category = GuideCategory.GUARDIANI,
        title = tr("Come funziona la Vedetta", "How the Lookout works"),
        summary = tr("Un potere prestato, solo in un luogo", "A borrowed power, only in one place"),
        shape = TimerType.VEDETTA,
        createType = TimerType.VEDETTA,
        body = tr(
            "La Vedetta (stella verde acqua) è un guardiano speciale: non ha un " +
                "potere suo, PRESTA quello di un altro.\n\n" +
                "Scegli quale guardiano deve interpretare (Sentinella, Guardiano, " +
                "Custode…), un LUOGO (la tua posizione attuale o un indirizzo " +
                "cercato) e un raggio in chilometri. La Vedetta agisce solo " +
                "quando sei in quel luogo: fuori dal raggio dorme.\n\n" +
                "Qualche esempio: una Sentinella severa solo a casa, un Custode " +
                "che blocca i giochi solo a scuola o in ufficio.\n\n" +
                "Per la tua sicurezza, se la posizione non è disponibile (GPS " +
                "spento o nessun dato) la Vedetta resta in silenzio e non blocca " +
                "nulla.",
            "The Lookout (teal star) is a special guardian: it has no power of " +
                "its own, it BORROWS another one's.\n\n" +
                "Choose which guardian it should play (Sentinel, Guardian, " +
                "Keeper…), a PLACE (your current position or a searched address) " +
                "and a radius in kilometres. The Lookout acts only when you are " +
                "in that place: outside the radius it sleeps.\n\n" +
                "Some examples: a strict Sentinel only at home, a Keeper that " +
                "blocks games only at school or at the office.\n\n" +
                "For your safety, if no position is available (GPS off or no " +
                "data) the Lookout stays silent and blocks nothing.",
        ),
    ),
    Guide(
        category = GuideCategory.BENESSERE,
        key = "goal",
        title = tr("Scegliere il miglior obiettivo giornaliero", "Choosing your best daily goal"),
        summary = tr("Un limite sostenibile, non un muro", "A sustainable limit, not a wall"),
        body = tr(
            "L'obiettivo giornaliero non è una punizione: è una promessa che " +
                "riesci a mantenere. Se lo metti troppo basso lo sfori subito e " +
                "ti scoraggi; troppo alto e non serve a niente.\n\n" +
                "Il metodo in tre passi:\n\n" +
                "1) PARTI DAL VERO. Guarda la tua media degli ultimi giorni nelle " +
                "Statistiche. Quello è il tuo punto di partenza onesto, non quello " +
                "che vorresti.\n\n" +
                "2) TOGLI IL 15-20%. Il primo obiettivo dovrebbe essere poco sotto " +
                "la media: abbastanza da sentirlo, non tanto da renderlo " +
                "impossibile. Se fai 4 ore, punta a 3h20.\n\n" +
                "3) SCENDI A GRADINI. Ogni una-due settimane, se reggi, togli " +
                "altri 15-20 minuti. Le abitudini si spostano per piccoli passi, " +
                "non per salti eroici.\n\n" +
                "Ricorda: un obiettivo mancato non è un fallimento, è un dato. " +
                "Il calendario dell'obiettivo (nel Dettaglio del tempo) ti mostra " +
                "i giorni verdi e rossi: guarda la tendenza, non il singolo giorno.",
            "The daily goal isn't a punishment: it's a promise you can keep. Set " +
                "it too low and you'll blow past it and give up; too high and it " +
                "does nothing.\n\n" +
                "The three-step method:\n\n" +
                "1) START FROM REALITY. Check your average of the last days in " +
                "Statistics. That's your honest starting point, not your wish.\n\n" +
                "2) CUT 15-20%. Your first goal should sit just below your " +
                "average: enough to feel it, not enough to make it impossible. " +
                "If you do 4 hours, aim for 3h20.\n\n" +
                "3) STEP DOWN GRADUALLY. Every week or two, if you hold, shave " +
                "another 15-20 minutes. Habits move in small steps, not heroic " +
                "leaps.\n\n" +
                "Remember: a missed goal isn't a failure, it's data. The goal " +
                "calendar (in Screen-time Details) shows your green and red days: " +
                "watch the trend, not the single day.",
        ),
    ),
    Guide(
        category = GuideCategory.BENESSERE,
        key = "sleep",
        title = tr("Guida per un buon sonno", "Guide to good sleep"),
        summary = tr("Perché gli schermi rubano il riposo", "Why screens steal your rest"),
        createType = TimerType.ARALDO,
        body = tr(
            "Il sonno non inizia quando chiudi gli occhi: inizia quando il tuo " +
                "cervello capisce che è sera. E per capirlo usa la MELATONINA, " +
                "l'ormone che regola il ritmo sonno-veglia.\n\n" +
                "Il problema: la luce degli schermi — specie quella blu — dice al " +
                "cervello che è ancora giorno, e la produzione di melatonina si " +
                "ferma. Un'ora di scrolling a letto non ti costa solo quell'ora: " +
                "ritarda l'addormentamento, rende il sonno più leggero e la " +
                "sveglia più amara.\n\n" +
                "Cosa funziona davvero:\n\n" +
                "• allontana gli schermi 30-60 minuti prima di dormire;\n" +
                "• niente telefono SUL comodino: la sola portata di mano è " +
                "una tentazione continua;\n" +
                "• al posto dello schermo: carta, musica, o semplicemente il buio.\n\n" +
                "È esattamente la filosofia dell'ARALDO: la sua fase serale " +
                "impara quando vai a dormire e ti scorta verso il sonno " +
                "bloccando le app prima, mentre quella mattutina ti regala i " +
                "primi minuti del giorno senza feed. Crealo qui sotto e lascia " +
                "che faccia la guardia alla tua melatonina.",
            "Sleep doesn't start when you close your eyes: it starts when your " +
                "brain understands it's evening. And to understand it, it uses " +
                "MELATONIN, the hormone that drives the sleep-wake rhythm.\n\n" +
                "The problem: screen light — especially blue light — tells the " +
                "brain it's still daytime, and melatonin production stops. An " +
                "hour of scrolling in bed doesn't just cost you that hour: it " +
                "delays falling asleep, makes sleep lighter and the alarm " +
                "clock crueler.\n\n" +
                "What actually works:\n\n" +
                "• put screens away 30-60 minutes before sleep;\n" +
                "• no phone ON the nightstand: within-reach is a standing " +
                "temptation;\n" +
                "• instead of a screen: paper, music, or simply darkness.\n\n" +
                "This is exactly the HERALD's philosophy: its evening phase " +
                "learns when you go to sleep and escorts you toward it by " +
                "blocking apps beforehand, while the morning phase gives you " +
                "the first minutes of the day feed-free. Create it below and " +
                "let it stand guard over your melatonin.",
        ),
    ),
    Guide(
        category = GuideCategory.SQUADRE,
        key = "doom",
        title = tr("Guida all'anti-doomscrolling", "Anti-doomscrolling guide"),
        summary = tr("Una squadra pronta per i social", "A ready-made team for social apps"),
        body = tr(
            "Il doomscrolling è lo scorrere all'infinito i social senza accorgersene. " +
                "Una buona difesa combina due guardiani sulle stesse app:\n\n" +
                "• una SENTINELLA che ti butta fuori dopo 10 minuti di fila, per " +
                "spezzare le sessioni continue;\n" +
                "• un GUARDIANO che dopo 1 ora TOTALE nella giornata blocca le app " +
                "fino a domani.\n\n" +
                "Con il pulsante qui sotto aggiungi la squadra \"Anti doom scrolling\" " +
                "già pronta con questi due guardiani sulle app social più comuni. " +
                "Dopo averla aggiunta, aprila da \"Squadre\" e regola le app o i " +
                "tempi come preferisci.",
            "Doomscrolling is scrolling social apps endlessly without noticing. " +
                "A good defense combines two guardians on the same apps:\n\n" +
                "• a SENTINEL that kicks you out after 10 minutes in a row, to " +
                "break continuous sessions;\n" +
                "• a GUARDIAN that after 1 hour TOTAL in the day blocks the apps " +
                "until tomorrow.\n\n" +
                "With the button below you add the ready-made \"Anti doom scrolling\" " +
                "team with these two guardians on the most common social apps. " +
                "After adding it, open it from \"Teams\" and adjust apps or times " +
                "as you like.",
        ),
        presetTeam = "Anti doom scrolling",
        presetTimers = {
            listOf(
                GuardianTimer(
                    id = UUID.randomUUID().toString(),
                    name = tr("Sentinella social", "Social Sentinel"),
                    type = TimerType.SENTINELLA,
                    limitAmount = 10, limitUnit = TimeUnit.MINUTES,
                    packages = SOCIAL_PACKAGES,
                    team = "Anti doom scrolling",
                ),
                GuardianTimer(
                    id = UUID.randomUUID().toString(),
                    name = tr("Guardiano social", "Social Guardian"),
                    type = TimerType.GUARDIANO,
                    limitAmount = 1, limitUnit = TimeUnit.HOURS,
                    packages = SOCIAL_PACKAGES,
                    team = "Anti doom scrolling",
                ),
            )
        },
    ),
)

@Composable
fun GuidesScreen(
    onBack: () -> Unit,
    onOpenTeams: () -> Unit,
    onCreateGuardian: (TimerType) -> Unit,
) {
    val context = LocalContext.current
    val allGuides = guides()
    var open by remember { mutableStateOf<Guide?>(null) }
    var query by remember { mutableStateOf("") }
    // Le guide dei guardiani vivono dentro l'hub "I Guardiani".
    var showGuardians by remember { mutableStateOf(false) }

    val current = open
    if (current != null) {
        // Anche il tasto indietro di sistema chiude l'articolo (torna all'elenco),
        // esattamente come la freccia: mai saltare un livello.
        androidx.activity.compose.BackHandler { open = null }
        GuideArticle(
            guide = current,
            onBack = { open = null },
            onAddPreset = { guide ->
                guide.presetTimers().forEach { TimerRepository.upsert(context, it) }
                onOpenTeams()
            },
            onCreateGuardian = onCreateGuardian,
        )
        return
    }

    // Sotto-pagina: tutte le guide dei guardiani, una per ciascuno.
    if (showGuardians) {
        androidx.activity.compose.BackHandler { showGuardians = false }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showGuardians = false }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                }
                Text(
                    tr("I Guardiani", "The Guardians"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            allGuides.filter { it.category == GuideCategory.GUARDIANI }.forEach { guide ->
                GuideCard(guide) { open = it }
            }
        }
        return
    }

    // Filtro per testo (titolo, sommario, corpo).
    val filtered = allGuides.filter {
        query.isBlank() ||
            it.title.contains(query, true) ||
            it.summary.contains(query, true) ||
            it.body.contains(query, true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
            Text(
                tr("Guide", "Guides"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(tr("Cerca nelle guide", "Search guides")) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (query.isBlank()) {
            // Suggerimenti INTELLIGENTI in cima, calcolati dai dati d'uso.
            val recommendedKeys by androidx.compose.runtime.produceState(
                initialValue = emptyList<String>(),
            ) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    recommendedGuideKeys(context)
                }
            }
            val recommended = recommendedKeys.mapNotNull { k ->
                allGuides.firstOrNull { it.key == k }
            }
            if (recommended.isNotEmpty()) {
                Text(
                    tr("Consigliati per te", "Recommended for you"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                recommended.forEach { guide -> GuideCard(guide) { open = it } }
            }
        }

        if (query.isNotBlank()) {
            // Ricerca attiva: risultati piatti da tutte le categorie.
            filtered.forEach { guide -> GuideCard(guide) { open = it } }
            if (filtered.isEmpty()) {
                Text(
                    tr("Nessuna guida trovata.", "No guides found."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Hub: le guide dei guardiani stanno tutte dentro un'unica voce madre.
            val guardianCount = allGuides.count { it.category == GuideCategory.GUARDIANI }
            Card(
                onClick = { showGuardians = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    TimerShapeIcon(TimerType.SENTINELLA, Modifier.size(26.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("I Guardiani", "The Guardians"), fontWeight = FontWeight.Bold)
                        Text(
                            tr(
                                "$guardianCount guide, una per ogni guardiano",
                                "$guardianCount guides, one for each guardian",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            GuideCategory.entries.filter { it != GuideCategory.GUARDIANI }.forEach { cat ->
                val items = allGuides.filter { it.category == cat }
                if (items.isEmpty()) return@forEach
                Text(
                    when (cat) {
                        GuideCategory.GUARDIANI -> tr("Guardiani", "Guardians")
                        GuideCategory.SQUADRE -> tr("Squadre", "Teams")
                        GuideCategory.BENESSERE -> tr("Benessere", "Wellbeing")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                items.forEach { guide -> GuideCard(guide) { open = it } }
            }
        }
    }
}

/** La card di una guida nell'elenco. */
@Composable
private fun GuideCard(guide: Guide, onOpen: (Guide) -> Unit) {
    Card(
        onClick = { onOpen(guide) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            if (guide.shape != null) {
                TimerShapeIcon(guide.shape, Modifier.size(26.dp))
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(guide.title, fontWeight = FontWeight.Bold)
                Text(
                    guide.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Suggerimenti mirati dai dati d'uso, in ordine di priorità:
 * - "doom" se oggi i social pesano molto sul tempo di schermo;
 * - "sleep" se ci sono sblocchi in piena notte (23-05);
 * - "goal" se non c'è ancora un obiettivo impostato.
 */
private fun recommendedGuideKeys(context: android.content.Context): List<String> {
    if (!hasUsageAccess(context)) return emptyList()
    val keys = mutableListOf<String>()
    val usm = context.getSystemService(android.content.Context.USAGE_STATS_SERVICE)
        as android.app.usage.UsageStatsManager
    val zone = java.time.ZoneId.systemDefault()
    val start = java.time.LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
    val now = System.currentTimeMillis()
    val social = setOf(
        "com.instagram.android", "com.zhiliaoapp.musically",
        "com.facebook.katana", "com.twitter.android",
        "com.snapchat.android", "com.reddit.frontpage",
    )
    var socialMs = 0L
    var totalMs = 0L
    var nightUnlocks = 0
    try {
        val perApp = usm.queryAndAggregateUsageStats(start, now)
        perApp.forEach { (pkg, s) ->
            totalMs += s.totalTimeInForeground
            if (pkg in social) socialMs += s.totalTimeInForeground
        }
        // Sblocchi notturni: eventi di foreground tra le 23 e le 05.
        val events = usm.queryEvents(now - 3L * 86400_000L, now)
        val e = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                val h = java.time.Instant.ofEpochMilli(e.timeStamp)
                    .atZone(zone).toLocalTime().hour
                if (h >= 23 || h < 5) nightUnlocks++
            }
        }
    } catch (_: Exception) {
    }
    // Social oltre il 35% del tempo di oggi (e almeno mezz'ora) → anti-doom.
    if (socialMs > 30 * 60_000L && totalMs > 0 && socialMs * 100 / totalMs >= 35) {
        keys.add("doom")
    }
    // Parecchi sblocchi notturni negli ultimi 3 giorni → guida del sonno.
    if (nightUnlocks >= 10) keys.add("sleep")
    // Nessun obiettivo impostato → guida su come sceglierlo.
    if (com.guardians.app.data.ProfileRepository.dailyGoalMinutes.value <= 0) {
        keys.add("goal")
    }
    return keys
}

@Composable
private fun GuideArticle(
    guide: Guide,
    onBack: () -> Unit,
    onAddPreset: (Guide) -> Unit,
    onCreateGuardian: (TimerType) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
            Text(
                guide.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        // Immagine del guardiano: la forma grande, centrata.
        guide.shape?.let { shape ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimerShapeIcon(shape, Modifier.size(96.dp))
            }
        }
        Text(guide.body, style = MaterialTheme.typography.bodyMedium)

        // Dall'articolo si passa subito alla creazione di quel guardiano.
        if (guide.createType != null) {
            Button(
                onClick = { onCreateGuardian(guide.createType) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    tr("Crea questo guardiano", "Create this guardian"),
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (guide.presetTeam != null) {
            Button(
                onClick = { onAddPreset(guide) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    tr(
                        "Aggiungi la squadra \"${guide.presetTeam}\"",
                        "Add the \"${guide.presetTeam}\" team",
                    ),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
