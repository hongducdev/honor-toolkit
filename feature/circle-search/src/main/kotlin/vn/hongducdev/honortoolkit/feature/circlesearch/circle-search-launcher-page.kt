package vn.hongducdev.honortoolkit.feature.circlesearch

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import vn.hongducdev.honortoolkit.core.ui.SettingsGroup
import vn.hongducdev.honortoolkit.core.ui.SettingsNote
import vn.hongducdev.honortoolkit.core.ui.SwitchRow

@Composable
internal fun LauncherPage(state: CircleSearchUiState, viewModel: CircleSearchViewModel) {
    SettingsGroup(stringResource(R.string.cs_launcher_heading)) {
        SettingsNote(stringResource(R.string.cs_launcher_body))
        listOf(
            LauncherEntry.CircleSearch to R.string.cs_launcher_circle_search,
            LauncherEntry.Gemini to R.string.cs_launcher_gemini,
            LauncherEntry.SearchLive to R.string.cs_launcher_live,
        ).forEach { (entry, label) ->
            SwitchRow(stringResource(label), entry in state.launcherEntries) { viewModel.setLauncherEntry(entry, it) }
        }
    }
}
