package io.github.aedev.flow.ui.screens.settings.youtube

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.nav
import io.github.aedev.flow.ui.components.settings.switch
import io.github.aedev.flow.ui.components.shared.FlowNavRow
import io.github.aedev.flow.ui.screens.settings.index.YouTubeSyncIndex
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun frequencyLabel(minutes: Long): String =
    when (minutes) {
        15L -> "15 dakika"
        30L -> "30 dakika"
        60L -> "1 saat"
        120L -> "2 saat"
        360L -> "6 saat"
        720L -> "12 saat"
        1440L -> "24 saat"
        else -> "$minutes dakika"
    }

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "Henüz eşitlenmedi"
    val instant = Instant.ofEpochMilli(timestamp)
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT)
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}

@Composable
internal fun YouTubeSyncScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    viewModel: YouTubeSyncViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    var showLoginDialog by rememberSaveable { mutableStateOf(false) }
    var showManualDialog by rememberSaveable { mutableStateOf(false) }
    var showFrequencyDialog by rememberSaveable { mutableStateOf(false) }

    val cookieFilePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                viewModel.importFromUri(uri)
            }
        }

    SettingsPage(
        title = stringResource(R.string.youtube_sync_title),
        onBack = onBack,
        highlight = highlight,
        snackbarHostState = snackbarHostState,
    ) {
        // Account Status Card
        item(key = YouTubeSyncIndex.account.key) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border =
                    androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    ),
            ) {
                if (state.isLoggedIn && state.account != null) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!state.account?.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = state.account?.avatarUrl,
                                    contentDescription = state.account?.name,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .size(52.dp)
                                            .clip(CircleShape),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(52.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.account?.name.orEmpty(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (!state.account?.handle.isNullOrBlank()) {
                                    Text(
                                        text = state.account?.handle.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.youtube_sync_connected),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = viewModel::signOut,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.youtube_sync_sign_out))
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.youtube_sync_connect_account_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.youtube_sync_connect_account_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { showLoginDialog = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.youtube_sync_sign_in_google))
                            }

                            OutlinedButton(
                                onClick = { showManualDialog = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.youtube_sync_paste_cookie))
                            }
                        }

                        OutlinedButton(
                            onClick = { cookieFilePicker.launch(arrayOf("text/plain", "*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FileOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.youtube_sync_import_file))
                        }

                        // Check if cookies.txt exists in flow root directory for 1-click import
                        val localCookiesFile = File("/home/oguz/İndirilenler/flow-tr/cookies.txt")
                        val localMusicCookiesFile = File("/home/oguz/İndirilenler/flow-tr/music-cookies.txt")
                        if (localCookiesFile.exists() || localMusicCookiesFile.exists()) {
                            Button(
                                onClick = {
                                    val target = if (localMusicCookiesFile.exists()) localMusicCookiesFile else localCookiesFile
                                    viewModel.tryImportFromLocalFile(target)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.youtube_sync_import_local_cookies))
                            }
                        }
                    }
                }
            }
        }

        // Sync Now Action Row
        if (state.isLoggedIn) {
            item(key = YouTubeSyncIndex.syncNow.key) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border =
                        androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        ),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.youtube_sync_now_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            val statusText =
                                when {
                                    isSyncing -> stringResource(R.string.youtube_sync_status_syncing)
                                    state.lastSyncStatus == "ERROR" -> "Hata: ${state.lastSyncError ?: "Bilinmeyen hata"}"
                                    state.lastSyncTimestamp > 0L -> "Son eşitleme: ${formatTimestamp(state.lastSyncTimestamp)}" +
                                        (state.lastSyncSummary?.let { " ($it)" } ?: "")
                                    else -> "Henüz eşitlenmedi"
                                }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    if (state.lastSyncStatus == "ERROR") {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }

                        Button(
                            onClick = viewModel::syncNow,
                            enabled = !isSyncing,
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.youtube_sync_status_syncing))
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.youtube_sync_action_sync_now))
                            }
                        }
                    }
                }
            }
        }

        // Sync Options Group
        group(
            key = "youtube_sync.options",
            header = R.string.youtube_sync_options_header,
        ) {
            switch(
                entry = YouTubeSyncIndex.continuousSync,
                checked = state.syncEnabled,
                onCheckedChange = viewModel::setSyncEnabled,
                enabled = state.isLoggedIn,
                icon = Icons.Outlined.CloudSync,
            )
            switch(
                entry = YouTubeSyncIndex.likedSongs,
                checked = state.syncLikedSongs,
                onCheckedChange = viewModel::setSyncLikedSongs,
                enabled = state.isLoggedIn && state.syncEnabled,
            )
            switch(
                entry = YouTubeSyncIndex.playlists,
                checked = state.syncPlaylists,
                onCheckedChange = viewModel::setSyncPlaylists,
                enabled = state.isLoggedIn && state.syncEnabled,
            )
            switch(
                entry = YouTubeSyncIndex.subscriptions,
                checked = state.syncSubscriptions,
                onCheckedChange = viewModel::setSyncSubscriptions,
                enabled = state.isLoggedIn && state.syncEnabled,
            )
            switch(
                entry = YouTubeSyncIndex.playbackHistory,
                checked = state.syncPlaybackHistory,
                onCheckedChange = viewModel::setSyncPlaybackHistory,
                enabled = state.isLoggedIn && state.syncEnabled,
            )
            nav(
                entry = YouTubeSyncIndex.frequency,
                value = frequencyLabel(state.syncFrequencyMinutes),
                icon = Icons.Outlined.Schedule,
                enabled = state.isLoggedIn && state.syncEnabled,
                onClick = { showFrequencyDialog = true },
            )
        }
    }

    if (showLoginDialog) {
        YouTubeLoginDialog(
            onDismiss = { showLoginDialog = false },
            onSuccess = { cookie ->
                showLoginDialog = false
                viewModel.loginWithCookie(cookie)
            },
        )
    }

    if (showManualDialog) {
        ManualCookieDialog(
            onDismiss = { showManualDialog = false },
            onSubmit = { cookie ->
                showManualDialog = false
                viewModel.loginWithCookie(cookie)
            },
        )
    }

    if (showFrequencyDialog) {
        FrequencyPickerDialog(
            currentMinutes = state.syncFrequencyMinutes,
            onDismiss = { showFrequencyDialog = false },
            onSelect = { minutes ->
                showFrequencyDialog = false
                viewModel.setSyncFrequencyMinutes(minutes)
            },
        )
    }
}
