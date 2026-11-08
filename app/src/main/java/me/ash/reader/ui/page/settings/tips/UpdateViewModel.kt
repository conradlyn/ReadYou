package me.ash.reader.ui.page.settings.tips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.ash.reader.domain.service.AppService
import me.ash.reader.infrastructure.net.Download
import me.ash.reader.ui.ext.isGitHub
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val appService: AppService,
) : ViewModel() {

    private val _updateUiState = MutableStateFlow(UpdateUiState())
    val updateUiState: StateFlow<UpdateUiState> = _updateUiState.asStateFlow()

    var updateJob: Job? = null
    private var downloadJob: Job? = null

    fun checkUpdate(
        preProcessor: suspend () -> Unit = {},
        postProcessor: suspend (Boolean) -> Unit = {},
    ) {
        if (!isGitHub) return
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            preProcessor()
            appService.checkUpdate().let {
                it?.let {
                    if (it) {
                        showDialog()
                    } else {
                        hideDialog()
                    }
                    postProcessor(it)
                }
            }
        }
    }

    fun showDialog() {
        _updateUiState.update {
            it.copy(
                updateDialogVisible = true
            )
        }
    }

    fun hideDialog() {
        _updateUiState.update {
            it.copy(
                updateDialogVisible = false
            )
        }
    }

    // Collect here instead of in the dialog: the upstream cold Flow was re-collected on every
    // recomposition / navigation back to the page, which restarted the APK download from zero.
    // A single Job plus a State field also makes "already downloading" and "ready to install"
    // observable states rather than a Flow that has to be replayed.
    fun downloadUpdate(url: String) {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            _updateUiState.update { it.copy(downloadState = Download.Progress(0)) }
            appService.downloadFile(url).collect { state ->
                _updateUiState.update { it.copy(downloadState = state) }
            }
            // AppService returns an empty flow when the request fails (it already shows a toast).
            // Reset the button instead of leaving it stuck at "Update 0%" forever.
            if (_updateUiState.value.downloadState !is Download.Finished) {
                _updateUiState.update { it.copy(downloadState = Download.NotYet) }
            }
        }
    }
}

data class UpdateUiState(
    val updateDialogVisible: Boolean = false,
    val downloadState: Download = Download.NotYet,
)
