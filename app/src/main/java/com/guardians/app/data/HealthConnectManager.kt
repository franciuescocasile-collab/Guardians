package com.guardians.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

/**
 * Ponte verso Health Connect: la piattaforma di sistema dove Samsung Health (e le
 * altre app di salute) depositano i propri dati. Noi non parliamo con Samsung
 * Health direttamente: leggiamo il SONNO da questo "magazzino comune".
 *
 * Tutto è difensivo: se Health Connect non c'è, se il permesso manca o se la
 * lettura fallisce, restituiamo semplicemente "niente dati" senza mai crashare.
 * Il collegamento vero avviene una-tantum quando l'utente concede il permesso
 * dalla schermata di Health Connect (vedi la card nelle Statistiche).
 */
object HealthConnectManager {

    /** Permessi richiesti: per ora solo la lettura delle sessioni di sonno. */
    val PERMISSIONS: Set<String> =
        setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))

    /** Stato dell'SDK: disponibile, da aggiornare o assente. */
    fun status(context: Context): Int = try {
        HealthConnectClient.getSdkStatus(context)
    } catch (_: Throwable) {
        HealthConnectClient.SDK_UNAVAILABLE
    }

    fun isAvailable(context: Context): Boolean =
        status(context) == HealthConnectClient.SDK_AVAILABLE

    /** Serve aggiornare/installare l'app di Health Connect? */
    fun needsProviderUpdate(context: Context): Boolean =
        status(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    fun client(context: Context): HealthConnectClient? = try {
        if (isAvailable(context)) HealthConnectClient.getOrCreate(context) else null
    } catch (_: Throwable) {
        null
    }

    /** True se l'utente ci ha già concesso i permessi di lettura del sonno. */
    suspend fun hasPermission(context: Context): Boolean = try {
        val c = client(context) ?: return false
        c.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    } catch (_: Throwable) {
        false
    }

    /** Le sessioni di sonno registrate tra [start] e [end]. */
    suspend fun readSleep(
        context: Context,
        start: Instant,
        end: Instant,
    ): List<SleepSessionRecord> = try {
        val c = client(context) ?: return emptyList()
        c.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
        ).records
    } catch (_: Throwable) {
        emptyList()
    }

    /**
     * L'ultima dormita nelle ultime ~36 ore (la sessione di sonno più recente),
     * o null se non c'è nulla / mancano permessi. Comodo per la card "Sonno".
     */
    suspend fun lastSleep(context: Context): SleepSessionRecord? {
        val end = Instant.now()
        val start = end.minusSeconds(36L * 3600L)
        return readSleep(context, start, end).maxByOrNull { it.endTime }
    }

    /**
     * Una notte "distacco → sonno": il momento in cui il telefono è stato posato
     * (Punto A, dai dati dell'Araldo) e l'orario in cui ci si è addormentati per
     * davvero (Punto B, da Health Connect), più il divario tra i due e la durata
     * del sonno. Serve a dimostrare che staccare prima di dormire fa bene.
     */
    data class WindDownNight(
        val sleepOnset: Instant,   // Punto B: addormentamento vero
        val phoneOff: Instant,     // Punto A: distacco dal telefono
        val gapMinutes: Long,      // minuti "senza telefono" prima di dormire
        val sleepMinutes: Long,    // durata della dormita
    )

    /**
     * Una notte con VOTO: punteggio 0..100 del sonno (durata + fasi, se lo
     * smartwatch le fornisce) e minuti di "distacco" dal telefono prima di
     * addormentarsi (null se l'Araldo non ha il dato di quella sera).
     */
    data class NightScore(
        val date: java.time.LocalDate,   // il giorno del RISVEGLIO
        val score: Int,                  // 0..100
        val durationMin: Long,
        val gapMin: Long?,               // distacco telefono→sonno (null = ignoto)
    )

    /**
     * Voto del sonno 0..100 che premia soprattutto la QUALITÀ (9.3): quando lo
     * smartwatch fornisce le fasi, 55 punti vengono da profondo (~20%) e REM
     * (~22%) e solo 45 dalla durata (pieni tra 7 e 9 ore). Senza fasi (niente
     * smartwatch) il voto si basa per forza sulla sola durata.
     */
    private fun sleepScore(record: SleepSessionRecord): Int {
        val durMs = java.time.Duration.between(record.startTime, record.endTime)
            .toMillis().coerceAtLeast(0L)
        val durH = durMs / 3_600_000.0
        // Punteggio-durata normalizzato 0..1 (poi pesato diversamente).
        val durationUnit = when {
            durH in 7.0..9.0 -> 1.0
            durH < 7.0 -> (durH / 7.0)
            else -> (1.0 - (durH - 9.0) * 0.07).coerceAtLeast(0.7)
        }.coerceIn(0.0, 1.0)
        var deepMs = 0L
        var remMs = 0L
        var stagedMs = 0L
        try {
            record.stages.forEach { s ->
                val ms = java.time.Duration.between(s.startTime, s.endTime)
                    .toMillis().coerceAtLeast(0L)
                stagedMs += ms
                when (s.stage) {
                    SleepSessionRecord.STAGE_TYPE_DEEP -> deepMs += ms
                    SleepSessionRecord.STAGE_TYPE_REM -> remMs += ms
                }
            }
        } catch (_: Throwable) {
        }
        return if (stagedMs > 0L && durMs > 0L) {
            val deepRatio = deepMs.toDouble() / durMs
            val remRatio = remMs.toDouble() / durMs
            // La QUALITÀ pesa più della durata: 30 profondo + 25 REM = 55.
            val phaseScore = 30.0 * (deepRatio / 0.20).coerceAtMost(1.0) +
                25.0 * (remRatio / 0.22).coerceAtMost(1.0)
            (45.0 * durationUnit + phaseScore).toInt().coerceIn(0, 100)
        } else {
            // Niente fasi: la durata vale da sola tutto il voto.
            (100.0 * durationUnit).toInt().coerceIn(0, 100)
        }
    }

    /**
     * Le notti dell'ultima settimana con voto e distacco, indicizzate per
     * giorno del risveglio. Serve al grafico settimanale della pagina Sonno.
     */
    suspend fun weeklyNightScores(context: Context): Map<java.time.LocalDate, NightScore> {
        val zone = java.time.ZoneId.systemDefault()
        val end = Instant.now()
        val start = end.minusSeconds(8L * 24 * 3600)
        val sessions = readSleep(context, start, end)
        if (sessions.isEmpty()) return emptyMap()
        val phoneOffs = AraldoData.bedtimeEpochs(context).map { Instant.ofEpochMilli(it) }
        val out = HashMap<java.time.LocalDate, NightScore>()
        for (s in sessions) {
            val wakeDay = s.endTime.atZone(zone).toLocalDate()
            val durMin = java.time.Duration.between(s.startTime, s.endTime)
                .toMinutes().coerceAtLeast(0L)
            // Tieni la dormita PIÙ LUNGA del giorno (i pisolini non contano).
            val existing = out[wakeDay]
            if (existing != null && existing.durationMin >= durMin) continue
            val gap = phoneOffs
                .filter {
                    !it.isAfter(s.startTime) &&
                        java.time.Duration.between(it, s.startTime).toHours() < 5
                }
                .maxOrNull()
                ?.let { java.time.Duration.between(it, s.startTime).toMinutes() }
            out[wakeDay] = NightScore(wakeDay, sleepScore(s), durMin, gap)
        }
        return out
    }

    /**
     * Incrocia i distacchi serali registrati dall'Araldo con le dormite di Health
     * Connect delle ultime 2 settimane. Per ogni dormita cerca il distacco più
     * vicino PRIMA dell'addormentamento (entro 5 ore) e calcola il divario.
     */
    suspend fun windDownNights(context: Context): List<WindDownNight> {
        val end = Instant.now()
        val start = end.minusSeconds(14L * 24 * 3600L)
        val sessions = readSleep(context, start, end)
        if (sessions.isEmpty()) return emptyList()
        val phoneOffs = AraldoData.bedtimeEpochs(context).map { Instant.ofEpochMilli(it) }
        val out = mutableListOf<WindDownNight>()
        for (s in sessions) {
            val candidate = phoneOffs
                .filter {
                    !it.isAfter(s.startTime) &&
                        java.time.Duration.between(it, s.startTime).toHours() < 5
                }
                .maxOrNull()
                ?: continue
            val gap = java.time.Duration.between(candidate, s.startTime).toMinutes().coerceAtLeast(0L)
            val dur = java.time.Duration.between(s.startTime, s.endTime).toMinutes().coerceAtLeast(0L)
            out.add(WindDownNight(s.startTime, candidate, gap, dur))
        }
        return out.sortedBy { it.sleepOnset }
    }
}
