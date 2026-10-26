package vn.hongducdev.honortoolkit.feature.quickshare

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Asks for consent while the app is on screen: MagicOS hides Magic Capsule for the foreground app
 * and keeps the offer channel below banner level, so a notification alone would go unseen.
 */
@Composable
fun QuickShareOfferDialog() {
    val context = LocalContext.current
    val offer = QuickShareReceiverState.status.collectAsStateWithLifecycle().value.offer ?: return
    val what = if (offer.fileCount > 0) {
        stringResource(R.string.quick_share_offer_files, offer.fileCount)
    } else {
        stringResource(R.string.quick_share_offer_text_item)
    }
    AlertDialog(
        onDismissRequest = { QuickShareReceiverService.answer(context, offer.id, accept = false) },
        title = { Text(stringResource(R.string.quick_share_offer_title, offer.senderName)) },
        text = { Text(stringResource(R.string.quick_share_offer_text, offer.pin, what)) },
        confirmButton = {
            TextButton(onClick = { QuickShareReceiverService.answer(context, offer.id, accept = true) }) {
                Text(stringResource(R.string.quick_share_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = { QuickShareReceiverService.answer(context, offer.id, accept = false) }) {
                Text(stringResource(R.string.quick_share_decline))
            }
        },
    )
}
