package vn.hongducdev.honortoolkit.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.hongducdev.honortoolkit.R
import vn.hongducdev.honortoolkit.core.adb.AdbState
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.NavRow
import vn.hongducdev.honortoolkit.core.ui.SettingsGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenApps: () -> Unit,
    onOpenCircleSearch: () -> Unit,
    onOpenConnection: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(title = { Text(stringResource(R.string.app_name)) }, scrollBehavior = scrollBehavior)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()).padding(bottom = LocalToolbarClearance.current),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConnectionStatusCard(state = state.adb, busy = state.busy, onReconnect = viewModel::reconnect)
            SettingsGroup(stringResource(R.string.home_tools_heading)) {
                // Circle to Search works without ADB (only setting the assistant needs it), so it is always listed.
                NavRow(
                    Icons.Rounded.Search,
                    stringResource(R.string.home_open_circle_search),
                    stringResource(R.string.home_circle_search_description),
                    onOpenCircleSearch,
                )
                if (state.adb == AdbState.Connected) {
                    NavRow(
                        Icons.Rounded.Apps,
                        stringResource(R.string.home_open_apps),
                        stringResource(R.string.home_apps_description),
                        onOpenApps,
                    )
                }
                NavRow(
                    Icons.Rounded.Link,
                    stringResource(R.string.home_open_connection),
                    stringResource(R.string.home_connection_description),
                    onOpenConnection,
                )
            }
        }
    }
}
