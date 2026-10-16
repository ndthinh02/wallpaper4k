package com.example.wallpaper.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore by preferencesDataStore("walper_settings")

data class Settings(
    val onboarded: Boolean = false, val theme: String = "system", val language: String = "vi",
    val grid: String = "staggered", val notifications: Boolean = false, val auto: Boolean = false,
    val hours: Int = 6, val target: Int = 1, val lastWallpaper: String = ""
)

class SettingsRepository(context: Context) {
    private val store = context.settingsStore
    val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        Settings(
            p[booleanPreferencesKey("onboarded")] ?: false, p[stringPreferencesKey("theme")] ?: "system",
            p[stringPreferencesKey("language")] ?: "vi", p[stringPreferencesKey("grid")] ?: "staggered",
            p[booleanPreferencesKey("notifications")] ?: false, p[booleanPreferencesKey("auto")] ?: false,
            p[intPreferencesKey("hours")] ?: 6, p[intPreferencesKey("target")] ?: 1,
            p[stringPreferencesKey("lastWallpaper")] ?: ""
        )
    }

    suspend fun text(key: String, value: String) {
        store.edit { it[stringPreferencesKey(key)] = value }
    }

    suspend fun flag(key: String, value: Boolean) {
        store.edit { it[booleanPreferencesKey(key)] = value }
    }

    suspend fun auto(enabled: Boolean, hours: Int, target: Int) {
        store.edit {
            it[booleanPreferencesKey("auto")] = enabled; it[intPreferencesKey("hours")] =
            hours; it[intPreferencesKey("target")] = target
        }
    }
}
