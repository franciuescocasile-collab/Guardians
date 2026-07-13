package com.guardians.app.data

import com.guardians.app.model.MacroCategory

/**
 * ============================================================================
 *  ConductConfig — i coefficienti del Motore della Condotta, TUTTI in un posto.
 * ============================================================================
 *
 * Questa classe isola ogni numero magico del motore (pesi, soglie, penali,
 * ritmi di decadimento) così che una calibrazione futura non richieda di
 * toccare i metodi core del motore: basta cambiare i valori qui.
 *
 * Il motore lavora su un valore interno di "condotta" in punti (0..100), che
 * NON viene mai mostrato come numero all'utente: si traduce solo nella
 * posizione di un cursore su una barra continua Verde→Rosso.
 */
object ConductConfig {

    // ----------------------------------------------------------------- pesi
    /**
     * Pesi dopaminergici di spostamento (App Displacement). Intercettano chi
     * riduce un social ma "scivola" su altre app: il tempo pesa in base a
     * quanto la categoria è potenzialmente compulsiva.
     */
    val displacementWeight: Map<MacroCategory, Double> = mapOf(
        MacroCategory.SOCIAL_GAMING to 1.0,
        MacroCategory.INTRATTENIMENTO to 0.6,
        MacroCategory.PRODUTTIVITA to 0.1,
        MacroCategory.UTILITY to 0.0,
    )

    fun weightOf(macro: MacroCategory): Double = displacementWeight[macro] ?: 0.0

    // --------------------------------------------------- tempo risparmiato
    /**
     * Quota del tempo di Grande Congelamento che confluisce nel tempo
     * risparmiato: un congelamento "vale", ma non uno-a-uno (parte del tempo
     * l'utente l'avrebbe comunque passato lontano dal telefono).
     */
    const val FREEZE_SAVED_FACTOR = 0.3

    // --------------------------------------------------- veti e soglie (ore)
    /**
     * Veto Biologico (Health Ceiling): oltre queste ore TOTALI di schermo al
     * giorno la condotta inizia a flettere, anche se l'obiettivo personale
     * dell'utente è più largo. È salute fisica, non disciplina.
     */
    const val HEALTH_CEILING_MS = 5L * 3_600_000L

    /**
     * Tetto di tossicità isolato: oltre questi minuti su Social/Gaming al
     * giorno la barra inizia SUBITO il drenaggio prioritario verso il rosso,
     * indipendentemente dall'obiettivo globale.
     */
    const val TOXICITY_CEILING_MS = 45L * 60_000L

    // ------------------------------------------- decadimento al minuto (punti)
    /**
     * Coefficienti di decadimento capillare AL MINUTO in zona di sforamento.
     * Sostenibili: puniscono in modo visibile ma senza azzerare lo storico
     * in un colpo solo.
     */
    const val ALPHA_PRODUCTIVE = 0.003   // sforamento su app produttive
    const val ALPHA_TOXIC = 0.08         // sforamento su app tossiche/social

    /**
     * Tetto al calo giornaliero della condotta: nemmeno una giornata pessima può
     * far crollare la barra in un colpo solo. Rende la condotta più indulgente e
     * legata all'andamento, non a un singolo giorno-no.
     */
    const val MAX_DAILY_DRAIN = 12.0

    // ------------------------------------------------------------- malus
    /**
     * Malus secco per l'uso di un Incantesimo (Bypass): infrazione critica
     * immediata, pesa nella finestra mobile e NON compensa i blocchi passati.
     */
    const val SPELL_MALUS = 15.0

    /** Solo i congelamenti oltre questa soglia (Grandi Congelamenti) pesano. */
    const val BIG_FREEZE_MIN_MS = 30L * 60_000L

    /** Malus massimo per l'interruzione anticipata di un Grande Congelamento. */
    const val BIG_FREEZE_MAX_MALUS = 10.0

    /** Finestra mobile (giorni) su cui vivono i malus degli incantesimi. */
    const val MALUS_WINDOW_DAYS = 10

    // --------------------------------------------------- recupero passivo
    /**
     * Recupero giornaliero: se una giornata è "pulita" (nessuno sforamento,
     * nessun malus), la condotta risale piano verso il verde. La virtù non è
     * istantanea, ma nemmeno impossibile.
     */
    const val DAILY_RECOVERY = 7.0

    /** Valore interno di partenza della condotta (0..100). */
    const val START_VALUE = 80.0

    // -------------------------------------------------- anti-farming (log)
    /**
     * Raggruppa i tentativi ripetuti sulla stessa app bloccata entro questa
     * finestra in un solo evento (i tentativi sono eventi meccanici neutri:
     * 0 punti, nessun merito, nessuna sanzione).
     */
    const val BLOCK_LOG_GROUP_MS = 5L * 60_000L
}
