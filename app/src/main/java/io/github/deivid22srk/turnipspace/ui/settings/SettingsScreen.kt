package io.github.deivid22srk.turnipspace.ui.settings

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.deivid22srk.turnipspace.R
import io.github.deivid22srk.turnipspace.common.AppLogger
import io.github.deivid22srk.turnipspace.data.shizuku.ShizukuManager
import io.github.deivid22srk.turnipspace.ui.common.InfoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel()
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.refresh() }

    val logExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(AppLogger.readFullLog().toByteArray())
                }
                AppLogger.i("Settings", "log exported to $uri")
            } catch (t: Throwable) {
                AppLogger.e("Settings", "log export failed", t)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            // ---- Shizuku ------------------------------------------------------
            Text(stringResource(R.string.shizuku_section), style = MaterialTheme.typography.titleMedium)
            when (state.shizukuState) {
                ShizukuManager.State.PERMISSION_GRANTED ->
                    InfoCard(stringResource(R.string.shizuku_section), stringResource(R.string.shizuku_granted))
                ShizukuManager.State.AVAILABLE -> {
                    InfoCard(stringResource(R.string.shizuku_section), stringResource(R.string.shizuku_available))
                    Button(onClick = { vm.requestShizuku() }) {
                        Text(stringResource(R.string.shizuku_request_permission))
                    }
                }
                else ->
                    InfoCard(stringResource(R.string.shizuku_section), stringResource(R.string.shizuku_unavailable))
            }

            HorizontalDivider()

            // ---- Logs ----------------------------------------------------------
            Text(stringResource(R.string.logs_section), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { logExport.launch("turnipspace.log") }) {
                    Text(stringResource(R.string.export_logs))
                }
                OutlinedButton(onClick = { AppLogger.clear() }) {
                    Text(stringResource(R.string.clear_logs))
                }
            }

            HorizontalDivider()

            // ---- Limitations (repeated from onboarding) -------------------------
            Text(stringResource(R.string.limitations_title), style = MaterialTheme.typography.titleMedium)
            Limitation(stringResource(R.string.lim_1))
            Limitation(stringResource(R.string.lim_2))
            Limitation(stringResource(R.string.lim_3))
            Limitation(stringResource(R.string.lim_4))
            Limitation(stringResource(R.string.lim_5))

            HorizontalDivider()

            // ---- About -----------------------------------------------------------
            Text(stringResource(R.string.about_section), style = MaterialTheme.typography.titleMedium)
            Text(
                "Turnip Space v${state.appVersion}\n" +
                    "Virtual space runtime + AdrenoTools-compatible Turnip driver manager.\n" +
                    "github.com/deivid22srk/turnip-space",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Limitation(text: String) {
    InfoCard(title = "•", body = text)
}
