package io.github.deivid22srk.turnipspace.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.deivid22srk.turnipspace.TurnipSpaceApp
import io.github.deivid22srk.turnipspace.common.AppLogger
import io.github.deivid22srk.turnipspace.data.shizuku.ShizukuManager
import io.github.deivid22srk.turnipspace.domain.UseCases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val shizukuState: ShizukuManager.State = ShizukuManager.State.NOT_INSTALLED,
    val appVersion: String = "",
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as TurnipSpaceApp).container
    private val useCases = UseCases.from(container)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: MutableStateFlow<SettingsUiState> = _state

    init {
        container.shizukuManager.onPermissionResult = { refresh() }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val shizuku = try {
                useCases.shizuku.state()
            } catch (t: Throwable) {
                AppLogger.w("SettingsVM", "shizuku state failed: ${t.message}")
                ShizukuManager.State.NOT_RUNNING
            }
            _state.value = _state.value.copy(shizukuState = shizuku, appVersion = "1.0.0")
        }
    }

    fun requestShizuku() = useCases.shizuku.request()

    fun shizukuAfterRequest() = refresh()
}
