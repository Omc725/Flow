package io.github.aedev.flow.sync.youtube

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.safePreferencesDataStore
import io.github.aedev.flow.data.profile.StoreScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.youtubeSyncDataStore: DataStore<Preferences> by safePreferencesDataStore(
    name = "youtube_sync",
    scope = StoreScope.PROFILE_PRIVATE,
)

@Singleton
class YouTubeSyncPreferences
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
            private val KEY_COOKIE = stringPreferencesKey("cookie")
            private val KEY_ACCOUNT_NAME = stringPreferencesKey("account_name")
            private val KEY_ACCOUNT_HANDLE = stringPreferencesKey("account_handle")
            private val KEY_ACCOUNT_AVATAR = stringPreferencesKey("account_avatar")
            private val KEY_SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
            private val KEY_SYNC_LIKED_SONGS = booleanPreferencesKey("sync_liked_songs")
            private val KEY_SYNC_PLAYLISTS = booleanPreferencesKey("sync_playlists")
            private val KEY_SYNC_SUBSCRIPTIONS = booleanPreferencesKey("sync_subscriptions")
            private val KEY_SYNC_PLAYBACK_HISTORY = booleanPreferencesKey("sync_playback_history")
            private val KEY_SYNC_FREQUENCY_MINUTES = longPreferencesKey("sync_frequency_minutes")
            private val KEY_LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
            private val KEY_LAST_SYNC_STATUS = stringPreferencesKey("last_sync_status")
            private val KEY_LAST_SYNC_ERROR = stringPreferencesKey("last_sync_error")
            private val KEY_LAST_SYNC_SUMMARY = stringPreferencesKey("last_sync_summary")

            /**
             * Parses raw cookie strings in Netscape format (e.g. cookies.txt) or standard HTTP header format.
             */
            fun parseAnyCookieFormat(raw: String): String {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) return ""

                // Detect Netscape format: multiple lines, tab-separated or starts with #
                if (trimmed.contains("\t") || trimmed.lines().any { it.startsWith("#") }) {
                    val map = LinkedHashMap<String, String>()
                    trimmed.lines().forEach { line ->
                        val lineTrimmed = line.trim()
                        if (lineTrimmed.isEmpty() || lineTrimmed.startsWith("#")) return@forEach
                        val parts = lineTrimmed.split("\t")
                        if (parts.size >= 7) {
                            val name = parts[5].trim()
                            val value = parts[6].trim()
                            if (name.isNotEmpty()) {
                                map[name] = value
                            }
                        }
                    }
                    if (map.isNotEmpty()) {
                        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    }
                }

                // Standard header format: strip "Cookie:" prefix if present
                val cleaned = if (trimmed.startsWith("Cookie:", ignoreCase = true)) {
                    trimmed.substring(7).trim()
                } else {
                    trimmed
                }

                val pairs = cleaned.split(";")
                    .mapNotNull {
                        val eq = it.indexOf('=')
                        if (eq > 0) {
                            val k = it.substring(0, eq).trim()
                            val v = it.substring(eq + 1).trim()
                            if (k.isNotEmpty()) k to v else null
                        } else null
                    }
                return pairs.joinToString("; ") { "${it.first}=${it.second}" }
            }
        }

        val state: Flow<YouTubeSyncState> =
            context.youtubeSyncDataStore.data.map { prefs ->
                val isLoggedIn = prefs[KEY_IS_LOGGED_IN] ?: false
                val cookie = prefs[KEY_COOKIE]
                val name = prefs[KEY_ACCOUNT_NAME]
                val handle = prefs[KEY_ACCOUNT_HANDLE]
                val avatar = prefs[KEY_ACCOUNT_AVATAR]

                val account = if (!name.isNullOrBlank() || !handle.isNullOrBlank()) {
                    YouTubeAccountInfo(
                        name = name.orEmpty(),
                        handle = handle.orEmpty(),
                        avatarUrl = avatar,
                    )
                } else null

                YouTubeSyncState(
                    isLoggedIn = isLoggedIn && !cookie.isNullOrBlank(),
                    cookie = cookie,
                    account = account,
                    syncEnabled = prefs[KEY_SYNC_ENABLED] ?: true,
                    syncLikedSongs = prefs[KEY_SYNC_LIKED_SONGS] ?: true,
                    syncPlaylists = prefs[KEY_SYNC_PLAYLISTS] ?: true,
                    syncSubscriptions = prefs[KEY_SYNC_SUBSCRIPTIONS] ?: true,
                    syncPlaybackHistory = prefs[KEY_SYNC_PLAYBACK_HISTORY] ?: true,
                    syncFrequencyMinutes = prefs[KEY_SYNC_FREQUENCY_MINUTES] ?: 60L,
                    lastSyncTimestamp = prefs[KEY_LAST_SYNC_TIME] ?: 0L,
                    lastSyncStatus = prefs[KEY_LAST_SYNC_STATUS] ?: "IDLE",
                    lastSyncError = prefs[KEY_LAST_SYNC_ERROR],
                    lastSyncSummary = prefs[KEY_LAST_SYNC_SUMMARY],
                )
            }

        suspend fun currentState(): YouTubeSyncState = state.first()

        suspend fun saveLogin(
            cookie: String,
            name: String,
            handle: String,
            avatarUrl: String?,
        ) {
            context.youtubeSyncDataStore.edit { prefs ->
                prefs[KEY_IS_LOGGED_IN] = true
                prefs[KEY_COOKIE] = cookie
                prefs[KEY_ACCOUNT_NAME] = name
                prefs[KEY_ACCOUNT_HANDLE] = handle
                if (avatarUrl != null) {
                    prefs[KEY_ACCOUNT_AVATAR] = avatarUrl
                } else {
                    prefs.remove(KEY_ACCOUNT_AVATAR)
                }
                prefs[KEY_LAST_SYNC_STATUS] = "IDLE"
                prefs.remove(KEY_LAST_SYNC_ERROR)
            }
        }

        suspend fun updateAccount(
            name: String,
            handle: String,
            avatarUrl: String?,
        ) {
            context.youtubeSyncDataStore.edit { prefs ->
                prefs[KEY_ACCOUNT_NAME] = name
                prefs[KEY_ACCOUNT_HANDLE] = handle
                if (avatarUrl != null) {
                    prefs[KEY_ACCOUNT_AVATAR] = avatarUrl
                }
            }
        }

        suspend fun signOut() {
            context.youtubeSyncDataStore.edit { prefs ->
                prefs[KEY_IS_LOGGED_IN] = false
                prefs.remove(KEY_COOKIE)
                prefs.remove(KEY_ACCOUNT_NAME)
                prefs.remove(KEY_ACCOUNT_HANDLE)
                prefs.remove(KEY_ACCOUNT_AVATAR)
                prefs[KEY_LAST_SYNC_STATUS] = "IDLE"
                prefs.remove(KEY_LAST_SYNC_ERROR)
                prefs.remove(KEY_LAST_SYNC_SUMMARY)
            }
        }

        suspend fun setSyncEnabled(enabled: Boolean) {
            context.youtubeSyncDataStore.edit { it[KEY_SYNC_ENABLED] = enabled }
        }

        suspend fun setSyncLikedSongs(enabled: Boolean) {
            context.youtubeSyncDataStore.edit { it[KEY_SYNC_LIKED_SONGS] = enabled }
        }

        suspend fun setSyncPlaylists(enabled: Boolean) {
            context.youtubeSyncDataStore.edit { it[KEY_SYNC_PLAYLISTS] = enabled }
        }

        suspend fun setSyncSubscriptions(enabled: Boolean) {
            context.youtubeSyncDataStore.edit { it[KEY_SYNC_SUBSCRIPTIONS] = enabled }
        }

        suspend fun setSyncPlaybackHistory(enabled: Boolean) {
            context.youtubeSyncDataStore.edit { it[KEY_SYNC_PLAYBACK_HISTORY] = enabled }
        }

        suspend fun setSyncFrequencyMinutes(minutes: Long) {
            context.youtubeSyncDataStore.edit { it[KEY_SYNC_FREQUENCY_MINUTES] = minutes }
        }

        suspend fun setSyncStatus(
            status: String,
            error: String? = null,
            summary: String? = null,
        ) {
            context.youtubeSyncDataStore.edit { prefs ->
                prefs[KEY_LAST_SYNC_STATUS] = status
                if (status == "SUCCESS") {
                    prefs[KEY_LAST_SYNC_TIME] = System.currentTimeMillis()
                    prefs.remove(KEY_LAST_SYNC_ERROR)
                }
                if (error != null) {
                    prefs[KEY_LAST_SYNC_ERROR] = error
                } else if (status != "ERROR") {
                    prefs.remove(KEY_LAST_SYNC_ERROR)
                }
                if (summary != null) {
                    prefs[KEY_LAST_SYNC_SUMMARY] = summary
                }
            }
        }
    }
