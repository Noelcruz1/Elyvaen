package com.mardous.booming.data.remote.deezer

import android.content.Context

/**
 * Experimental Deezer session storage.
 *
 * The ARL is a personal session cookie, not an OAuth token. It is kept in the
 * app's private preferences and never logged or shown again in the UI.
 */
class DeezerAccountStore(context: Context) {

    private val preferences =
        context.getSharedPreferences("elyvaen_deezer_account", Context.MODE_PRIVATE)

    var arl: String?
        get() = preferences.getString(KEY_ARL, null)
        set(value) = preferences.edit().putString(KEY_ARL, value).apply()

    var userId: String?
        get() = preferences.getString(KEY_USER_ID, null)
        set(value) = preferences.edit().putString(KEY_USER_ID, value).apply()

    var userName: String?
        get() = preferences.getString(KEY_USER_NAME, null)
        set(value) = preferences.edit().putString(KEY_USER_NAME, value).apply()

    var plan: String?
        get() = preferences.getString(KEY_PLAN, null)
        set(value) = preferences.edit().putString(KEY_PLAN, value).apply()

    val isConnected: Boolean
        get() = !arl.isNullOrBlank()

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_ARL = "arl"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_NAME = "user_name"
        const val KEY_PLAN = "plan"
    }
}
