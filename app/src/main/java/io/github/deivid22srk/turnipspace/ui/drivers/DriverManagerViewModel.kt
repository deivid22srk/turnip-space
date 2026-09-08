package io.github.deivid22srk.turnipspace.ui.drivers

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.deivid22srk.turnipspace.TurnipSpaceApp
import io.github.deivid22srk.turnipspace.data.gpu.GpuInfo
import io.github.deivid22srk.turnipspace.domain.DriverInfo
import io.github.deivid22srk.turnipspace.domain.UseCases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DriversUiState(
    val drivers: List<DriverInfo> = emptyList(),
    val gpu: GpuInfo? = null,
    val busy: Boolean = false,
    val errorKey: String? = null,
)

class DriverManagerViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as TurnipSpaceApp).container
    private val useCases = UseCases.from(container)

    private val _state = MutableStateFlow(DriversUiState())
    val state: StateFlow<DriversUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val drivers = withContext(Dispatchers.IO) { container.driverRepository.list() }
            _state.value = _state.value.copy(drivers = drivers)
            if (_state.value.gpu == null) {
                val gpu = withContext(Dispatchers.Default) { useCases.observeGpu().first() }
                _state.value = _state.value.copy(gpu = gpu)
            }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val result = withContext(Dispatchers.IO) { useCases.importDriver(uri) }
            val drivers = withContext(Dispatchers.IO) { container.driverRepository.list() }
            _state.value = when (result) {
                is io.github.deivid22srk.turnipspace.domain.DriverImportResult.Success ->
                    _state.value.copy(busy = false, errorKey = null, drivers = drivers)
                is io.github.deivid22srk.turnipspace.domain.DriverImportResult.Failure ->
                    _state.value.copy(busy = false, errorKey = result.errorKey)
            }
        }
    }

    fun delete(driverId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { useCases.deleteDriver(driverId) }
            val drivers = withContext(Dispatchers.IO) { container.driverRepository.list() }
            _state.value = _state.value.copy(drivers = drivers)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorKey = null)
    }
}
