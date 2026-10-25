package vn.hongducdev.honortoolkit.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.hapticfeedback.HapticFeedback
import vn.hongducdev.honortoolkit.core.ui.ToolbarAction
import vn.hongducdev.honortoolkit.core.ui.ToolbarSpec
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

data class ToolbarDestination<T : Any>(
    val id: T,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
)

// Four tabs with the longest label need about 300dp, so labels fit on 365dp phones; below 340dp or at large font they would push the bar off-screen.
private const val LabelHiddenFontScale = 1.25f
private const val CompactScreenWidthDp = 340
private const val CompactDestinationCount = 3

// A fifth tab plus a floating action makes the bar wider than a phone and the layout jumps.
private const val MaxTabs = 4

// The overload with a floating action lays the bar out taller than the one without, which lifts it off the bottom edge.
private val BarHeight = 64.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun <T : Any> ToolkitFloatingToolbar(
    destinations: List<ToolbarDestination<T>>,
    selectedId: T,
    onSelect: (T) -> Unit,
    spec: ToolbarSpec,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    require(destinations.size <= MaxTabs) { "The toolbar holds at most $MaxTabs tabs, got ${destinations.size}" }
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val fontScale = LocalDensity.current.fontScale
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val showLabel = fontScale <= LabelHiddenFontScale &&
        !(screenWidthDp < CompactScreenWidthDp && destinations.size > CompactDestinationCount)

    val toolbarColors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
        toolbarContainerColor = colors.primaryContainer,
        toolbarContentColor = colors.onPrimaryContainer,
    )
    val fab = when (spec) {
        is ToolbarSpec.Tabs -> spec.fab
        is ToolbarSpec.Page -> spec.fab
    }
    // The overload with a FAB slot reserves room for it even when empty, which shifts the bar off-centre.
    val content: @Composable RowScope.() -> Unit = {
        AnimatedContent(
            targetState = spec is ToolbarSpec.Page,
            transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessMedium)) togetherWith fadeOut(spring(stiffness = Spring.StiffnessMedium)) },
            label = "toolbar_mode",
        ) { isPage ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPage && spec is ToolbarSpec.Page) {
                    PageContent(spec, haptics)
                } else {
                    destinations.forEach { destination ->
                        key(destination.id) {
                            ToolbarItem(
                                label = stringResource(destination.labelRes),
                                icon = destination.icon,
                                selected = destination.id == selectedId,
                                showLabel = showLabel,
                                motionEnabled = motionEnabled,
                                onClick = {
                                    if (destination.id != selectedId) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onSelect(destination.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (fab != null) {
        HorizontalFloatingToolbar(
            expanded = true,
            colors = toolbarColors,
            floatingActionButton = { ToolbarFab(fab, haptics) },
            modifier = modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val height = BarHeight.roundToPx()
                layout(placeable.width, height) { placeable.place(0, (height - placeable.height) / 2) }
            },
            content = content,
        )
    } else {
        HorizontalFloatingToolbar(
            expanded = true,
            colors = toolbarColors,
            modifier = modifier,
            content = content,
        )
    }
}

@Composable
private fun PageContent(spec: ToolbarSpec.Page, haptics: HapticFeedback) {
    val colors = MaterialTheme.colorScheme
    FilledIconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            spec.onBack()
        },
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
    ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = spec.backDescription, modifier = Modifier.size(24.dp))
    }
    Text(
        text = spec.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        modifier = Modifier.widthIn(min = 96.dp, max = 200.dp).padding(horizontal = 12.dp).basicMarquee(),
    )
    spec.actions.forEach { action ->
        IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); action.onClick() }) {
            Icon(action.icon, contentDescription = action.description)
        }
    }
}

@Composable
private fun ToolbarFab(action: ToolbarAction, haptics: HapticFeedback) {
    val colors = MaterialTheme.colorScheme
    FloatingActionButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            action.onClick()
        },
        containerColor = colors.primary,
        contentColor = colors.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        Icon(action.icon, contentDescription = action.description)
    }
}

@Composable
private fun ToolbarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    showLabel: Boolean,
    motionEnabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val colorSpec: FiniteAnimationSpec<Color> = if (motionEnabled) spring(stiffness = Spring.StiffnessMedium) else snap()
    val sizeSpec: FiniteAnimationSpec<IntSize> =
        if (motionEnabled) spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow) else snap()
    val container by animateColorAsState(
        targetValue = if (selected) colors.primary else Color.Transparent,
        animationSpec = colorSpec,
        label = "toolbar_item_container",
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onPrimaryContainer,
        animationSpec = colorSpec,
        label = "toolbar_item_content",
    )
    val labelVisible = selected && showLabel

    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(container)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (labelVisible) null else label,
            tint = content,
            modifier = Modifier.size(24.dp),
        )
        AnimatedVisibility(
            visible = labelVisible,
            enter = expandHorizontally(animationSpec = sizeSpec) + fadeIn(),
            exit = shrinkHorizontally(animationSpec = sizeSpec) + fadeOut(),
        ) {
            Text(
                text = label,
                color = content,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
