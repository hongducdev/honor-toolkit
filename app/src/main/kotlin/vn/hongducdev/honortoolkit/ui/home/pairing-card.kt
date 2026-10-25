package vn.hongducdev.honortoolkit.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.hongducdev.honortoolkit.R

private const val CODE_LENGTH = 6

private val PairingSteps = listOf(
    R.string.pairing_step_1,
    R.string.pairing_step_2,
    R.string.pairing_step_3,
    R.string.pairing_step_4,
)

@Composable
fun PairingCard(
    pairingPortFound: Boolean,
    busy: Boolean,
    onOpenDeveloperOptions: () -> Unit,
    onStartPairing: () -> Unit,
    onSubmitCode: (String) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.pairing_heading), style = MaterialTheme.typography.titleLarge)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PairingSteps.forEachIndexed { index, step -> StepRow(number = index + 1, text = stringResource(step)) }
            }
            Text(
                stringResource(R.string.pairing_reboot_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenDeveloperOptions, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pairing_open_developer_options))
            }
            Button(onClick = onStartPairing, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pairing_start))
            }
            if (pairingPortFound) {
                Text(stringResource(R.string.pairing_code_found), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(CODE_LENGTH) },
                    label = { Text(stringResource(R.string.pairing_code_field)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 24.sp, letterSpacing = 8.sp, textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onSubmitCode(code) },
                    enabled = code.length == CODE_LENGTH && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.pairing_submit))
                }
            }
        }
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
