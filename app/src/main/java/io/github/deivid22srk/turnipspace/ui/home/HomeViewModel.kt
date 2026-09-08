package io.github.deivid22srk.turnipspace.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.deivid22srk.turnipspace.TurnipSpaceApp
import io.github.deivid22srk.turnipspace.domain.UseCases
import io.github.deivid22srk.turnipspace.domain.VirtualSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val useCases = UseCases.from((app as TurnipSpaceApp).container)

    private val _spaces = MutableStateFlow<List<VirtualSpace>>(emptyList())
    val spaces: StateFlow<List<VirtualSpace>> = _spaces

    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _spaces.value = useCases.listSpaces()
        }
    }

    fun createSpace(name: String, onCreated: (VirtualSpace) -> Unit) {
        viewModelScope.launch {
            _creating.value = true
            try {
                val space = withContext(Dispatchers.IO) { useCases.createSpace(name) }
                _spaces.value = useCases.listSpaces()
                onCreated(space)
            } finally {
                _creating.value = false
            }
        }
    }
}
