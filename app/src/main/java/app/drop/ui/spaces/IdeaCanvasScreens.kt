package app.drop.ui.spaces

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.drop.R
import app.drop.data.CardColor
import app.drop.data.CardShape
import app.drop.data.CardWithNote
import app.drop.data.Links
import app.drop.data.Note
import app.drop.richtext.DropMarkdown
import app.drop.ui.icons.ActionIcons
import app.drop.ui.icons.FormatIcons
import app.drop.ui.notes.cardTitle
import app.drop.ui.theme.DropTheme
import kotlin.math.roundToInt

@Composable
fun CanvasListScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, viewModel: CanvasListViewModel = viewModel(factory = CanvasListViewModel.Factory)) {
    val c = DropTheme.colors
    val canvases by viewModel.canvases.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.testTag("canvases"),
        topBar = { SpacesTopBar(stringResource(R.string.canvas_list_title), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.canvas_new)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { creating = true },
                containerColor = c.accent,
                contentColor = c.onAccent,
            )
        },
        containerColor = c.background,
    ) { padding ->
        val list = canvases ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyMessage(stringResource(R.string.canvas_empty_title), stringResource(R.string.canvas_empty_body), Modifier.padding(padding))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = padding.calculateTopPadding() + 4.dp, end = 16.dp, bottom = padding.calculateBottomPadding() + 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.id }) { canvas ->
                    Surface(
                        onClick = { onOpen(canvas.id) },
                        modifier = Modifier.fillMaxWidth().testTag("canvas-${canvas.id}"),
                        shape = RoundedCornerShape(16.dp),
                        color = c.surface,
                        border = BorderStroke(1.dp, c.border),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(canvas.name, color = c.textPrimary, style = MaterialTheme.typography.titleMedium)
                            Text(
                                pluralStringResource(R.plurals.canvas_cards, canvas.cardCount, canvas.cardCount),
                                color = c.textSecondary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        NameDialog(stringResource(R.string.canvas_new), "", onConfirm = { name, _ ->
            creating = false
            viewModel.create(name, onOpen)
        }, onDismiss = { creating = false })
    }
}

/**
 * An Idea Canvas: drag cards to move them, drag empty space to look around, pinch or use the
 * buttons to zoom. Where the canvas was last looked at is remembered.
 */
@Composable
fun CanvasScreen(onBack: () -> Unit, onOpenNote: (Long) -> Unit, viewModel: CanvasViewModel = viewModel(factory = CanvasViewModel.Factory)) {
    val c = DropTheme.colors
    val canvas by viewModel.canvas.collectAsStateWithLifecycle()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val edges by viewModel.edges.collectAsStateWithLifecycle()
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val store = remember { CanvasViewportStore(context) }
    var menu by remember { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var connectFrom by rememberSaveable { mutableStateOf<Long?>(null) }
    var styling by rememberSaveable { mutableStateOf<Long?>(null) }
    var writing by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var viewport by remember { mutableStateOf(store.load(viewModel.id, density) ?: Viewport.DEFAULT) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val dragging = remember { mutableStateMapOf<Long, Offset>() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { store.save(viewModel.id, viewport, density) }
    DisposableEffect(Unit) { onDispose { store.save(viewModel.id, viewport, density) } }

    val list = cards
    val positions: Map<Long, Offset> = list.orEmpty().associate { it.id to (dragging[it.id] ?: Offset(it.x, it.y)) }
    val centerX = viewSize.width / 2f
    val centerY = viewSize.height / 2f
    val fitAll: () -> Unit = {
        WorldRect.ofCards(positions.values.map { it.x to it.y })?.let { bounds ->
            viewport = Viewport.fit(bounds, viewSize.width.toFloat(), viewSize.height.toFloat(), density, 32 * density)
        }
    }

    Scaffold(
        modifier = Modifier.testTag("canvas"),
        topBar = {
            SpacesTopBar(canvas?.name.orEmpty(), onBack) {
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("canvas-menu")) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.note_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.surfaceElevated) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.spaces_rename), color = c.textPrimary) }, onClick = {
                            menu = false
                            renaming = true
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.canvas_delete), color = c.error) }, onClick = {
                            menu = false
                            deleting = true
                        })
                    }
                }
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(
                    onClick = { writing = true },
                    containerColor = c.surfaceElevated,
                    contentColor = c.textPrimary,
                    modifier = Modifier.testTag("canvas-text-block"),
                ) { Icon(FormatIcons.Title, contentDescription = stringResource(R.string.canvas_text_block)) }
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.canvas_add_notes)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { adding = true },
                    containerColor = c.accent,
                    contentColor = c.onAccent,
                    modifier = Modifier.testTag("canvas-add"),
                )
            }
        },
        containerColor = c.background,
    ) { padding ->
        if (list == null) return@Scaffold
        Box(Modifier.fillMaxSize().padding(padding)) {
            val dot = c.border
            val line = c.textSecondary
            val accentLine = c.accentContent
            Box(
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged { viewSize = it }
                    .testTag("canvas-workspace")
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            viewport = viewport.zoomBy(zoom, centroid.x, centroid.y).panBy(pan.x, pan.y)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            selected = null
                            connectFrom = null
                        })
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawDotGrid(viewport, density, dot)
                    for (e in edges) {
                        val a = positions[e.fromCard] ?: continue
                        val b = positions[e.toCard] ?: continue
                        val (x1, y1, x2, y2) = viewport.connection(a.x to a.y, b.x to b.y, density)
                        val emphasised = selected == e.fromCard || selected == e.toCard
                        drawLine(
                            if (emphasised) accentLine else line,
                            Offset(x1, y1),
                            Offset(x2, y2),
                            strokeWidth = viewport.scale.coerceAtLeast(0.6f) * (if (emphasised) 2.5f else 1.5f) * density,
                        )
                    }
                }
                val margin = 48 * density
                for (card in list) {
                    val pos = positions.getValue(card.id)
                    if (!viewport.isVisible(pos.x, pos.y, viewSize.width.toFloat(), viewSize.height.toFloat(), density, margin)) continue
                    val from = connectFrom
                    BoardCard(
                        card = card,
                        screenX = viewport.toScreenX(pos.x, density),
                        screenY = viewport.toScreenY(pos.y, density),
                        scale = viewport.scale,
                        selected = selected == card.id,
                        connectSource = from == card.id,
                        onTap = {
                            when {
                                from != null && from != card.id -> {
                                    viewModel.toggleConnection(from, card.id)
                                    true
                                }
                                from == card.id -> {
                                    connectFrom = null
                                    true
                                }
                                else -> {
                                    selected = card.id
                                    false
                                }
                            }
                        },
                        onDragStart = { selected = card.id },
                        onDrag = { dx, dy ->
                            val current = dragging[card.id] ?: Offset(card.x, card.y)
                            val (cx, cy) = CanvasGeometry.clamp(current.x + dx, current.y + dy)
                            dragging[card.id] = Offset(cx, cy)
                        },
                        onDragEnd = { dragging.remove(card.id)?.let { viewModel.move(card.id, it.x, it.y) } },
                        onNudge = { dx, dy -> viewModel.move(card.id, card.x + dx, card.y + dy) },
                        onOpen = { onOpenNote(card.noteId) },
                        onConnect = { connectFrom = card.id },
                        onDisconnect = { viewModel.disconnect(card.id) },
                        onRemove = { viewModel.remove(card.id) },
                        linkedToSource = from != null && edges.any {
                            (it.fromCard == from && it.toCard == card.id) || (it.toCard == from && it.fromCard == card.id)
                        },
                        onStyle = { styling = card.id },
                    )
                }
            }
            if (list.isEmpty()) EmptyMessage(stringResource(R.string.canvas_title), stringResource(R.string.canvas_board_empty))
            connectFrom?.let { from ->
                val count = edges.count { it.fromCard == from || it.toCard == from }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(12.dp).testTag("canvas-connect-bar"),
                    shape = RoundedCornerShape(12.dp),
                    color = c.accentSubtle,
                ) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.canvas_connect_many_hint) + " · " + pluralStringResource(R.plurals.canvas_connections, count, count),
                            modifier = Modifier.weight(1f),
                            color = c.textPrimary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { connectFrom = null }) { Text(stringResource(R.string.canvas_connect_done), color = c.accentContent) }
                    }
                }
            }
            ViewportControls(
                scale = viewport.scale,
                onZoomIn = { viewport = viewport.zoomBy(Viewport.STEP, centerX, centerY) },
                onZoomOut = { viewport = viewport.zoomBy(0.8f, centerX, centerY) },
                onFit = if (list.isEmpty()) null else fitAll,
                onReset = { viewport = Viewport.DEFAULT },
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            )
        }
    }

    styling?.let { id ->
        val card = list.orEmpty().firstOrNull { it.id == id }
        if (card == null) {
            styling = null
        } else {
            CardStyleDialog(CardShape.of(card.shape), CardColor.of(card.color), onPick = { shape, color -> viewModel.style(id, shape, color) }, onDismiss = { styling = null })
        }
    }
    if (writing) {
        NameDialog(stringResource(R.string.canvas_text_block), "", onConfirm = { text, _ ->
            writing = false
            viewModel.addTextBlock(
                text,
                viewport.toWorldX(centerX, density) - CanvasGeometry.CARD_W / 2,
                viewport.toWorldY(centerY, density) - CanvasGeometry.CARD_H / 2,
            )
        }, onDismiss = { writing = false })
    }
    if (adding) {
        NotePicker(
            stringResource(R.string.canvas_add_notes),
            allNotes,
            multiple = true,
            onPick = { ids ->
                adding = false
                viewModel.add(ids, viewport.toWorldX(0f, density), viewport.toWorldY(0f, density))
            },
            onDismiss = { adding = false },
            exclude = list.orEmpty().mapTo(HashSet()) { it.noteId },
        )
    }
    if (renaming) {
        NameDialog(stringResource(R.string.spaces_rename), canvas?.name.orEmpty(), onConfirm = { name, _ ->
            renaming = false
            viewModel.rename(name)
        }, onDismiss = { renaming = false })
    }
    if (deleting) {
        Confirm(
            stringResource(R.string.canvas_delete_title),
            stringResource(R.string.canvas_delete_body),
            stringResource(R.string.canvas_delete),
            onConfirm = {
                deleting = false
                store.forget(viewModel.id)
                viewModel.delete(onBack)
            },
            onDismiss = { deleting = false },
            destructive = true,
        )
    }
}

private fun DrawScope.drawDotGrid(viewport: Viewport, density: Float, color: Color) {
    val step = viewport.gridStep(density)
    val startX = viewport.gridStart(viewport.panX, step)
    val startY = viewport.gridStart(viewport.panY, step)
    val points = ArrayList<Offset>(((size.width / step + 1) * (size.height / step + 1)).toInt().coerceAtLeast(0))
    var x = startX
    while (x <= size.width) {
        var y = startY
        while (y <= size.height) {
            points.add(Offset(x, y))
            y += step
        }
        x += step
    }
    drawPoints(points, PointMode.Points, color, strokeWidth = density * 2f, cap = StrokeCap.Round)
}

@Composable
private fun ViewportControls(
    scale: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: (() -> Unit)?,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DropTheme.colors
    val percent = (100 * scale).roundToInt()
    val zoomLabel = stringResource(R.string.canvas_zoom_level, percent)
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = c.surfaceElevated, border = BorderStroke(1.dp, c.border)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onZoomOut, enabled = scale > Viewport.MIN_SCALE + 0.001f) {
                Icon(ActionIcons.Minus, contentDescription = stringResource(R.string.canvas_zoom_out), tint = c.textPrimary)
            }
            Text(
                "$percent%",
                modifier = Modifier.widthIn(min = 40.dp).testTag("canvas-zoom-level").semantics { contentDescription = zoomLabel },
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
            )
            IconButton(onClick = onZoomIn, enabled = scale < Viewport.MAX_SCALE - 0.001f, modifier = Modifier.testTag("canvas-zoom-in")) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.canvas_zoom_in), tint = c.textPrimary)
            }
            if (onFit != null) {
                IconButton(onClick = onFit) { Icon(ActionIcons.FitScreen, contentDescription = stringResource(R.string.canvas_fit), tint = c.textPrimary) }
            }
            IconButton(onClick = onReset) { Icon(ActionIcons.Recenter, contentDescription = stringResource(R.string.canvas_reset), tint = c.textPrimary) }
        }
    }
}

/** The background a card colour gives; null keeps the surface colour. */
@Composable
private fun cardTint(color: CardColor): Color? {
    val c = DropTheme.colors
    return when (color) {
        CardColor.Default -> null
        CardColor.Lime -> c.accentSubtle
        CardColor.Blue -> c.info.copy(alpha = 0.18f).compositeOver(c.surface)
        CardColor.Amber -> c.warning.copy(alpha = 0.2f).compositeOver(c.surface)
        CardColor.Rose -> c.error.copy(alpha = 0.16f).compositeOver(c.surface)
    }
}

/**
 * A note on the canvas, drawn at its world size and scaled with the zoom. Tapping selects it and
 * opens its menu (or connects it while connecting); dragging moves it.
 */
@Composable
private fun BoardCard(
    card: CardWithNote,
    screenX: Float,
    screenY: Float,
    scale: Float,
    selected: Boolean,
    connectSource: Boolean,
    onTap: () -> Boolean,
    onDragStart: () -> Unit,
    onDrag: (dxDp: Float, dyDp: Float) -> Unit,
    onDragEnd: () -> Unit,
    onNudge: (dxDp: Float, dyDp: Float) -> Unit,
    onOpen: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRemove: () -> Unit,
    linkedToSource: Boolean = false,
    onStyle: () -> Unit,
) {
    val c = DropTheme.colors
    val shape = CardShape.of(card.shape)
    val tint = cardTint(CardColor.of(card.color))
    var menu by remember { mutableStateOf(false) }
    val title = cardTitle(Note(title = card.title, body = card.body, format = card.format, createdAt = 0, updatedAt = 0)).ifBlank { stringResource(R.string.note_empty) }
    val actionsLabel = stringResource(R.string.canvas_card_actions)
    val labels = listOf(
        stringResource(R.string.canvas_move_left) to (-CanvasGeometry.NUDGE to 0f),
        stringResource(R.string.canvas_move_right) to (CanvasGeometry.NUDGE to 0f),
        stringResource(R.string.canvas_move_up) to (0f to -CanvasGeometry.NUDGE),
        stringResource(R.string.canvas_move_down) to (0f to CanvasGeometry.NUDGE),
    )
    val dragStart by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val currentScale by rememberUpdatedState(scale)
    val emphasised = selected || connectSource
    val cornerShape = when (shape) {
        CardShape.Card, CardShape.Frame -> RoundedCornerShape(14.dp)
        CardShape.Sticky -> RoundedCornerShape(2.dp)
        CardShape.Pill -> RoundedCornerShape(50)
    }
    val background = when {
        emphasised -> c.accentSubtle
        shape == CardShape.Frame -> c.background
        else -> tint ?: c.surface
    }
    val borderColor = when {
        emphasised || linkedToSource -> c.accentContent
        shape == CardShape.Frame -> c.textSecondary
        else -> c.border
    }
    Box(
        Modifier
            .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
            .size((CanvasGeometry.CARD_W * scale).dp, (CanvasGeometry.CARD_H * scale).dp)
            .testTag("canvas-card-${card.noteId}")
            .pointerInput(card.id) {
                detectDragGestures(
                    onDragStart = { dragStart() },
                    onDragEnd = { dragEnd() },
                    onDragCancel = { dragEnd() },
                ) { change, amount ->
                    change.consume()
                    drag(amount.x / (density * currentScale), amount.y / (density * currentScale))
                }
            },
    ) {
        Surface(
            onClick = { if (!onTap()) menu = true },
            modifier = Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(CanvasGeometry.CARD_W.dp, CanvasGeometry.CARD_H.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .semantics {
                    onClick(label = actionsLabel, action = null)
                    customActions = labels.map { (label, d) -> CustomAccessibilityAction(label) { onNudge(d.first, d.second); true } }
                },
            shape = cornerShape,
            color = background,
            border = BorderStroke(if (emphasised) 2.dp else 1.dp, borderColor),
        ) {
            Column(Modifier.padding(horizontal = if (shape == CardShape.Pill) 20.dp else 12.dp, vertical = 12.dp)) {
                val done = card.isTask && card.completedAt != null
                val prefix = if (card.isTask) (if (done) "☑ " else "☐ ") else ""
                Text(
                    prefix + title,
                    color = if (done) c.textSecondary else c.textPrimary,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                card.url?.let { Links.host(it) }?.let { host ->
                    Text("↗ $host", color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                }
                val preview = (DropMarkdown.previewOf(card.body, card.format).takeIf { card.title.isNotBlank() } ?: "")
                    .lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                if (preview.isNotEmpty()) {
                    Text(preview, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.surfaceElevated) {
            listOf(
                R.string.canvas_card_open to onOpen,
                R.string.canvas_card_connect to onConnect,
                R.string.canvas_card_style to onStyle,
                R.string.canvas_card_disconnect to onDisconnect,
                R.string.canvas_card_remove to onRemove,
            ).forEach { (label, action) ->
                DropdownMenuItem(text = { Text(stringResource(label), color = c.textPrimary) }, onClick = {
                    menu = false
                    action()
                })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardStyleDialog(shape: CardShape, color: CardColor, onPick: (CardShape, CardColor) -> Unit, onDismiss: () -> Unit) {
    val c = DropTheme.colors
    var s by remember { mutableStateOf(shape) }
    var k by remember { mutableStateOf(color) }
    val chipColors = FilterChipDefaults.filterChipColors(labelColor = c.textPrimary, selectedContainerColor = c.accentSubtle, selectedLabelColor = c.textPrimary)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close), color = c.accentContent) } },
        title = { Text(stringResource(R.string.canvas_card_style), color = c.textPrimary) },
        text = {
            Column {
                Text(stringResource(R.string.canvas_shape), color = c.textSecondary, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardShape.entries.forEach { option ->
                        FilterChip(selected = s == option, onClick = {
                            s = option
                            onPick(s, k)
                        }, label = { Text(stringResource(shapeLabel(option))) }, colors = chipColors)
                    }
                }
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.canvas_color), color = c.textSecondary, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardColor.entries.forEach { option ->
                        FilterChip(selected = k == option, onClick = {
                            k = option
                            onPick(s, k)
                        }, label = { Text(stringResource(colorLabel(option))) }, colors = chipColors)
                    }
                }
            }
        },
        containerColor = c.surfaceElevated,
    )
}

fun shapeLabel(s: CardShape): Int = when (s) {
    CardShape.Card -> R.string.canvas_shape_card
    CardShape.Frame -> R.string.canvas_shape_frame
    CardShape.Sticky -> R.string.canvas_shape_sticky
    CardShape.Pill -> R.string.canvas_shape_pill
}

fun colorLabel(c: CardColor): Int = when (c) {
    CardColor.Default -> R.string.canvas_color_default
    CardColor.Lime -> R.string.canvas_color_lime
    CardColor.Blue -> R.string.canvas_color_blue
    CardColor.Amber -> R.string.canvas_color_amber
    CardColor.Rose -> R.string.canvas_color_rose
}
