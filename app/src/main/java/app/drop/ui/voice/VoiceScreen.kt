package app.drop.ui.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.drop.DropApplication
import app.drop.R
import app.drop.ui.attach.AudioPlayer
import app.drop.ui.attach.formatDuration
import app.drop.ui.icons.ActionIcons
import app.drop.ui.spaces.spacesFieldColors
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.accentTextButtonColors
import app.drop.ui.theme.destructiveTextButtonColors
import app.drop.voice.OnDeviceSupport
import app.drop.voice.SpeechCapabilities
import app.drop.voice.SpeechPath
import app.drop.voice.SpeechSupport
import app.drop.voice.VoiceMessage
import app.drop.voice.VoiceMode
import app.drop.voice.VoiceState
import app.drop.voice.VoiceViewModel
import app.drop.voice.transcriptionPath
import kotlinx.coroutines.delay

private fun Context.findActivity(): Activity? {
    var c = this
    while (c !is Activity) c = (c as? ContextWrapper)?.baseContext ?: return null
    return c
}

/**
 * Voice Dump. Speak to write a note, or record a voice note. [autoStart] starts at once, as from
 * the launcher shortcut. [onSaved] gets the new note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    autoStart: Boolean,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: VoiceViewModel = viewModel(factory = VoiceViewModel.Factory),
) {
    val c = DropTheme.colors
    val context = LocalContext.current
    val settings = (context.applicationContext as DropApplication).settings
    val language by settings.speechLanguage.collectAsStateWithLifecycle()
    val allowSystem by settings.allowSystemSpeech.collectAsStateWithLifecycle()
    var chosenMode by rememberSaveable { mutableStateOf<VoiceMode?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var askAllowSystem by rememberSaveable { mutableStateOf(false) }
    var downloadNote by rememberSaveable { mutableStateOf<Int?>(null) }
    var support by remember { mutableStateOf<SpeechSupport?>(null) }
    // Checked again when DROP comes back, for example from the speech settings or a download.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val capabilities = remember { SpeechCapabilities(context) }
    LaunchedEffect(language, resumes) {
        support = capabilities.check(language)
    }
    val state = viewModel.state
    val path = support?.let { transcriptionPath(it, allowSystem) }
    // Until chosen, transcription when it can run here, otherwise recording.
    val mode = chosenMode ?: if (support == null || path != null) VoiceMode.Transcribe else VoiceMode.Record

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.start(mode, language, path) else viewModel.onPermissionDenied()
    }
    val start = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.start(mode, language, path)
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoStart, support) {
        if (autoStart && !autoStarted && support != null && state == VoiceState.Idle) {
            autoStarted = true
            start()
        }
    }
    (state as? VoiceState.Saved)?.let { saved -> LaunchedEffect(saved) { onSaved(saved.noteId) } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (context.findActivity()?.isChangingConfigurations != true) viewModel.onBackground()
    }
    val active = state is VoiceState.Listening || state is VoiceState.Recording
    val reviewing = state is VoiceState.ReviewText || state is VoiceState.ReviewAudio
    val leave = { if (active || reviewing) confirmDiscard = true else onBack() }
    BackHandler(onBack = leave)

    Scaffold(
        modifier = Modifier.testTag("voice"),
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.voice_title)) },
                navigationIcon = {
                    IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background, titleContentColor = c.textPrimary, navigationIconContentColor = c.textPrimary),
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when (state) {
                    VoiceState.Idle -> Setup(
                        mode = mode,
                        onMode = {
                            chosenMode = it
                            viewModel.clearMessage()
                        },
                        language = language,
                        onLanguage = settings::setSpeechLanguage,
                        support = support,
                        path = path,
                        allowSystem = allowSystem,
                        onAllowSystem = { askAllowSystem = true },
                        onDownload = {
                            downloadNote = if (capabilities.requestDownload(language)) R.string.voice_download_started else R.string.voice_download_failed
                        },
                        downloadNote = downloadNote,
                        onStart = start,
                    )
                    is VoiceState.Listening -> Live(
                        title = stringResource(R.string.voice_listening),
                        startedAt = state.startedAt,
                        level = viewModel.level,
                        text = state.text,
                        partial = state.partial,
                        onStop = viewModel::stop,
                    )
                    is VoiceState.Recording -> Live(
                        title = stringResource(R.string.voice_recording),
                        startedAt = state.startedAt,
                        level = viewModel.level,
                        text = "",
                        partial = "",
                        onStop = viewModel::stop,
                    )
                    is VoiceState.ReviewText -> Review(onSave = { viewModel.save(context.getString(R.string.voice_note_title)) }, onDiscard = { confirmDiscard = true }, saveLabel = R.string.voice_save_note) {
                        OutlinedTextField(
                            value = state.text,
                            onValueChange = viewModel::onReviewTextChange,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("voice-text"),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = spacesFieldColors(),
                        )
                    }
                    is VoiceState.ReviewAudio -> Review(onSave = { viewModel.save(context.getString(R.string.voice_note_title)) }, onDiscard = { confirmDiscard = true }, saveLabel = R.string.voice_save_recording) {
                        val file = (context.applicationContext as DropApplication).attachments.store.file(state.partName)
                        AudioPlayer(file, state.durationMs)
                    }
                    VoiceState.Saving, is VoiceState.Saved -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 32.dp), color = c.accentContent, trackColor = c.border)
                }
                viewModel.message?.let { Message(it, onDismiss = viewModel::clearMessage, onRecordInstead = { chosenMode = VoiceMode.Record; viewModel.clearMessage() }) }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    viewModel.cancel()
                    viewModel.discard()
                    onBack()
                }, colors = destructiveTextButtonColors(), modifier = Modifier.testTag("voice-discard-confirm")) { Text(stringResource(R.string.voice_discard)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }, colors = accentTextButtonColors()) { Text(stringResource(R.string.voice_keep)) } },
            title = { Text(stringResource(R.string.voice_discard_title)) },
            text = { Text(stringResource(R.string.voice_discard_body)) },
            containerColor = c.surfaceElevated,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
        )
    }
    if (askAllowSystem) {
        AlertDialog(
            onDismissRequest = { askAllowSystem = false },
            confirmButton = {
                TextButton(onClick = {
                    askAllowSystem = false
                    settings.setAllowSystemSpeech(true)
                }, colors = accentTextButtonColors(), modifier = Modifier.testTag("voice-allow-confirm")) { Text(stringResource(R.string.voice_allow_system_confirm)) }
            },
            dismissButton = { TextButton(onClick = { askAllowSystem = false }, colors = accentTextButtonColors()) { Text(stringResource(R.string.dialog_cancel)) } },
            title = { Text(stringResource(R.string.voice_allow_system_title)) },
            text = { Text(stringResource(R.string.settings_voice_system_body)) },
            containerColor = c.surfaceElevated,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Setup(
    mode: VoiceMode,
    onMode: (VoiceMode) -> Unit,
    language: String,
    onLanguage: (String) -> Unit,
    support: SpeechSupport?,
    path: SpeechPath?,
    allowSystem: Boolean,
    onAllowSystem: () -> Unit,
    onDownload: () -> Unit,
    downloadNote: Int?,
    onStart: () -> Unit,
) {
    val c = DropTheme.colors
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        VoiceMode.entries.forEachIndexed { i, m ->
            SegmentedButton(
                selected = m == mode,
                onClick = { onMode(m) },
                shape = SegmentedButtonDefaults.itemShape(i, VoiceMode.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = c.accentSubtle,
                    activeContentColor = c.textPrimary,
                    inactiveContainerColor = c.background,
                    inactiveContentColor = c.textSecondary,
                ),
                modifier = Modifier.testTag("voice-mode-${m.name}"),
            ) { Text(stringResource(if (m == VoiceMode.Transcribe) R.string.voice_mode_transcribe else R.string.voice_mode_record)) }
        }
    }
    Spacer(Modifier.height(16.dp))
    if (mode == VoiceMode.Transcribe) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("en-US" to R.string.voice_language_en, "tr-TR" to R.string.voice_language_tr).forEach { (tag, label) ->
                FilterChip(
                    selected = language == tag,
                    onClick = { onLanguage(tag) },
                    label = { Text(stringResource(label)) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = c.background,
                        labelColor = c.textSecondary,
                        selectedContainerColor = c.accentSubtle,
                        selectedLabelColor = c.textPrimary,
                    ),
                    modifier = Modifier.testTag("voice-language-$tag"),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    val info = when {
        mode == VoiceMode.Record -> stringResource(R.string.voice_record_hint)
        support == null -> stringResource(R.string.voice_support_checking)
        path == SpeechPath.System -> stringResource(R.string.voice_support_system)
        support.onDevice == OnDeviceSupport.Ready -> stringResource(R.string.voice_support_ready)
        support.onDevice == OnDeviceSupport.LanguageUnknown -> stringResource(R.string.voice_support_unknown)
        support.onDevice == OnDeviceSupport.LanguageNotInstalled -> stringResource(R.string.voice_support_not_installed)
        else -> stringResource(R.string.voice_support_none)
    }
    InfoCard(info, warning = mode == VoiceMode.Transcribe && support != null && (path == null || path == SpeechPath.System))
    // Transcription can't run here: say so plainly and offer every honest way forward.
    if (mode == VoiceMode.Transcribe && support != null && path == null) {
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().testTag("voice-unavailable"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onMode(VoiceMode.Record) }, modifier = Modifier.fillMaxWidth().testTag("voice-record-instead")) {
                Icon(ActionIcons.Mic, contentDescription = null, tint = c.textPrimary)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.voice_record_instead), color = c.textPrimary)
            }
            if (support.downloadable) {
                OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth().testTag("voice-download")) {
                    Text(stringResource(R.string.voice_download), color = c.textPrimary)
                }
            }
            if (support.systemAvailable && !allowSystem) {
                OutlinedButton(onClick = onAllowSystem, modifier = Modifier.fillMaxWidth().testTag("voice-allow-system")) {
                    Text(stringResource(R.string.voice_allow_system), color = c.textPrimary)
                }
            }
            downloadNote?.let { Text(stringResource(it), color = c.textSecondary, style = MaterialTheme.typography.bodySmall) }
        }
    } else if (mode == VoiceMode.Transcribe) {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.voice_transcribe_hint),
            modifier = Modifier.fillMaxWidth(),
            color = c.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Spacer(Modifier.height(28.dp))
    val canStart = mode == VoiceMode.Record || path != null
    Button(
        onClick = onStart,
        enabled = canStart,
        shape = CircleShape,
        modifier = Modifier.size(96.dp).testTag("voice-start"),
        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent, disabledContainerColor = c.surface, disabledContentColor = c.disabled),
    ) { Icon(ActionIcons.Mic, contentDescription = stringResource(R.string.voice_start), modifier = Modifier.size(36.dp)) }
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.voice_start), color = if (canStart) c.textPrimary else c.disabled, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun Live(title: String, startedAt: Long, level: Float, text: String, partial: String, onStop: () -> Unit) {
    val c = DropTheme.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val elapsed = formatDuration(now - startedAt)
    Spacer(Modifier.height(16.dp))
    Text(title, color = c.textPrimary, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    Spacer(Modifier.height(4.dp))
    val elapsedLabel = stringResource(R.string.voice_elapsed, elapsed)
    Text(elapsed, color = c.textSecondary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = elapsedLabel }.testTag("voice-elapsed"))
    Spacer(Modifier.height(16.dp))
    val levelLabel = stringResource(R.string.voice_level)
    LinearProgressIndicator(
        progress = { level.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(6.dp).semantics { contentDescription = levelLabel },
        color = c.accentContent,
        trackColor = c.border,
    )
    if (text.isNotEmpty() || partial.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(
            buildString {
                append(text)
                if (partial.isNotEmpty()) append(if (text.isEmpty()) partial else " $partial")
            },
            modifier = Modifier.fillMaxWidth().testTag("voice-live-text"),
            color = c.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    Spacer(Modifier.height(28.dp))
    Button(
        onClick = onStop,
        shape = CircleShape,
        modifier = Modifier.size(96.dp).testTag("voice-stop"),
        colors = ButtonDefaults.buttonColors(containerColor = c.error, contentColor = c.onAccent),
    ) { Icon(ActionIcons.Stop, contentDescription = stringResource(R.string.voice_stop), modifier = Modifier.size(36.dp)) }
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.voice_stop), color = c.textPrimary, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun Review(onSave: () -> Unit, onDiscard: () -> Unit, saveLabel: Int, content: @Composable () -> Unit) {
    val c = DropTheme.colors
    Text(stringResource(R.string.voice_review), color = c.textPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    content()
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onDiscard, colors = destructiveTextButtonColors(), modifier = Modifier.testTag("voice-discard")) { Text(stringResource(R.string.voice_discard)) }
        Spacer(Modifier.size(8.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.heightIn(min = 48.dp).testTag("voice-save"),
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
        ) { Text(stringResource(saveLabel)) }
    }
}

@Composable
private fun InfoCard(text: String, warning: Boolean) {
    val c = DropTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("voice-info"),
        shape = RoundedCornerShape(12.dp),
        color = if (warning) c.surface else c.accentSubtle,
        border = BorderStroke(1.dp, if (warning) c.warning else c.border),
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = c.textPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Message(message: VoiceMessage, onDismiss: () -> Unit, onRecordInstead: () -> Unit) {
    val c = DropTheme.colors
    val context = LocalContext.current
    val text = stringResource(
        when (message) {
            VoiceMessage.NothingHeard -> R.string.voice_msg_nothing_heard
            VoiceMessage.LanguageUnavailable -> R.string.voice_msg_language
            VoiceMessage.MicUnavailable -> R.string.voice_msg_mic
            VoiceMessage.PermissionDenied -> R.string.voice_msg_permission
            VoiceMessage.StorageFull -> R.string.voice_msg_storage
            VoiceMessage.TooShort -> R.string.voice_msg_too_short
            VoiceMessage.RecordingFailed -> R.string.voice_msg_failed
            VoiceMessage.MaxLengthReached -> R.string.voice_msg_max_length
            VoiceMessage.ConnectionLost -> R.string.voice_msg_connection
            VoiceMessage.SaveFailed -> R.string.voice_msg_save_failed
        },
    )
    Spacer(Modifier.height(16.dp))
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("voice-message"),
        shape = RoundedCornerShape(12.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.border),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text, color = c.textPrimary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Row(Modifier.align(Alignment.End)) {
                if (message == VoiceMessage.LanguageUnavailable) {
                    TextButton(onClick = onRecordInstead, colors = accentTextButtonColors()) { Text(stringResource(R.string.voice_record_instead)) }
                }
                if (message == VoiceMessage.PermissionDenied) {
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        }
                    }, colors = accentTextButtonColors()) { Text(stringResource(R.string.voice_open_settings)) }
                }
                TextButton(onClick = onDismiss, colors = accentTextButtonColors()) { Text(stringResource(android.R.string.ok)) }
            }
        }
    }
}
