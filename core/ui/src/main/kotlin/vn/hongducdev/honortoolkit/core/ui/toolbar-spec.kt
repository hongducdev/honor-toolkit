package vn.hongducdev.honortoolkit.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector

class ToolbarAction(val icon: ImageVector, val description: String, val onClick: () -> Unit)

/** What the floating toolbar shows. A screen declares it with [ProvideToolbar]; without one the toolbar shows the tabs. */
sealed interface ToolbarSpec {
    /** Top-level tabs, optionally with a floating action for the current screen. */
    class Tabs(val fab: ToolbarAction? = null) : ToolbarSpec

    /** Sub-page: back button, title, icon actions and an optional floating action. */
    class Page(
        val title: String,
        val backDescription: String,
        val onBack: () -> Unit,
        val actions: List<ToolbarAction> = emptyList(),
        val fab: ToolbarAction? = null,
    ) : ToolbarSpec
}

/** Holds the specs registered by composed screens; the most recent one wins, so a screen entering during a transition takes over. */
class ToolbarHost {
    private class Entry(val owner: Any, val spec: ToolbarSpec)

    private val entries = mutableStateListOf<Entry>()

    val active: ToolbarSpec get() = entries.lastOrNull()?.spec ?: DefaultSpec

    internal fun set(owner: Any, spec: ToolbarSpec) {
        val index = entries.indexOfFirst { it.owner === owner }
        if (index >= 0) entries[index] = Entry(owner, spec) else entries.add(Entry(owner, spec))
    }

    internal fun remove(owner: Any) {
        entries.removeAll { it.owner === owner }
    }

    private companion object {
        val DefaultSpec: ToolbarSpec = ToolbarSpec.Tabs()
    }
}

val LocalToolbarHost = compositionLocalOf { ToolbarHost() }

@Composable
fun ProvideToolbar(spec: ToolbarSpec) {
    val host = LocalToolbarHost.current
    val owner = remember { Any() }
    SideEffect { host.set(owner, spec) }
    DisposableEffect(host, owner) { onDispose { host.remove(owner) } }
}
