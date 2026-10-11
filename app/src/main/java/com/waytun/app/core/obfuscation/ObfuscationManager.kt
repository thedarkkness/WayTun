package com.waytun.app.core.obfuscation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI-process owner of the obfuscation settings and the current randomized profile. The profile is
 * regenerated every [ObfuscationSettings.ROTATE_INTERVAL_MINUTES] minutes (and on demand); a new
 * connection always starts with the then-current one, and the VPN service generates further ones
 * itself whenever it has to reconnect.
 */
@Singleton
class ObfuscationManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("obfuscation", Context.MODE_PRIVATE)

    init {
        // The defaults changed (everything on, light level): bring existing installs to them once.
        if (prefs.getInt(KEY_DEFAULTS_VERSION, 0) < DEFAULTS_VERSION) {
            prefs.edit().remove(KEY_SETTINGS).remove(KEY_PROFILE).putInt(KEY_DEFAULTS_VERSION, DEFAULTS_VERSION).apply()
        }
    }

    private val _settings = MutableStateFlow(ObfuscationSettings.fromJson(prefs.getString(KEY_SETTINGS, null)))
    val settings: StateFlow<ObfuscationSettings> = _settings.asStateFlow()

    private val _profile = MutableStateFlow(
        ObfuscationProfile.fromJson(prefs.getString(KEY_PROFILE, null)) ?: ObfuscationGenerator.generate(_settings.value)
    )
    val profile: StateFlow<ObfuscationProfile> = _profile.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (true) {
                delay(CHECK_INTERVAL_MS)
                if (isDue()) rotateNow()
            }
        }
    }

    fun update(transform: (ObfuscationSettings) -> ObfuscationSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        prefs.edit().putString(KEY_SETTINGS, updated.toJson()).apply()
        rotateNow()
    }

    fun rotateNow() {
        val fresh = ObfuscationGenerator.generate(_settings.value)
        _profile.value = fresh
        prefs.edit().putString(KEY_PROFILE, fresh.toJson()).apply()
    }

    /** What a new connection should start with: null when obfuscation is off, otherwise values no older than the rotation interval. */
    fun profileForConnect(): ObfuscationProfile? {
        if (!_settings.value.enabled) return null
        if (isDue()) rotateNow()
        return _profile.value
    }

    private fun isDue(): Boolean {
        val s = _settings.value
        return s.enabled && s.autoRotate &&
            System.currentTimeMillis() - _profile.value.generatedAtMillis >= TimeUnit.MINUTES.toMillis(ObfuscationSettings.ROTATE_INTERVAL_MINUTES)
    }

    private companion object {
        const val KEY_SETTINGS = "settings"
        const val KEY_PROFILE = "profile"
        const val KEY_DEFAULTS_VERSION = "defaultsVersion"
        const val DEFAULTS_VERSION = 2
        const val CHECK_INTERVAL_MS = 15_000L
    }
}
