package io.github.aedev.flow.sync.youtube

import androidx.compose.runtime.Immutable

@Immutable
data class YouTubeAccountInfo(
    val name: String,
    val handle: String,
    val avatarUrl: String?,
)

@Immutable
data class YouTubeSyncState(
    val isLoggedIn: Boolean = false,
    val cookie: String? = null,
    val account: YouTubeAccountInfo? = null,
    val syncEnabled: Boolean = true,
    val syncLikedSongs: Boolean = true,
    val syncPlaylists: Boolean = true,
    val syncSubscriptions: Boolean = true,
    val syncPlaybackHistory: Boolean = true,
    val syncFrequencyMinutes: Long = 60L,
    val lastSyncTimestamp: Long = 0L,
    val lastSyncStatus: String = "IDLE", // IDLE, SYNCING, SUCCESS, ERROR
    val lastSyncError: String? = null,
    val lastSyncSummary: String? = null,
)

data class YouTubeSyncSummary(
    val likedSongsCount: Int = 0,
    val playlistsCount: Int = 0,
    val subscriptionsCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
)
