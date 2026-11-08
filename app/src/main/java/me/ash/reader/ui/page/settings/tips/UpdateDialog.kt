package me.ash.reader.ui.page.settings.tips

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import me.ash.reader.R
import me.ash.reader.domain.service.isForkApkDownload
import me.ash.reader.infrastructure.net.Download
import me.ash.reader.infrastructure.preference.*
import me.ash.reader.ui.component.base.RYDialog
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.installLatestApk
import me.ash.reader.ui.ext.isGitHub
import me.ash.reader.ui.ext.openURL

@Composable
fun UpdateDialog(
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val updateUiState = updateViewModel.updateUiState.collectAsStateValue()
    val downloadState = updateUiState.downloadState
    val scope = rememberCoroutineScope { Dispatchers.IO }
    val newVersionNumber = LocalNewVersionNumber.current
    val newVersionPublishDate = LocalNewVersionPublishDate.current
    val newVersionLog = LocalNewVersionLog.current
    val newVersionSize = LocalNewVersionSize.current
    val newVersionDownloadUrl = LocalNewVersionDownloadUrl.current
    val releasesUrl = "${context.getString(R.string.github_link)}/releases"

    // Android 8+ gates sideloading behind a per-app setting. The system installer does the actual
    // install; this app only needs permission to request it (declared in the github flavor only).
    val settings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (context.packageManager.canRequestPackageInstalls()) {
            context.installLatestApk()
        }
    }

    fun requestInstall() {
        if (context.packageManager.canRequestPackageInstalls()) {
            context.installLatestApk()
        } else {
            try {
                settings.launch(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            } catch (e: Throwable) {
                Log.e("RLog", "cannot open install settings: ${e.message}")
                context.openURL(releasesUrl, OpenLinkPreference.AutoPreferCustomTabs)
            }
        }
    }

    // The upstream code called installLatestApk() from inside a Text() composable, i.e. during
    // composition. Drive the installer from the download state instead, once per finished download.
    val downloadFinished = isGitHub && downloadState is Download.Finished
    LaunchedEffect(downloadFinished) {
        if (downloadFinished) {
            val file = (downloadState as Download.Finished).file
            Log.i("RLog", "Download.Finished: ${file.absolutePath}")
            requestInstall()
        }
    }

    RYDialog(
        modifier = Modifier.heightIn(max = 400.dp),
        visible = updateUiState.updateDialogVisible,
        onDismissRequest = { updateViewModel.hideDialog() },
        icon = {
            Icon(
                imageVector = Icons.Rounded.Update,
                contentDescription = stringResource(R.string.change_log),
            )
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = stringResource(R.string.change_log))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "$newVersionPublishDate $newVersionSize",
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        },
        text = {
            SelectionContainer {
                Text(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    text = newVersionLog,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        // Already downloaded: just (re)open the system installer.
                        downloadState is Download.Finished -> requestInstall()
                        // One download at a time; the ViewModel also guards this.
                        downloadState is Download.Progress -> Unit
                        // F-Droid / Google Play must keep the old behaviour, and a URL that is not
                        // one of this fork's release assets is never handed to the installer.
                        !isGitHub || !isForkApkDownload(newVersionDownloadUrl) ->
                            context.openURL(releasesUrl, OpenLinkPreference.AutoPreferCustomTabs)
                        else -> updateViewModel.downloadUpdate(newVersionDownloadUrl)
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.update) + when (downloadState) {
                        is Download.NotYet -> ""
                        is Download.Progress -> " ${downloadState.percent}%"
                        is Download.Finished -> ""
                    }
                )
            }
        },
        dismissButton = {
            if (downloadState !is Download.Progress) {
                TextButton(
                    onClick = {
                        SkipVersionNumberPreference.put(context, scope, newVersionNumber.toString())
                        updateViewModel.hideDialog()
                    }
                ) {
                    Text(text = stringResource(R.string.skip_this_version))
                }
            }
        },
    )
}
