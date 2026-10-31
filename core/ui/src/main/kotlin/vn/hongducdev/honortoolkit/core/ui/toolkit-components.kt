package vn.hongducdev.honortoolkit.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val GroupGap = 2.dp

/** Shared top bar. A back arrow shows only when [onBack] is given (sub-pages); top-level screens omit it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolkitTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backDescription: String = "",
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = backDescription) }
            }
        },
        actions = actions,
    )
}

/** A labelled group of rows: one rounded block whose rows are separated by thin gaps. */
@Composable
fun SettingsGroup(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)),
            verticalArrangement = Arrangement.spacedBy(GroupGap),
            content = content,
        )
    }
}

@Composable
fun SettingsRow(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun SettingsNote(text: String) {
    SettingsRow { Text(text, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    description: String? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .toggleable(enabled = enabled, value = checked, role = Role.Switch) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onChange(it)
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * Slider row. [label] receives the current (snapped) value. The slider keeps its own position while
 * dragging and writes the setting only when the drag ends.
 */
@Composable
fun SliderRow(label: @Composable (Long) -> String, value: Long, min: Long, max: Long, step: Long, onCommit: (Long) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var dragging by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val snapped = (min + ((dragging.toLong() - min) / step) * step).coerceIn(min, max)
    SettingsRow {
        Text(label(snapped), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = snapped.toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onCommit(snapped) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = ((max - min) / step - 1).toInt().coerceAtLeast(0),
        )
    }
}

/** Shape for row [index] of a list that reads as one rounded block: large outer corners, small ones between rows. */
fun groupedRowShape(index: Int, lastIndex: Int, outer: Dp = 24.dp, inner: Dp = 4.dp): RoundedCornerShape =
    RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == lastIndex) outer else inner,
        bottomEnd = if (index == lastIndex) outer else inner,
    )

/** Rounded search box shared by the app-list pages. */
@Composable
fun ToolkitSearchField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(label) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear))
                }
            }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboard?.hide()
            focusManager.clearFocus()
        }),
        shape = CircleShape,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Cookie-shaped badge showing the first letter of [label], used as a stand-in app icon. */
@Composable
fun LetterAvatar(label: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val scheme = MaterialTheme.colorScheme
    val tints = listOf(
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
    )
    val (container, content) = tints[Math.floorMod(label.hashCode(), tints.size)]
    Box(
        modifier = modifier.size(size).background(container, CookieShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.firstOrNull()?.uppercase().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = content,
        )
    }
}

/** Section heading for lists that are not wrapped in a [SettingsGroup]. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** Tappable row with a cookie-shaped icon badge, for lists of destinations. */
@Composable
fun NavRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button) { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CookieShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
