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
