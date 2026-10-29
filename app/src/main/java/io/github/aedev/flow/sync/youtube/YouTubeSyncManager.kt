package io.github.aedev.flow.sync.youtube

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.AppDatabase
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.entity.PlaylistEntity
import io.github.aedev.flow.data.local.entity.PlaylistVideoCrossRef
import io.github.aedev.flow.data.local.entity.VideoEntity
import io.github.aedev.flow.data.music.PlaylistRepository as MusicPlaylistRepository
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.AccountInfo
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.innertube.utils.completed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

private const val TAG = "YouTubeSyncManager"

@Singleton
class YouTubeSyncManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferences: YouTubeSyncPreferences,
        private val musicLibrary: MusicPlaylistRepository,
        private val likedVideosRepository: LikedVideosRepository,
        private val subscriptionRepository: SubscriptionRepository,
    ) {
        private val database: AppDatabase
            get() = AppDatabase.profile(context)

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val syncMutex = Mutex()

        private val _isSyncing = MutableStateFlow(false)
        val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

        private val _lastSummary = MutableStateFlow<YouTubeSyncSummary?>(null)
        val lastSummary: StateFlow<YouTubeSyncSummary?> = _lastSummary.asStateFlow()

        fun initialize() {
            scope.launch {
                try {
                    val state = preferences.currentState()
                    if (state.isLoggedIn && !state.cookie.isNullOrBlank()) {
                        YouTube.cookie = state.cookie
                        YouTube.useLoginForBrowse = true
                        Log.d(TAG, "YouTube session cookie restored on startup")

                        if (state.syncEnabled) {
                            YouTubeSyncWorker.schedule(context, state.syncFrequencyMinutes)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize YouTubeSyncManager", e)
                }
            }
        }

        suspend fun loginWithCookie(rawCookie: String): Result<AccountInfo> = withContext(Dispatchers.IO) {
            val sanitizedCookie = YouTubeSyncPreferences.parseAnyCookieFormat(rawCookie)
            if (sanitizedCookie.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Geçersiz veya boş çerez"))
            }

            YouTube.cookie = sanitizedCookie
            YouTube.useLoginForBrowse = true

            val accountResult = YouTube.accountInfo()
            accountResult.onSuccess { accountInfo ->
                preferences.saveLogin(
                    cookie = sanitizedCookie,
                    name = accountInfo.name,
                    handle = accountInfo.channelHandle ?: "",
                    avatarUrl = accountInfo.thumbnailUrl,
                )
                Log.d(TAG, "Successfully logged in as: ${accountInfo.name} (${accountInfo.channelHandle})")

                val state = preferences.currentState()
                if (state.syncEnabled) {
                    YouTubeSyncWorker.schedule(context, state.syncFrequencyMinutes)
                    scope.launch { sync(isManual = false) }
                }
            }.onFailure { error ->
                Log.e(TAG, "Account verification failed", error)
                YouTube.cookie = null
                YouTube.useLoginForBrowse = false
            }

            accountResult
        }

        suspend fun signOut() = withContext(Dispatchers.IO) {
            YouTubeSyncWorker.cancel(context)
            preferences.signOut()
            YouTube.cookie = null
            YouTube.useLoginForBrowse = false
            Log.d(TAG, "Signed out from YouTube account")
        }

        suspend fun sync(isManual: Boolean = false): Result<YouTubeSyncSummary> = withContext(Dispatchers.IO) {
            if (!syncMutex.tryLock()) {
                Log.d(TAG, "Sync already in progress, skipping")
                return@withContext Result.failure(IllegalStateException("Eşitleme zaten devam ediyor"))
            }

            _isSyncing.value = true
            preferences.setSyncStatus("SYNCING")

            try {
                val state = preferences.currentState()
                if (!state.isLoggedIn || state.cookie.isNullOrBlank()) {
                    preferences.setSyncStatus("ERROR", error = "Giriş yapılmamış")
                    return@withContext Result.failure(IllegalStateException("Google / YouTube hesabına giriş yapılmamış"))
                }

                YouTube.cookie = state.cookie
                YouTube.useLoginForBrowse = true

                // Step 1: Verify and refresh account details
                val accountResult = YouTube.accountInfo()
                if (accountResult.isFailure) {
                    val err = accountResult.exceptionOrNull()?.message ?: "Hesap oturumu doğrulanamadı"
                    preferences.setSyncStatus("ERROR", error = err)
                    return@withContext Result.failure(Exception(err))
                }
                accountResult.getOrNull()?.let { account ->
                    preferences.updateAccount(
                        name = account.name,
                        handle = account.channelHandle ?: "",
                        avatarUrl = account.thumbnailUrl,
                    )
                }

                var likesCount = 0
                var playlistsCount = 0
                var subscriptionsCount = 0

                // Step 2: Sync Liked Songs (LM)
                if (state.syncLikedSongs) {
                    try {
                        val lmResult = YouTube.playlist("LM").completed()
                        lmResult.onSuccess { page ->
                            likesCount = page.songs.size
                            page.songs.forEach { song ->
                                val artistName = song.artists.joinToString(", ") { it.name }
                                val track = MusicTrack(
                                    videoId = song.id,
                                    title = song.title,
                                    artist = artistName,
                                    duration = song.duration ?: 0,
                                    thumbnailUrl = song.thumbnail,
                                )
                                musicLibrary.addToFavorites(track)
                                likedVideosRepository.likeVideo(
                                    LikedVideoInfo(
                                        videoId = song.id,
                                        title = song.title,
                                        thumbnail = song.thumbnail,
                                        channelName = artistName,
                                        isMusic = true,
                                        channelId = song.artists.firstOrNull()?.id,
                                        durationSeconds = song.duration ?: 0,
                                    ),
                                )
                            }
                            Log.d(TAG, "Synced $likesCount liked songs from YouTube Music")
                        }.onFailure { e ->
                            Log.w(TAG, "Failed to sync Liked Music", e)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error during liked songs sync", e)
                    }
                }

                // Step 3: Sync Playlists (FEmusic_liked_playlists)
                if (state.syncPlaylists) {
                    try {
                        val libraryResult = YouTube.library("FEmusic_liked_playlists").completed()
                        libraryResult.onSuccess { libraryPage ->
                            val playlistItems = libraryPage.items.filterIsInstance<PlaylistItem>().filter { it.id != "LM" }
                            playlistsCount = playlistItems.size

                            playlistItems.forEach { plItem ->
                                try {
                                    val pageResult = YouTube.playlist(plItem.id).completed()
                                    val playlistPage = pageResult.getOrNull()
                                    val songs = playlistPage?.songs ?: emptyList()

                                    database.withTransaction {
                                        val dao = database.playlistDao()
                                        val existing = dao.getPlaylist(plItem.id)
                                        val entity = PlaylistEntity(
                                            id = plItem.id,
                                            name = plItem.title,
                                            description = "",
                                            thumbnailUrl = plItem.thumbnail ?: "",
                                            isPrivate = false,
                                            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                                            isMusic = true,
                                            isUserCreated = plItem.isEditable,
                                            syncId = plItem.id,
                                            videoCount = songs.size,
                                        )
                                        dao.insertPlaylist(entity)

                                        val held = dao.getVideoIdsInPlaylist(plItem.id).toHashSet()
                                        var position = (dao.getMaxPlaylistPosition(plItem.id) ?: -1L) + 1L
                                        songs.forEach { song ->
                                            val artistName = song.artists.joinToString(", ") { it.name }
                                            val videoEntity = VideoEntity(
                                                id = song.id,
                                                title = song.title,
                                                channelName = artistName,
                                                channelId = song.artists.firstOrNull()?.id ?: "",
                                                thumbnailUrl = song.thumbnail,
                                                duration = song.duration ?: 0,
                                                viewCount = 0L,
                                                uploadDate = "",
                                                description = "",
                                                channelThumbnailUrl = "",
                                                isMusic = true,
                                            )
                                            database.videoDao().insertVideoOrIgnore(videoEntity)
                                            if (held.add(song.id)) {
                                                dao.insertPlaylistVideoCrossRef(
                                                    PlaylistVideoCrossRef(
                                                        playlistId = plItem.id,
                                                        videoId = song.id,
                                                        position = position++,
                                                        addedAt = System.currentTimeMillis(),
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to sync playlist ${plItem.title}", e)
                                }
                            }
                            Log.d(TAG, "Synced $playlistsCount playlists from YouTube Music")
                        }.onFailure { e ->
                            Log.w(TAG, "Failed to fetch playlists", e)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error during playlists sync", e)
                    }
                }

                // Step 4: Sync Subscriptions (FEmusic_library_corpus_artists)
                if (state.syncSubscriptions) {
                    try {
                        val artistsResult = YouTube.library("FEmusic_library_corpus_artists").completed()
                        artistsResult.onSuccess { page ->
                            val artists = page.items.filterIsInstance<ArtistItem>()
                            subscriptionsCount = artists.size
                            artists.forEach { artist ->
                                subscriptionRepository.subscribe(
                                    ChannelSubscription(
                                        channelId = artist.id,
                                        channelName = artist.title,
                                        channelThumbnail = artist.thumbnail ?: "",
                                        subscribedAt = System.currentTimeMillis(),
                                        isMusic = true,
                                    ),
                                )
                            }
                            Log.d(TAG, "Synced $subscriptionsCount artist subscriptions")
                        }.onFailure { e ->
                            Log.w(TAG, "Failed to fetch artist subscriptions", e)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error during subscriptions sync", e)
                    }
                }

                val summary = YouTubeSyncSummary(
                    likedSongsCount = likesCount,
                    playlistsCount = playlistsCount,
                    subscriptionsCount = subscriptionsCount,
                    timestamp = System.currentTimeMillis(),
                )
                _lastSummary.value = summary

                val summaryString = "$likesCount şarkı, $playlistsCount liste, $subscriptionsCount sanatçı"
                preferences.setSyncStatus("SUCCESS", summary = summaryString)
                Log.d(TAG, "Sync complete: $summaryString")

                Result.success(summary)
            } catch (t: Throwable) {
                Log.e(TAG, "Sync error", t)
                val errMsg = t.message ?: "Eşitleme sırasında hata oluştu"
                preferences.setSyncStatus("ERROR", error = errMsg)
                Result.failure(t)
            } finally {
                _isSyncing.value = false
                syncMutex.unlock()
            }
        }

        // Continuous real-time synchronization hooks:

        fun onTrackLiked(videoId: String, liked: Boolean) {
            scope.launch {
                try {
                    val state = preferences.currentState()
                    if (!state.isLoggedIn || !state.syncEnabled || !state.syncLikedSongs) return@launch
                    YouTube.likeVideo(videoId, liked)
                    Log.d(TAG, "Synced like ($liked) for video $videoId to YouTube")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync like action to YouTube", e)
                }
            }
        }

        fun onChannelSubscribed(channelId: String, subscribed: Boolean) {
            scope.launch {
                try {
                    val state = preferences.currentState()
                    if (!state.isLoggedIn || !state.syncEnabled || !state.syncSubscriptions) return@launch
                    YouTube.subscribeChannel(channelId, subscribed)
                    Log.d(TAG, "Synced subscription ($subscribed) for channel $channelId to YouTube")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync subscription action to YouTube", e)
                }
            }
        }

        fun onPlaylistTrackAdded(playlistSyncId: String, videoId: String) {
            scope.launch {
                try {
                    val state = preferences.currentState()
                    if (!state.isLoggedIn || !state.syncEnabled || !state.syncPlaylists) return@launch
                    YouTube.addToPlaylist(playlistSyncId, videoId)
                    Log.d(TAG, "Synced added song $videoId to playlist $playlistSyncId on YouTube")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync add track to YouTube playlist", e)
                }
            }
        }

        fun onPlaylistTrackRemoved(playlistSyncId: String, videoId: String, setVideoId: String) {
            scope.launch {
                try {
                    val state = preferences.currentState()
                    if (!state.isLoggedIn || !state.syncEnabled || !state.syncPlaylists) return@launch
                    YouTube.removeFromPlaylist(playlistSyncId, videoId, setVideoId)
                    Log.d(TAG, "Synced removed song $videoId from playlist $playlistSyncId on YouTube")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync remove track from YouTube playlist", e)
                }
            }
        }

        fun reportPlayback(videoId: String) {
            scope.launch {
                try {
                    val state = preferences.currentState()
                    if (!state.isLoggedIn || !state.syncEnabled || !state.syncPlaybackHistory) return@launch
                    val playerRes = YouTube.player(videoId = videoId, client = YouTubeClient.WEB_REMIX).getOrNull()
                    val baseUrl = playerRes?.playbackTracking?.videostatsPlaybackUrl?.baseUrl ?: return@launch
                    val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                    val cpn = (1..16).map { chars[Random.nextInt(chars.length)] }.joinToString("")
                    val url = if (baseUrl.contains("?")) "$baseUrl&cpn=$cpn&ver=2" else "$baseUrl?cpn=$cpn&ver=2"
                    YouTube.pingPlayback(url)
                    Log.d(TAG, "Reported playback scrobble to YouTube for video $videoId")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to report playback to YouTube", e)
                }
            }
        }
    }
