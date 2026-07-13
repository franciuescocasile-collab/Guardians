package com.guardians.app.model

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.guardians.app.data.tr

/** Categorie d'app riconosciute, con etichetta bilingue e colore per il grafico. */
enum class AppCategory(private val it: String, private val en: String, val colorArgb: Long) {
    SOCIAL("Social", "Social", 0xFFEC407A),
    VIDEO("Video", "Video", 0xFFE53935),
    MUSICA("Musica", "Music", 0xFFAB47BC),
    GIOCHI("Giochi", "Games", 0xFF7E57C2),
    FOTO("Foto", "Photos", 0xFF26A69A),
    NOTIZIE("Notizie", "News", 0xFF42A5F5),
    MAPPE("Mappe", "Maps", 0xFF66BB6A),
    // La messaggistica (WhatsApp, Telegram…) è uno strumento necessario, non
    // svago: NON penalizza la condotta (macro Utility, peso 0).
    MESSAGGISTICA("Messaggistica", "Messaging", 0xFF26C6DA),
    PRODUTTIVITA("Produttività", "Productivity", 0xFF29B6F6),
    ALTRO("Altro", "Other", 0xFF90A4AE),
    ;

    val label: String get() = tr(it, en)

    /** Macro-categoria d'appartenenza, usata dal motore della condotta. */
    val macro: MacroCategory
        get() = when (this) {
            SOCIAL, GIOCHI -> MacroCategory.SOCIAL_GAMING
            VIDEO, MUSICA -> MacroCategory.INTRATTENIMENTO
            PRODUTTIVITA, NOTIZIE -> MacroCategory.PRODUTTIVITA
            FOTO, MAPPE, MESSAGGISTICA, ALTRO -> MacroCategory.UTILITY
        }
}

/**
 * Le macro-categorie del motore della condotta, con il "peso dopaminergico"
 * (coefficiente di tossicità) usato per pesare il tempo speso: più una categoria
 * è potenzialmente compulsiva, più conta nel calcolo del tempo risparmiato e del
 * decadimento della condotta. I valori vivono in ConductConfig e sono qui solo
 * come riferimento leggibile.
 */
enum class MacroCategory(private val it: String, private val en: String) {
    SOCIAL_GAMING("Social e Giochi", "Social & Gaming"),
    INTRATTENIMENTO("Intrattenimento", "Entertainment"),
    PRODUTTIVITA("Produttività", "Productivity"),
    UTILITY("Utility", "Utility"),
    ;

    val label: String get() = tr(it, en)
}

/**
 * Categoria di un'app: prima il dizionario interno per le app popolari (che spesso
 * non dichiarano la categoria), poi la categoria di sistema, infine "Altro".
 */
fun categoryOf(context: Context, packageName: String): AppCategory {
    KNOWN[packageName]?.let { return it }
    return try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= 26) fromSystem(info.category) else AppCategory.ALTRO
    } catch (_: Exception) {
        AppCategory.ALTRO
    }
}

private fun fromSystem(category: Int): AppCategory = when (category) {
    ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
    ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO
    ApplicationInfo.CATEGORY_AUDIO -> AppCategory.MUSICA
    ApplicationInfo.CATEGORY_GAME -> AppCategory.GIOCHI
    ApplicationInfo.CATEGORY_IMAGE -> AppCategory.FOTO
    ApplicationInfo.CATEGORY_NEWS -> AppCategory.NOTIZIE
    ApplicationInfo.CATEGORY_MAPS -> AppCategory.MAPPE
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUTTIVITA
    else -> AppCategory.ALTRO
}

/** Dizionario delle app più diffuse che spesso non dichiarano la categoria di sistema. */
private val KNOWN: Map<String, AppCategory> = mapOf(
    "com.instagram.android" to AppCategory.SOCIAL,
    "com.zhiliaoapp.musically" to AppCategory.SOCIAL,
    "com.facebook.katana" to AppCategory.SOCIAL,
    "com.facebook.lite" to AppCategory.SOCIAL,
    "com.twitter.android" to AppCategory.SOCIAL,
    "com.snapchat.android" to AppCategory.SOCIAL,
    "com.reddit.frontpage" to AppCategory.SOCIAL,
    "com.linkedin.android" to AppCategory.SOCIAL,
    "com.pinterest" to AppCategory.SOCIAL,
    // Messaggistica: necessaria per organizzarsi, non è svago.
    "org.telegram.messenger" to AppCategory.MESSAGGISTICA,
    "com.whatsapp" to AppCategory.MESSAGGISTICA,
    "com.whatsapp.w4b" to AppCategory.MESSAGGISTICA,
    "org.thoughtcrime.securesms" to AppCategory.MESSAGGISTICA,   // Signal
    "com.facebook.orca" to AppCategory.MESSAGGISTICA,            // Messenger
    "com.google.android.apps.messaging" to AppCategory.MESSAGGISTICA,
    "com.discord" to AppCategory.MESSAGGISTICA,
    "com.google.android.youtube" to AppCategory.VIDEO,
    "com.netflix.mediaclient" to AppCategory.VIDEO,
    "com.amazon.avod.thirdpartyclient" to AppCategory.VIDEO,
    "tv.twitch.android.app" to AppCategory.VIDEO,
    "com.spotify.music" to AppCategory.MUSICA,
    "com.google.android.apps.youtube.music" to AppCategory.MUSICA,
    "com.google.android.gm" to AppCategory.PRODUTTIVITA,
    "com.google.android.apps.docs" to AppCategory.PRODUTTIVITA,
    "com.microsoft.office.outlook" to AppCategory.PRODUTTIVITA,
    "com.google.android.googlequicksearchbox" to AppCategory.PRODUTTIVITA,
    "com.android.chrome" to AppCategory.PRODUTTIVITA,
    "com.google.android.apps.maps" to AppCategory.MAPPE,
)
