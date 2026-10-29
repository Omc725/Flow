package io.github.aedev.flow.ui.screens.settings.index

import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsDestination

internal object YouTubeSyncIndex {
    private val page = SettingsDestination.YOUTUBE_SYNC

    val account =
        SettingEntry(
            key = "youtube_sync.account",
            title = R.string.youtube_sync_account_title,
            summary = R.string.youtube_sync_account_summary,
            destination = page,
        )
    val syncNow =
        SettingEntry(
            key = "youtube_sync.sync_now",
            title = R.string.youtube_sync_now_title,
            summary = R.string.youtube_sync_now_summary,
            destination = page,
        )
    val continuousSync =
        SettingEntry(
            key = "youtube_sync.continuous",
            title = R.string.youtube_sync_continuous_title,
            summary = R.string.youtube_sync_continuous_summary,
            destination = page,
        )
    val likedSongs =
        SettingEntry(
            key = "youtube_sync.liked_songs",
            title = R.string.youtube_sync_liked_songs_title,
            summary = R.string.youtube_sync_liked_songs_summary,
            destination = page,
        )
    val playlists =
        SettingEntry(
            key = "youtube_sync.playlists",
            title = R.string.youtube_sync_playlists_title,
            summary = R.string.youtube_sync_playlists_summary,
            destination = page,
        )
    val subscriptions =
        SettingEntry(
            key = "youtube_sync.subscriptions",
            title = R.string.youtube_sync_subscriptions_title,
            summary = R.string.youtube_sync_subscriptions_summary,
            destination = page,
        )
    val playbackHistory =
        SettingEntry(
            key = "youtube_sync.history",
            title = R.string.youtube_sync_history_title,
            summary = R.string.youtube_sync_history_summary,
            destination = page,
        )
    val frequency =
        SettingEntry(
            key = "youtube_sync.frequency",
            title = R.string.youtube_sync_frequency_title,
            summary = R.string.youtube_sync_frequency_summary,
            destination = page,
        )
}
