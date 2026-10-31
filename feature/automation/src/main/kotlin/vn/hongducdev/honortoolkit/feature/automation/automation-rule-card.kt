package vn.hongducdev.honortoolkit.feature.automation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal class RuleCardCallbacks(
    val onClick: () -> Unit,
    val onToggle: () -> Unit,
    val onTest: () -> Unit,
    val onDelete: () -> Unit,
    val onSelectedChange: (Boolean) -> Unit,
)

/**
 * One rule as a trigger chip pointing at an action chip. A state rule gets a second arrow back, and
 * its exit actions are listed under the entry actions. Long-press opens the menu; in selection
 * mode a tap toggles the checkbox.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RuleCard(
    rule: AutomationRule,
    live: Boolean,
    blocked: Boolean,
    shape: Shape,
    selectionMode: Boolean,
    selected: Boolean,
    callbacks: RuleCardCallbacks,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        if (selectionMode) callbacks.onSelectedChange(!selected) else callbacks.onClick()
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (selectionMode) callbacks.onSelectedChange(!selected) else menuOpen = true
                    },
                )
                .alpha(if (live) 1f else 0.55f),
        ) {
            Box {
                RuleMenu(
                    open = menuOpen,
                    live = rule.enabled,
                    onDismiss = { menuOpen = false },
                    callbacks = callbacks,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp).height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedVisibility(
                        visible = selectionMode,
                        enter = expandHorizontally() + fadeIn(),
                        exit = shrinkHorizontally() + fadeOut(),
                    ) {
                        Checkbox(checked = selected, onCheckedChange = callbacks.onSelectedChange)
                    }
                    TriggerChip(rule, modifier = Modifier.weight(1f).fillMaxHeight())
                    Arrows(isState = rule.isState)
                    ActionsChip(rule, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        if (blocked) {
            Text(
                stringResource(R.string.automation_rule_disabled_permission),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun TriggerChip(rule: AutomationRule, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                rule.trigger.icon(),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    rule.trigger.summary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Arrows(isState: Boolean) {
    Column(
        modifier = Modifier.fillMaxHeight().width(30.dp),
        verticalArrangement = if (isState) Arrangement.SpaceAround else Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArrowDot(forward = true)
        if (isState) ArrowDot(forward = false)
    }
}

@Composable
private fun ArrowDot(forward: Boolean) {
    Box(
        modifier = Modifier.size(24.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (forward) Icons.AutoMirrored.Rounded.ArrowForward else Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun ActionsChip(rule: AutomationRule, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionLines(rule.actions, prefix = null)
            if (rule.exitActions.isNotEmpty()) ActionLines(rule.exitActions, prefix = stringResource(R.string.automation_exit_prefix))
        }
    }
}

@Composable
private fun ActionLines(actions: List<Action>, prefix: String?) {
    val shown = actions.take(MAX_LINES)
    shown.forEach { action ->
        val text = if (prefix != null) prefix.format(action.summary()) else action.summary()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                action.icon(),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
    if (actions.size > MAX_LINES) {
        Text(
            "+${actions.size - MAX_LINES}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun RuleMenu(open: Boolean, live: Boolean, onDismiss: () -> Unit, callbacks: RuleCardCallbacks) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        MenuItem(
            if (live) R.string.automation_disable else R.string.automation_enable,
            if (live) Icons.Rounded.PowerSettingsNew else Icons.Rounded.CheckCircle,
            onDismiss,
            callbacks.onToggle,
        )
        MenuItem(R.string.automation_test, Icons.Rounded.PlayArrow, onDismiss, callbacks.onTest)
        MenuItem(R.string.automation_edit, Icons.Rounded.Edit, onDismiss, callbacks.onClick)
        MenuItem(R.string.automation_select, Icons.Rounded.CheckCircle, onDismiss) { callbacks.onSelectedChange(true) }
        MenuItem(R.string.automation_delete, Icons.Rounded.Delete, onDismiss, callbacks.onDelete)
    }
}

@Composable
private fun MenuItem(label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, dismiss: () -> Unit, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = {
            dismiss()
            onClick()
        },
    )
}

private const val MAX_LINES = 3
