package app.drop.ui.attach

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.drop.R
import app.drop.data.Attachment
import app.drop.data.AttachmentKind
import app.drop.data.OcrStatus
import app.drop.ui.icons.ActionIcons
import app.drop.ui.theme.DropTheme
import java.util.Locale

internal fun AttachmentKind?.icon() = when (this) {
    AttachmentKind.Audio -> ActionIcons.Mic
    AttachmentKind.Pdf -> ActionIcons.Pdf
    else -> ActionIcons.Image
}

@Composable
internal fun AttachmentKind?.label(): String = stringResource(
    when (this) {
        AttachmentKind.Audio -> R.string.attachment_kind_audio
        AttachmentKind.Pdf -> R.string.attachment_kind_pdf
        else -> R.string.attachment_kind_image
    },
)

/** "1:05", or "1:02:05" past an hour. */
fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/** The note's attachments, under the text in the editor. */
@Composable
fun AttachmentList(attachments: List<Attachment>, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    if (attachments.isEmpty()) return
    Column(modifier.fillMaxWidth().testTag("attachments"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        attachments.forEach { AttachmentRow(it, onOpen) }
    }
}

@Composable
private fun AttachmentRow(attachment: Attachment, onOpen: (Long) -> Unit) {
    val c = DropTheme.colors
    val kind = attachment.attachmentKind
    val detail = when {
        kind == AttachmentKind.Audio -> attachment.durationMs?.let(::formatDuration)
        attachment.status == OcrStatus.Pending || attachment.status == OcrStatus.Running -> stringResource(R.string.ocr_running)
        attachment.status == OcrStatus.Failed -> stringResource(R.string.ocr_failed)
        attachment.status == OcrStatus.Done && attachment.ocrText.isNullOrBlank() -> stringResource(R.string.ocr_empty)
        !attachment.ocrText.isNullOrBlank() -> attachment.ocrText.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        else -> null
    }
    val pages = attachment.pageCount?.takeIf { kind == AttachmentKind.Pdf }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(attachment.id) }.testTag("attachment-${attachment.id}"),
        shape = RoundedCornerShape(12.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.border),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(kind.icon(), contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val title = kind.label() + (pages?.let { " · " + pluralStringResource(R.plurals.paper_pages, it, it) } ?: "")
                Text(title, color = c.textPrimary, style = MaterialTheme.typography.bodyMedium)
                if (detail != null) {
                    Text(detail, color = c.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
