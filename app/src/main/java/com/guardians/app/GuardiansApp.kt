package com.guardians.app

import android.app.Application
import com.guardians.app.data.CrashReporter
import com.guardians.app.data.ExclusionsRepository
import com.guardians.app.data.SettingsRepository
import com.guardians.app.data.StatsRepository
import com.guardians.app.data.TimerRepository

class GuardiansApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // PRIMA di tutto: cattura ogni crash non gestito (UI o servizio) e lo
        // salva su file. Sui Samsung il logcat via adb è muto, così il crash
        // (es. aprendo un gioco) resta comunque leggibile a posteriori.
        CrashReporter.install(this)
        TimerRepository.load(this)
        SettingsRepository.load(this)
        StatsRepository.load(this)
        ExclusionsRepository.load(this)
    }
}
