package vn.hongducdev.honortoolkit.feature.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> OptionChips(selected: T, options: List<Pair<T, Int>>, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
internal fun NumberField(label: String, value: Int, onValue: (Int) -> Unit, maxDigits: Int) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val filtered = input.filter(Char::isDigit).take(maxDigits)
            text = filtered
            filtered.toIntOrNull()?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    numeric: Boolean = false,
    decimal: Boolean = false,
    maxLength: Int = 0,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            val allowed = when {
                decimal -> text.filter { it.isDigit() || it == '.' || it == '-' }
                numeric -> text.filter(Char::isDigit)
                else -> text
            }.let { if (maxLength > 0) it.take(maxLength) else it }
            onChange(allowed)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric || decimal) KeyboardType.Number else KeyboardType.Text,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private val dayLabels = listOf(
    1 to R.string.automation_day_1,
    2 to R.string.automation_day_2,
    3 to R.string.automation_day_3,
    4 to R.string.automation_day_4,
    5 to R.string.automation_day_5,
    6 to R.string.automation_day_6,
    7 to R.string.automation_day_7,
)

/** Multi-select of ISO weekdays; an empty selection means every day. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayChips(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Text(stringResource(R.string.automation_editor_days), style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        dayLabels.forEach { (day, label) ->
            FilterChip(
                selected = day in days,
                onClick = { onChange(if (day in days) days - day else days + day) },
                label = { Text(stringResource(label)) },
            )
        }
    }
    Text(
        stringResource(R.string.automation_editor_every_day),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Hour and minute as two number fields for a time stored as minutes since midnight. */
@Composable
internal fun MinuteOfDayFields(minute: Int, hourLabel: Int, minuteLabel: Int, onChange: (Int) -> Unit) {
    NumberField(
        label = stringResource(hourLabel),
        value = minute / 60,
        onValue = { onChange(it.coerceIn(0, 23) * 60 + minute % 60) },
        maxDigits = 2,
    )
    NumberField(
        label = stringResource(minuteLabel),
        value = minute % 60,
        onValue = { onChange(minute / 60 * 60 + it.coerceIn(0, 59)) },
        maxDigits = 2,
    )
}
