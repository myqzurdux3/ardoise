package fr.ardoise.tasks.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.ardoise.tasks.domain.RenderSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

internal val Context.ardoiseDataStore: DataStore<Preferences> by preferencesDataStore(name = "ardoise")

data class ArdoiseSettings(
    val listId: String? = null,
    val listTitle: String = "",
    val maxTasks: Int = DEFAULT_MAX_TASKS,
    val notificationEnabled: Boolean = true,
    /** Off by default: it replaces the user's own lock screen wallpaper. */
    val wallpaperEnabled: Boolean = false,
    val syncIntervalMinutes: Int = DEFAULT_SYNC_MINUTES,
) {
    val isConfigured: Boolean get() = !listId.isNullOrBlank()

    companion object {
        const val DEFAULT_MAX_TASKS = 6
        const val DEFAULT_SYNC_MINUTES = 30
        val MAX_TASKS_CHOICES = listOf(3, 4, 6, 8, 10)
        val SYNC_MINUTES_CHOICES = listOf(15, 30, 60)
    }
}

class SettingsStore(private val context: Context) {

    val settings: Flow<ArdoiseSettings> = context.ardoiseDataStore.data.map { prefs ->
        ArdoiseSettings(
            listId = prefs[KEY_LIST_ID],
            listTitle = prefs[KEY_LIST_TITLE].orEmpty(),
            maxTasks = prefs[KEY_MAX_TASKS] ?: ArdoiseSettings.DEFAULT_MAX_TASKS,
            notificationEnabled = prefs[KEY_NOTIFICATION] ?: true,
            wallpaperEnabled = prefs[KEY_WALLPAPER] ?: false,
            syncIntervalMinutes = prefs[KEY_SYNC_MINUTES] ?: ArdoiseSettings.DEFAULT_SYNC_MINUTES,
        )
    }

    suspend fun current(): ArdoiseSettings = settings.first()

    suspend fun selectList(id: String, title: String) = edit {
        it[KEY_LIST_ID] = id
        it[KEY_LIST_TITLE] = title
    }

    suspend fun setMaxTasks(value: Int) = edit { it[KEY_MAX_TASKS] = value }

    suspend fun setNotificationEnabled(value: Boolean) = edit { it[KEY_NOTIFICATION] = value }

    suspend fun setWallpaperEnabled(value: Boolean) = edit { it[KEY_WALLPAPER] = value }

    suspend fun setSyncIntervalMinutes(value: Int) = edit { it[KEY_SYNC_MINUTES] = value }

    /**
     * Diagnostic state, not a preference.
     *
     * Google only rejects an unregistered build after the account picker, so
     * the silent probe on launch cannot detect it. Remembering the last verdict
     * is what lets the setup card greet the user on the next launch instead of
     * making them walk the whole flow again to see what is wrong.
     */
    val setupRequired: Flow<Boolean> =
        context.ardoiseDataStore.data.map { it[KEY_SETUP_REQUIRED] ?: false }

    suspend fun setSetupRequired(value: Boolean) = edit { it[KEY_SETUP_REQUIRED] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.ardoiseDataStore.edit(block)
    }

    private companion object {
        val KEY_LIST_ID = stringPreferencesKey("list_id")
        val KEY_LIST_TITLE = stringPreferencesKey("list_title")
        val KEY_MAX_TASKS = intPreferencesKey("max_tasks")
        val KEY_NOTIFICATION = booleanPreferencesKey("notification_enabled")
        val KEY_WALLPAPER = booleanPreferencesKey("wallpaper_enabled")
        val KEY_SYNC_MINUTES = intPreferencesKey("sync_minutes")
        val KEY_SETUP_REQUIRED = booleanPreferencesKey("setup_required")
    }
}

/**
 * The cached [RenderSnapshot], stored as one JSON document.
 *
 * A database would be over-engineering for a few dozen lines of text, and the
 * renderers only ever read the whole thing at once.
 */
class SnapshotStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val snapshot: Flow<RenderSnapshot?> = context.ardoiseDataStore.data.map { prefs ->
        prefs[KEY_SNAPSHOT]?.let { raw ->
            runCatching { json.decodeFromString<RenderSnapshot>(raw) }.getOrNull()
        }
    }

    suspend fun current(): RenderSnapshot? = snapshot.first()

    suspend fun save(value: RenderSnapshot) {
        context.ardoiseDataStore.edit { it[KEY_SNAPSHOT] = json.encodeToString(value) }
    }

    /** Marks the cached copy as stale without discarding it, so renderers keep working offline. */
    suspend fun markStale() {
        val existing = current() ?: return
        if (existing.stale) return
        save(existing.copy(stale = true))
    }

    suspend fun lastWallpaperKey(): String? =
        context.ardoiseDataStore.data.first()[KEY_WALLPAPER_KEY]

    suspend fun setLastWallpaperKey(value: String) {
        context.ardoiseDataStore.edit { it[KEY_WALLPAPER_KEY] = value }
    }

    private companion object {
        val KEY_SNAPSHOT = stringPreferencesKey("snapshot_json")
        val KEY_WALLPAPER_KEY = stringPreferencesKey("wallpaper_content_key")
    }
}
