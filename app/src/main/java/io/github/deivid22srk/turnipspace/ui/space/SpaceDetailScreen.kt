package io.github.deivid22srk.turnipspace.ui.space

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.deivid22srk.turnipspace.common.AppLogger
import io.github.deivid22srk.turnipspace.ui.common.InfoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceDetailScreen(spaceId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: SpaceDetailViewModel = viewModel()
    val state by vm.state.collectAsState()

    LaunchedEffect(spaceId) { vm.bind(spaceId) }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.installApk(uri) { key ->
                val id = context.resources.getIdentifier(key, "string", context.packageName)
                val msg = if (id != 0) context.getString(id) else key
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.space?.name ?: stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.onboarding_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp))
                    Text(stringResource(R.string.installing_apk))
                }
            }

            // ---- App section -------------------------------------------------
            SectionTitle(stringResource(R.string.space_section_app))
            val app = state.space?.installedApp
            if (app == null) {
                InfoCard(
                    title = stringResource(R.string.no_app_installed),
                    body = stringResource(R.string.import_driver_hint),
                )
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${app.packageName} · v${app.versionName ?: "?"} (${app.versionCode})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "minSdk ${app.minSdk} · ABI ${app.abi} · ${app.nativeLibraries.size} native libs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive")) }) {
                    Text(stringResource(R.string.install_apk))
                }
                Button(
                    onClick = { vm.launch(context) { key, detail -> showError(context, key, detail) } },
                    enabled = app != null && !state.busy,
                ) { Text(stringResource(R.string.launch)) }
            }

            HorizontalDivider()

            // ---- Driver section ----------------------------------------------
            SectionTitle(stringResource(R.string.space_section_driver))
            state.drivers.forEach { driver ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.space?.selectedDriverId == driver.id,
                        onClick = { vm.selectDriver(driver.id) },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(driver.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            buildString {
                                append("v${driver.version ?: stringResource(R.string.not_set)}")
                                driver.apiVersion?.let { append(" · ${stringResource(R.string.api_version)} $it") }
                                driver.author?.let { append(" · $it") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = { vm.selectDriver(null) }, enabled = state.space?.selectedDriverId != null) {
                    Text(stringResource(R.string.no_driver_selected))
                }
            }

            HorizontalDivider()

            // ---- Diagnostics ---------------------------------------------------
            SectionTitle(stringResource(R.string.space_section_diagnostics))
            OutlinedButton(onClick = { exportLogs(context) }) {
                Text(stringResource(R.string.export_space_logs))
            }
            OutlinedButton(
                onClick = {
                    vm.deleteSpace(onBack)
                    android.widget.Toast.makeText(
                        context, R.string.space_deleted, android.widget.Toast.LENGTH_SHORT,
                    ).show()
                },
                enabled = !state.busy,
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Text(stringResource(R.string.remove_space), Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

private fun showError(context: android.content.Context, key: String, detail: String?) {
    AppLogger.e("SpaceDetailUI", "user-facing error: $key detail=$detail")
    val message = try {
        val id = context.resources.getIdentifier(key, "string", context.packageName)
        if (id != 0) context.getString(id) else key
    } catch (_: Exception) {
        key
    }
    val full = if (detail.isNullOrBlank()) message else "$message ($detail)"
    android.widget.Toast.makeText(context, full, android.widget.Toast.LENGTH_LONG).show()
}

private fun exportLogs(context: android.content.Context) {
    try {
        val log = io.github.deivid22srk.turnipspace.common.AppLogger.readFullLog()
        val dir = java.io.File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val f = java.io.File(dir, "turnipspace-launch-${System.currentTimeMillis()}.log")
        f.writeText(log)
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.export_logs) + ": " + f.absolutePath,
            android.widget.Toast.LENGTH_LONG,
        ).show()
    } catch (t: Throwable) {
        AppLogger.e("SpaceDetailUI", "log export failed", t)
    }
}
