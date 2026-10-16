package dev.vibe.gallery.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class LayoutMode { GRID, MASONRY }

enum class ThemeMode { AMOLED, DARK, LIGHT }

data class Prefs(
    val layout: LayoutMode = LayoutMode.GRID,
    val columns: Int = 3,
    val theme: ThemeMode = ThemeMode.AMOLED,
    val accent: Int = ACCENT_BLUE,
    val resume: Boolean = true,
    val autoplay: Boolean = true,
) {
    companion object {
        const val ACCENT_LIME = 0xFFD4FF3A.toInt()
        const val ACCENT_BLUE = 0xFF4C8DFF.toInt()
        const val ACCENT_PINK = 0xFFFF5FA2.toInt()
        const val ACCENT_WHITE = 0xFFF4F4F5.toInt()
        const val ACCENT_SKY = 0xFF5CC8FF.toInt()
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private val kLayout = stringPreferencesKey("layout")
    private val kColumns = intPreferencesKey("columns")
    private val kTheme = stringPreferencesKey("theme")
    private val kAccent = intPreferencesKey("accent")
    private val kResume = booleanPreferencesKey("resume")
    private val kAutoplay = booleanPreferencesKey("autoplay")

    val flow: Flow<Prefs> = context.dataStore.data.map { p ->
        Prefs(
            layout = runCatching { LayoutMode.valueOf(p[kLayout] ?: "GRID") }.getOrDefault(LayoutMode.GRID),
            columns = (p[kColumns] ?: 3).coerceIn(2, 6),
            theme = runCatching { ThemeMode.valueOf(p[kTheme] ?: "AMOLED") }.getOrDefault(ThemeMode.AMOLED),
            accent = p[kAccent] ?: Prefs.ACCENT_BLUE,
            resume = p[kResume] ?: true,
            autoplay = p[kAutoplay] ?: true,
        )
    }

    suspend fun setLayout(v: LayoutMode) { context.dataStore.edit { it[kLayout] = v.name } }
    suspend fun setColumns(v: Int) { context.dataStore.edit { it[kColumns] = v.coerceIn(2, 6) } }
    suspend fun setTheme(v: ThemeMode) { context.dataStore.edit { it[kTheme] = v.name } }
    suspend fun setAccent(v: Int) { context.dataStore.edit { it[kAccent] = v } }
    suspend fun setResume(v: Boolean) { context.dataStore.edit { it[kResume] = v } }
    suspend fun setAutoplay(v: Boolean) { context.dataStore.edit { it[kAutoplay] = v } }
}
