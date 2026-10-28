package com.enonymous1.subredditbrowser.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.enonymous1.subredditbrowser.model.DEFAULT_SUBREDDITS
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** The single settings store, shared by the app and its instrumented tests' factory. */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val SELECTED_SUBREDDIT_KEY = stringPreferencesKey("selected_subreddit")
private val CUSTOM_SUBREDDITS_KEY = stringPreferencesKey("custom_subreddits")

/** The persisted subreddit selection and the user's added subreddits. */
interface SettingsRepository {
    val selectedSubreddit: Flow<String>
    val customSubreddits: Flow<List<String>>
    suspend fun setSelectedSubreddit(subreddit: String)
    suspend fun addCustomSubreddit(subreddit: String)
    suspend fun removeCustomSubreddit(subreddit: String)
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val selectedSubreddit: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[SELECTED_SUBREDDIT_KEY] ?: DEFAULT_SUBREDDITS.first()
        }

    override val customSubreddits: Flow<List<String>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[CUSTOM_SUBREDDITS_KEY]
                ?.split("\n")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        }

    override suspend fun setSelectedSubreddit(subreddit: String) {
        dataStore.edit { preferences -> preferences[SELECTED_SUBREDDIT_KEY] = subreddit }
    }

    override suspend fun addCustomSubreddit(subreddit: String) {
        dataStore.edit { preferences ->
            val current = preferences[CUSTOM_SUBREDDITS_KEY]
                ?.split("\n")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            if (subreddit !in current) {
                preferences[CUSTOM_SUBREDDITS_KEY] = (current + subreddit).joinToString("\n")
            }
        }
    }

    override suspend fun removeCustomSubreddit(subreddit: String) {
        dataStore.edit { preferences ->
            val current = preferences[CUSTOM_SUBREDDITS_KEY]
                ?.split("\n")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            preferences[CUSTOM_SUBREDDITS_KEY] = (current - subreddit).joinToString("\n")
        }
    }
}
