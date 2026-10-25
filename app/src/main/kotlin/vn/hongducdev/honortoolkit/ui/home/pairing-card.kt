package vn.hongducdev.honortoolkit.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vn.hongducdev.honortoolkit.R

private const val CODE_LENGTH = 6

@Composable
fun PairingCard(
    pairingPortFound: Boolean,
    busy: Boolean,
    onOpenDeveloperOptions: () -> Unit,
    onStartPairing: () -> Unit,
    onSubmitCode: (String) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.pairing_heading), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pairing_steps), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.pairing_reboot_note), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onOpenDeveloperOptions) {
                Text(stringResource(R.string.pairing_open_developer_options))
            }
            Button(onClick = onStartPairing) {
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
                )
                Button(onClick = { onSubmitCode(code) }, enabled = code.length == CODE_LENGTH && !busy) {
                    Text(stringResource(R.string.pairing_submit))
                }
            }
        }
    }
}
