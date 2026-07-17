package com.guardians.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Impostazioni dell'app (schermata con l'ingranaggio). */
object SettingsRepository {

    private const val PREFS = "guardians_prefs"
    private const val KEY_VIBRATE = "vibrate_on_alert"
    private const val KEY_SOUND = "sound_on_alert"
    private const val KEY_DARK_THEME = "dark_theme"
    private const val KEY_ENGLISH = "english_language"
    private const val KEY_WEEKLY_REPORT = "weekly_report"
    private const val KEY_BATTERY_SAVER = "battery_saver"
    private const val KEY_BATTERY_THRESHOLD = "battery_saver_threshold"
    private const val KEY_CONFIRM_ACTIONS = "confirm_actions"
    private const val KEY_DAY_START = "day_start_minute"
    private const val KEY_WEEK_START_MONDAY = "week_start_monday"
    private const val KEY_MONITOR_NOTIF = "monitor_notification"
    private const val KEY_SHOW_NOTIFIER_CARD = "show_notifier_card"
    private const val KEY_APP_ACCENT = "app_accent_type"      // nome TimerType o "" (default)
    private const val KEY_ACCENT_FROM_AVATAR = "app_accent_from_avatar"

    private val _vibrateOnAlert = MutableStateFlow(true)
    val vibrateOnAlert: StateFlow<Boolean> = _vibrateOnAlert

    private val _soundOnAlert = MutableStateFlow(true)
    val soundOnAlert: StateFlow<Boolean> = _soundOnAlert

    private val _darkTheme = MutableStateFlow(true)
    val darkTheme: StateFlow<Boolean> = _darkTheme

    private val _english = MutableStateFlow(false)
    val english: StateFlow<Boolean> = _english

    private val _weeklyReport = MutableStateFlow(true)
    val weeklyReport: StateFlow<Boolean> = _weeklyReport

    // Risparmio batteria: sotto la soglia (10-20%) il monitoraggio si ferma.
    private val _batterySaver = MutableStateFlow(false)
    val batterySaver: StateFlow<Boolean> = _batterySaver

    private val _batteryThreshold = MutableStateFlow(15)
    val batteryThreshold: StateFlow<Int> = _batteryThreshold

    // Conferma globale: se attiva, salvataggi e switch importanti chiedono conferma.
    private val _confirmActions = MutableStateFlow(false)
    val confirmActions: StateFlow<Boolean> = _confirmActions

    // "Inizio del giorno": a che minuto della giornata si azzerano i conteggi.
    private val _dayStartMinute = MutableStateFlow(0)
    val dayStartMinute: StateFlow<Int> = _dayStartMinute

    // Primo giorno della settimana: lunedì (it) o domenica (en) di default.
    private val _weekStartMonday = MutableStateFlow(true)
    val weekStartMonday: StateFlow<Boolean> = _weekStartMonday

    // Notifica fissa di monitoraggio: se spenta, resta discreta/nascosta.
    private val _monitorNotification = MutableStateFlow(true)
    val monitorNotification: StateFlow<Boolean> = _monitorNotification

    // Card del Notificatore in home: NASCOSTA di default, si mostra da Impostazioni.
    // Nascondere la card NON cancella i promemoria salvati (solo visibilità).
    private val _showNotifierCard = MutableStateFlow(false)
    val showNotifierCard: StateFlow<Boolean> = _showNotifierCard

    // Colore dell'app (13): "" = predefinito, altrimenti il nome di un TimerType
    // (palette di quel guardiano). Se accentFromAvatar è attivo, vince il colore
    // del guardiano scelto come stemma del profilo.
    private val _appAccentType = MutableStateFlow("")
    val appAccentType: StateFlow<String> = _appAccentType

    private val _accentFromAvatar = MutableStateFlow(false)
    val accentFromAvatar: StateFlow<Boolean> = _accentFromAvatar

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        _vibrateOnAlert.value = prefs(context).getBoolean(KEY_VIBRATE, true)
        _soundOnAlert.value = prefs(context).getBoolean(KEY_SOUND, true)
        _darkTheme.value = prefs(context).getBoolean(KEY_DARK_THEME, true)
        // Al primo avvio la lingua segue il dispositivo: italiano → italiano,
        // qualsiasi altra lingua → inglese. Poi vale la scelta salvata dall'utente.
        val deviceItalian = try {
            java.util.Locale.getDefault().language == "it"
        } catch (_: Exception) {
            false
        }
        _english.value = prefs(context).getBoolean(KEY_ENGLISH, !deviceItalian)
        _weeklyReport.value = prefs(context).getBoolean(KEY_WEEKLY_REPORT, true)
        _batterySaver.value = prefs(context).getBoolean(KEY_BATTERY_SAVER, false)
        _batteryThreshold.value = prefs(context).getInt(KEY_BATTERY_THRESHOLD, 15).coerceIn(10, 20)
        _confirmActions.value = prefs(context).getBoolean(KEY_CONFIRM_ACTIONS, false)
        _dayStartMinute.value = prefs(context).getInt(KEY_DAY_START, 0).coerceIn(0, 1439)
        // Preset per lingua: italiano → lunedì, altre lingue → domenica.
        _weekStartMonday.value = prefs(context).getBoolean(KEY_WEEK_START_MONDAY, deviceItalian)
        _monitorNotification.value = prefs(context).getBoolean(KEY_MONITOR_NOTIF, true)
        _showNotifierCard.value = prefs(context).getBoolean(KEY_SHOW_NOTIFIER_CARD, false)
        _appAccentType.value = prefs(context).getString(KEY_APP_ACCENT, "") ?: ""
        _accentFromAvatar.value = prefs(context).getBoolean(KEY_ACCENT_FROM_AVATAR, false)
    }

    fun setAppAccentType(context: Context, type: String) {
        _appAccentType.value = type
        prefs(context).edit().putString(KEY_APP_ACCENT, type).apply()
    }

    fun setAccentFromAvatar(context: Context, enabled: Boolean) {
        _accentFromAvatar.value = enabled
        prefs(context).edit().putBoolean(KEY_ACCENT_FROM_AVATAR, enabled).apply()
    }

    fun setMonitorNotification(context: Context, enabled: Boolean) {
        _monitorNotification.value = enabled
        prefs(context).edit().putBoolean(KEY_MONITOR_NOTIF, enabled).apply()
    }

    fun setShowNotifierCard(context: Context, enabled: Boolean) {
        _showNotifierCard.value = enabled
        prefs(context).edit().putBoolean(KEY_SHOW_NOTIFIER_CARD, enabled).apply()
    }

    fun setWeekStartMonday(context: Context, monday: Boolean) {
        _weekStartMonday.value = monday
        prefs(context).edit().putBoolean(KEY_WEEK_START_MONDAY, monday).apply()
    }

    fun setConfirmActions(context: Context, enabled: Boolean) {
        _confirmActions.value = enabled
        prefs(context).edit().putBoolean(KEY_CONFIRM_ACTIONS, enabled).apply()
    }

    fun setDayStartMinute(context: Context, minute: Int) {
        _dayStartMinute.value = minute.coerceIn(0, 1439)
        prefs(context).edit().putInt(KEY_DAY_START, _dayStartMinute.value).apply()
    }

    fun setBatterySaver(context: Context, enabled: Boolean) {
        _batterySaver.value = enabled
        prefs(context).edit().putBoolean(KEY_BATTERY_SAVER, enabled).apply()
    }

    fun setBatteryThreshold(context: Context, percent: Int) {
        val value = percent.coerceIn(10, 20)
        _batteryThreshold.value = value
        prefs(context).edit().putInt(KEY_BATTERY_THRESHOLD, value).apply()
    }

    fun setWeeklyReport(context: Context, enabled: Boolean) {
        _weeklyReport.value = enabled
        prefs(context).edit().putBoolean(KEY_WEEKLY_REPORT, enabled).apply()
    }

    fun setEnglish(context: Context, enabled: Boolean) {
        _english.value = enabled
        prefs(context).edit().putBoolean(KEY_ENGLISH, enabled).apply()
    }

    fun setVibrateOnAlert(context: Context, enabled: Boolean) {
        _vibrateOnAlert.value = enabled
        prefs(context).edit().putBoolean(KEY_VIBRATE, enabled).apply()
    }

    fun setSoundOnAlert(context: Context, enabled: Boolean) {
        _soundOnAlert.value = enabled
        prefs(context).edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    fun setDarkTheme(context: Context, enabled: Boolean) {
        _darkTheme.value = enabled
        prefs(context).edit().putBoolean(KEY_DARK_THEME, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Testo bilingue: italiano di default, inglese se attivato nelle impostazioni. */
fun tr(it: String, en: String): String =
    if (SettingsRepository.english.value) en else it
