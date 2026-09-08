package io.github.deivid22srk.turnipspace.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.deivid22srk.turnipspace.R
import io.github.deivid22srk.turnipspace.domain.VirtualSpace
import io.github.deivid22srk.turnipspace.ui.common.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenDrivers: () -> Unit,
    onOpenSpace: (String) -> Unit,
) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel()
    val spaces by vm.spaces.collectAsState()
    var showCreate by remember { mutableStateOf(false) }

    // Refresh whenever the screen re-enters composition (e.g. back from detail
    // after a delete) so the list never shows stale spaces.
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onOpenDrivers) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.home_drivers))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.home_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_space))
            }
        },
    ) { padding ->
        if (spaces.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.home_empty_title),
                body = stringResource(R.string.home_empty_body),
                actionLabel = stringResource(R.string.create_space),
                onAction = { showCreate = true },
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer2() }
                items(spaces, key = { it.id }) { space ->
                    SpaceCard(
                        space = space,
                        onClick = { onOpenSpace(space.id) },
                    )
                }
                item { Spacer2() }
            }
        }
    }

    if (showCreate) {
        CreateSpaceDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                showCreate = false
                vm.createSpace(name) { space ->
                    android.widget.Toast.makeText(
                        context, context.getString(R.string.space_created),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    onOpenSpace(space.id)
                }
            },
        )
    }
}

@Composable
private fun SpaceCard(space: VirtualSpace, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(space.name, style = MaterialTheme.typography.titleMedium)
            val app = space.installedApp
            Text(
                text = app?.let { "${it.label} · ${it.versionName ?: it.packageName}" }
                    ?: stringResource(R.string.no_app_installed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.launch),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (space.selectedDriverId != null) {
                        stringResource(R.string.driver_set)
                    } else {
                        stringResource(R.string.no_driver_selected)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun CreateSpaceDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_space)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.space_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.ifBlank { "Space" }) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun Spacer2() = androidx.compose.foundation.layout.Spacer(Modifier.fillMaxWidth())
