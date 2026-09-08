package io.github.deivid22srk.turnipspace.ui.drivers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.deivid22srk.turnipspace.R
import io.github.deivid22srk.turnipspace.domain.GpuFamily
import io.github.deivid22srk.turnipspace.ui.common.EmptyState
import io.github.deivid22srk.turnipspace.ui.common.InfoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: DriverManagerViewModel = viewModel()
    val state by vm.state.collectAsState()

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drivers_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.onboarding_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- GPU detection card ----------------------------------------
            val gpu = state.gpu
            InfoCard(
                title = stringResource(R.string.gpu_detected),
                body = gpu?.let {
                    buildString {
                        append(it.renderer ?: stringResource(R.string.gpu_unknown))
                        it.socModel?.let { soc -> append(" · SoC: $soc") }
                        append("\n${it.recommendedNote ?: ""}")
                    }
                } ?: stringResource(R.string.gpu_unknown),
            )

            Button(
                onClick = {
                    zipPicker.launch(
                        arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"),
                    )
                },
                enabled = !state.busy,
            ) { Text(stringResource(R.string.import_driver)) }
            Text(
                stringResource(R.string.import_driver_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.drivers.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.no_drivers),
                    body = stringResource(R.string.import_driver_hint),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.drivers, key = { it.id }) { driver ->
                        DriverCard(
                            name = driver.name,
                            version = driver.version,
                            author = driver.author,
                            apiVersion = driver.apiVersion,
                            vendor = driver.vendor,
                            gpuFamily = state.gpu?.family,
                            onDelete = { vm.delete(driver.id) },
                        )
                    }
                }
            }
        }
    }

    state.errorKey?.let { key ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.clearError() },
            title = { Text(stringResource(R.string.warning)) },
            text = { Text(stringFromKey(context, key)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { vm.clearError() }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun DriverCard(
    name: String,
    version: String?,
    author: String?,
    apiVersion: Int?,
    vendor: String?,
    gpuFamily: GpuFamily?,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        buildString {
                            append("v").append(version ?: stringResource(R.string.not_set))
                            apiVersion?.let { append(" · ${stringResource(R.string.api_version)} $it") }
                            vendor?.let { append(" · ").append(it) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    author?.let {
                        Text(
                            "${stringResource(R.string.author)}: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.driver_delete))
                }
            }
            val compat = when (gpuFamily) {
                GpuFamily.ADRENO_6XX, GpuFamily.ADRENO_7XX, GpuFamily.ADRENO_8XX ->
                    stringResource(R.string.driver_compatible_hint)
                GpuFamily.ADRENO_LEGACY -> stringResource(R.string.driver_incompatible_hint)
                else -> stringResource(R.string.driver_compat_unknown)
            }
            Text(
                compat,
                style = MaterialTheme.typography.bodySmall,
                color = if (compat == stringResource(R.string.driver_incompatible_hint)) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

private fun stringFromKey(context: android.content.Context, key: String): String = try {
    val id = context.resources.getIdentifier(key, "string", context.packageName)
    if (id != 0) context.getString(id) else key
} catch (_: Exception) {
    key
}
