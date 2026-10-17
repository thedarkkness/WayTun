package com.waytun.app.core.language

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Per-app language: [AUTO] (follow the system), "en" or "ru". The activity handles locale changes itself
 * (`configChanges="locale"`), so switching re-resolves the strings in place instead of recreating the window.
 *
 * API 33+: the platform [LocaleManager] stores the choice and applies it to every process of the
 * app (including the VPN service processes, so notifications follow too), recreating running
 * activities itself. AppCompatDelegate.setApplicationLocales can't be used for this - on API 33+ it
 * only reaches LocaleManager through a live AppCompatActivity, and MainActivity is a plain
 * ComponentActivity, so the call silently did nothing.
 *
 * API 29-32: no platform support, so the choice is persisted here and applied by wrapping the
 * activity's base context ([wrap]) and recreating it.
 */
object AppLanguage {

    private const val PREFS = "app_language"
    private const val KEY_TAG = "tag"

    const val AUTO = "auto"

    /** The explicit choice ("ru"/"en"), or [AUTO] when the app follows the system language. */
    fun current(context: Context): String = storedTag(context) ?: AUTO

    fun set(activity: Activity, tag: String) {
        // On every path the platform ends up fully recreating the Activity for a per-app locale change -
        // `configChanges="locale"` only covers the system's own locale switch, not this one - so without a
        // custom transition the stock window teardown/rebuild shows as a stark black flash. A fade swap reads
        // as an intentional change instead.
        fadeTransition(activity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == AUTO) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            prefs(activity).edit().apply { if (tag == AUTO) remove(KEY_TAG) else putString(KEY_TAG, tag) }.apply()
            // No platform per-app locales before Android 13: the new base context needs a fresh activity.
            activity.recreate()
        }
    }

    @Suppress("DEPRECATION")
    private fun fadeTransition(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out
            )
            activity.overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out
            )
        } else {
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    /** For Activity.attachBaseContext - a no-op on API 33+, where the platform does this itself. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = prefs(base).getString(KEY_TAG, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    private fun storedTag(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
                .takeIf { !it.isEmpty }?.get(0)?.language
        } else {
            prefs(context).getString(KEY_TAG, null)
        }

    private fun systemLanguage(): String = Resources.getSystem().configuration.locales[0].language

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
