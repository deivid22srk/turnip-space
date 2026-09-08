package io.github.deivid22srk.turnipspace.common

import android.content.Context
import android.content.SharedPreferences

/** Tiny SharedPreferences wrapper for onboarding + UI state flags. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("turnip_space_settings", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    private companion object {
        const val KEY_ONBOARDING = "onboarding_done"
    }
}
