package io.github.aedev.flow.ui.screens.settings.youtube

import android.content.Context
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.sync.youtube.YouTubeSyncManager
import io.github.aedev.flow.sync.youtube.YouTubeSyncPreferences
import io.github.aedev.flow.sync.youtube.YouTubeSyncState
import io.github.aedev.flow.sync.youtube.YouTubeSyncWorker
import io.github.aedev.flow.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface LoginProgressState {
    data object Idle : LoginProgressState

    data object LoggingIn : LoginProgressState

    data class Success(
        val name: String,
    ) : LoginProgressState

    data class Error(
        val message: String,
    ) : LoginProgressState
}

@HiltViewModel
class YouTubeSyncViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val syncManager: YouTubeSyncManager,
        private val preferences: YouTubeSyncPreferences,
    ) : SettingsViewModel() {
        val state: StateFlow<YouTubeSyncState> = preferences.state.asState(YouTubeSyncState())
        val isSyncing: StateFlow<Boolean> = syncManager.isSyncing

        private val _loginState = MutableStateFlow<LoginProgressState>(LoginProgressState.Idle)
        val loginState: StateFlow<LoginProgressState> = _loginState.asStateFlow()

        private val _messages = Channel<String>(Channel.BUFFERED)
        val messages = _messages.receiveAsFlow()

        fun resetLoginState() {
            _loginState.value = LoginProgressState.Idle
        }

        fun loginWithCookie(rawCookie: String) {
            viewModelScope.launch {
                _loginState.value = LoginProgressState.LoggingIn
                val result = syncManager.loginWithCookie(rawCookie)
                result.onSuccess { account ->
                    _loginState.value = LoginProgressState.Success(account.name)
                    _messages.send("Giriş yapıldı: ${account.name}")
                }.onFailure { error ->
                    val errMsg = error.message ?: "Giriş başarısız oldu"
                    _loginState.value = LoginProgressState.Error(errMsg)
                    _messages.send("Hata: $errMsg")
                }
            }
        }

        fun signOut() {
            viewModelScope.launch {
                syncManager.signOut()
                _messages.send("Hesaptan çıkış yapıldı")
            }
        }

        fun syncNow() {
            viewModelScope.launch {
                YouTubeSyncWorker.syncNow(context)
                val result = syncManager.sync(isManual = true)
                result.onSuccess { summary ->
                    val msg = "Eşitlendi: ${summary.likedSongsCount} şarkı, ${summary.playlistsCount} liste"
                    _messages.send(msg)
                }.onFailure { err ->
                    _messages.send("Eşitleme hatası: ${err.message}")
                }
            }
        }

        fun setSyncEnabled(enabled: Boolean) {
            viewModelScope.launch {
                preferences.setSyncEnabled(enabled)
                if (enabled) {
                    val current = preferences.currentState()
                    YouTubeSyncWorker.schedule(context, current.syncFrequencyMinutes)
                } else {
                    YouTubeSyncWorker.cancel(context)
                }
            }
        }

        fun setSyncLikedSongs(enabled: Boolean) {
            viewModelScope.launch { preferences.setSyncLikedSongs(enabled) }
        }

        fun setSyncPlaylists(enabled: Boolean) {
            viewModelScope.launch { preferences.setSyncPlaylists(enabled) }
        }

        fun setSyncSubscriptions(enabled: Boolean) {
            viewModelScope.launch { preferences.setSyncSubscriptions(enabled) }
        }

        fun setSyncPlaybackHistory(enabled: Boolean) {
            viewModelScope.launch { preferences.setSyncPlaybackHistory(enabled) }
        }

        fun setSyncFrequencyMinutes(minutes: Long) {
            viewModelScope.launch {
                preferences.setSyncFrequencyMinutes(minutes)
                val current = preferences.currentState()
                if (current.syncEnabled) {
                    YouTubeSyncWorker.schedule(context, minutes)
                }
            }
        }

        fun tryImportFromLocalFile(file: File) {
            viewModelScope.launch {
                try {
                    if (!file.exists()) {
                        _messages.send("Dosya bulunamadı: ${file.name}")
                        return@launch
                    }
                    val text = file.readText(Charsets.UTF_8)
                    loginWithCookie(text)
                } catch (e: Exception) {
                    _messages.send("Dosya okunamadı: ${e.message}")
                }
            }
        }

        fun importFromUri(uri: android.net.Uri) {
            viewModelScope.launch {
                try {
                    val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    }
                    if (text.isNullOrBlank()) {
                        _messages.send("Dosya boş veya okunamadı")
                        return@launch
                    }
                    loginWithCookie(text)
                } catch (e: Exception) {
                    _messages.send("Dosya okunamadı: ${e.message}")
                }
            }
        }
    }
