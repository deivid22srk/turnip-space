package io.github.deivid22srk.turnipspace.ui.space

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.deivid22srk.turnipspace.R
import io.github.deivid22srk.turnipspace.TurnipSpaceApp
import io.github.deivid22srk.turnipspace.common.AppLogger
import io.github.deivid22srk.turnipspace.domain.DriverInfo
import io.github.deivid22srk.turnipspace.domain.LaunchResult
import io.github.deivid22srk.turnipspace.domain.UseCases
import io.github.deivid22srk.turnipspace.domain.VirtualSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SpaceUiState(
    val space: VirtualSpace? = null,
    val drivers: List<DriverInfo> = emptyList(),
    val busy: Boolean = false,
    val messageKey: String? = null,
    val messageDetail: String? = null,
)

class SpaceDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as TurnipSpaceApp).container
    private val useCases = UseCases.from(container)

    private val _state = MutableStateFlow(SpaceUiState())
    val state: StateFlow<SpaceUiState> = _state

    private var spaceId: String = ""

    fun bind(id: String) {
        if (spaceId == id && _state.value.space != null) return
        spaceId = id
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val space = container.spaceRepository.get(spaceId)
            _state.value = _state.value.copy(space = space, drivers = container.driverRepository.list())
        }
    }

    fun installApk(uri: Uri, onError: (String) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val result = withContext(Dispatchers.IO) { useCases.installApk(spaceId, uri) }
            _state.value = when (result) {
                is io.github.deivid22srk.turnipspace.domain.ApkInstallResult.Success ->
                    _state.value.copy(busy = false, messageKey = null, space = container.spaceRepository.get(spaceId))
                is io.github.deivid22srk.turnipspace.domain.ApkInstallResult.Failure ->
                    _state.value.copy(busy = false, messageKey = result.errorKey, messageDetail = result.detail)
            }
            if (_state.value.messageKey != null) onError(_state.value.messageKey ?: "error_generic")
        }
    }

    fun launch(context: Context, onError: (String, String?) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val result = withContext(Dispatchers.IO) { useCases.launchSpace(context, spaceId) }
            _state.value = _state.value.copy(busy = false)
            when (result) {
                is LaunchResult.Success -> {
                    AppLogger.i("SpaceDetail", "launch ok hook=${result.hookActive} driver=${result.driverPreloaded}")
                }
                is LaunchResult.Failure -> onError(result.errorKey, result.detail)
            }
        }
    }

    fun selectDriver(driverId: String?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { useCases.selectDriver(spaceId, driverId) }
            refresh()
        }
    }

    fun deleteSpace(onDeleted: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { useCases.deleteSpace(spaceId) }
            onDeleted()
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(messageKey = null, messageDetail = null)
    }
}
