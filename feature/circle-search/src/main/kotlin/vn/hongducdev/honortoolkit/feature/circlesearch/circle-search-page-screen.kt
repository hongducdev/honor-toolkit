package vn.hongducdev.honortoolkit.feature.circlesearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.ProvideToolbar
import vn.hongducdev.honortoolkit.core.ui.ToolbarAction
import vn.hongducdev.honortoolkit.core.ui.ToolbarSpec

enum class CsPage(val titleRes: Int, val descriptionRes: Int, val icon: ImageVector) {
    Trigger(R.string.cs_page_trigger, R.string.cs_page_trigger_desc, Icons.Rounded.TouchApp),
    Wake(R.string.cs_page_wake, R.string.cs_page_wake_desc, Icons.Rounded.Mic),
    Launcher(R.string.cs_page_launcher, R.string.cs_page_launcher_desc, Icons.Rounded.Widgets),
    Strip(R.string.cs_page_strip, R.string.cs_page_strip_desc, Icons.Rounded.SwipeUp),
}

/** One settings page of Circle to Search. The host slides it over the tabs and handles back. */
@Composable
fun CircleSearchPageScreen(
    page: CsPage,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CircleSearchViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProvideToolbar(
        ToolbarSpec.Page(
            title = stringResource(page.titleRes),
            backDescription = stringResource(R.string.cs_back),
            onBack = onBack,
            fab = if (page == CsPage.Trigger) ToolbarAction(Icons.Rounded.PlayArrow, stringResource(R.string.cs_test_button), viewModel::test) else null,
        ),
    )

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(start = 16.dp, end = 16.dp, top = 8.dp).verticalScroll(rememberScrollState()).padding(bottom = LocalToolbarClearance.current),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (page) {
                CsPage.Trigger -> TriggerPage(state, viewModel)
                CsPage.Wake -> WakePage(state, viewModel)
                CsPage.Launcher -> LauncherPage(state, viewModel)
                CsPage.Strip -> StripPage(state, viewModel)
            }
        }
    }
}
