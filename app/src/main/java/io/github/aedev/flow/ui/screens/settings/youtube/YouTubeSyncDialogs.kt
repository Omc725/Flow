package io.github.aedev.flow.ui.screens.settings.youtube

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog

private const val CHROME_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private const val GOOGLE_SIGN_IN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginDialog(
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit,
) {
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top header bar
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.youtube_login_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { loadingProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams =
                                    ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    userAgentString = CHROME_USER_AGENT
                                }

                                webChromeClient =
                                    object : WebChromeClient() {
                                        override fun onProgressChanged(
                                            view: WebView?,
                                            newProgress: Int,
                                        ) {
                                            loadingProgress = newProgress / 100f
                                            isLoading = newProgress < 100
                                        }
                                    }

                                webViewClient =
                                    object : WebViewClient() {
                                        override fun onPageStarted(
                                            view: WebView?,
                                            url: String?,
                                            favicon: Bitmap?,
                                        ) {
                                            super.onPageStarted(view, url, favicon)
                                            checkCookies(url)
                                        }

                                        override fun onPageFinished(
                                            view: WebView?,
                                            url: String?,
                                        ) {
                                            super.onPageFinished(view, url)
                                            checkCookies(url)
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                        ): Boolean {
                                            checkCookies(request?.url?.toString())
                                            return false
                                        }

                                        private fun checkCookies(url: String?) {
                                            if (url == null) return
                                            val ytCookies = cookieManager.getCookie("https://www.youtube.com").orEmpty()
                                            val ytmCookies = cookieManager.getCookie("https://music.youtube.com").orEmpty()
                                            val combined = "$ytCookies; $ytmCookies"

                                            if (combined.contains("SAPISID") || combined.contains("__Secure-3PAPISID")) {
                                                onSuccess(combined)
                                            }
                                        }
                                    }

                                loadUrl(GOOGLE_SIGN_IN_URL)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
fun ManualCookieDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }

    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.youtube_sync_manual_cookie_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.youtube_sync_manual_cookie_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.youtube_sync_cookie_label)) },
                    placeholder = { Text("SAPISID=...; SSID=... veya Netscape formatı") },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    maxLines = 10,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(text) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.settings_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun FrequencyPickerDialog(
    currentMinutes: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    val options =
        listOf(
            15L to "15 dakika",
            30L to "30 dakika",
            60L to "1 saat",
            120L to "2 saat",
            360L to "6 saat",
            720L to "12 saat",
            1440L to "24 saat (Günde bir)",
        )

    var selected by rememberSaveable { mutableStateOf(currentMinutes) }

    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.youtube_sync_frequency_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                options.forEach { (minutes, label) ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = minutes }
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (selected == minutes),
                            onClick = { selected = minutes },
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSelect(selected) }) {
                Text(stringResource(R.string.settings_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
