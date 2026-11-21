package app.drop.ui.spaces

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.floor

/** Sizes on the canvas, in dp of the "world" (zoom 1). */
object CanvasGeometry {
    const val CARD_W = 180f
    const val CARD_H = 100f
    const val GRID = 24f
    const val NUDGE = 40f
    const val WORLD_LIMIT = 20_000f

    /** Keeps a card's top-left corner inside the world. */
    fun clamp(x: Float, y: Float): Pair<Float, Float> =
        x.coerceIn(-WORLD_LIMIT, WORLD_LIMIT - CARD_W) to y.coerceIn(-WORLD_LIMIT, WORLD_LIMIT - CARD_H)

    /** Where the [n]th card placed automatically goes: four to a row from the origin. */
    fun slot(n: Int, originX: Float = 0f, originY: Float = 0f): Pair<Float, Float> =
        originX + GRID + (n % 4) * (CARD_W + GRID) to originY + GRID + (n / 4) * (CARD_H + GRID)

    fun center(x: Float, y: Float): Pair<Float, Float> = x + CARD_W / 2 to y + CARD_H / 2
}

/** A rectangle in world coordinates. */
data class WorldRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        /** The area covering every card whose top-left corners are [positions], or null without cards. */
        fun ofCards(positions: Collection<Pair<Float, Float>>): WorldRect? {
            if (positions.isEmpty()) return null
            return WorldRect(
                positions.minOf { it.first },
                positions.minOf { it.second },
                positions.maxOf { it.first } + CanvasGeometry.CARD_W,
                positions.maxOf { it.second } + CanvasGeometry.CARD_H,
            )
        }
    }
}

/** What part of the canvas is on screen: a pan in pixels and a zoom. */
data class Viewport(val panX: Float = 0f, val panY: Float = 0f, val scale: Float = 1f) {
    fun toScreenX(worldX: Float, density: Float): Float = worldX * density * scale + panX

    fun toScreenY(worldY: Float, density: Float): Float = worldY * density * scale + panY

    fun toWorldX(screenX: Float, density: Float): Float = (screenX - panX) / (scale * density)

    fun toWorldY(screenY: Float, density: Float): Float = (screenY - panY) / (scale * density)

    fun panBy(dx: Float, dy: Float): Viewport = copy(panX = panX + dx, panY = panY + dy)

    /** Zooms by [factor] keeping the point under ([focusX], [focusY]) in place. */
    fun zoomBy(factor: Float, focusX: Float, focusY: Float): Viewport {
        val next = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val applied = next / scale
        return Viewport(focusX - (focusX - panX) * applied, focusY - (focusY - panY) * applied, next)
    }

    /** The screen line between the centres of two cards: x1, y1, x2, y2. */
    fun connection(from: Pair<Float, Float>, to: Pair<Float, Float>, density: Float): FloatArray {
        val (ax, ay) = CanvasGeometry.center(from.first, from.second)
        val (bx, by) = CanvasGeometry.center(to.first, to.second)
        return floatArrayOf(toScreenX(ax, density), toScreenY(ay, density), toScreenX(bx, density), toScreenY(by, density))
    }

    /** Whether a card at ([worldX], [worldY]) is on a screen of that size, with [marginPx] to spare. */
    fun isVisible(worldX: Float, worldY: Float, widthPx: Float, heightPx: Float, density: Float, marginPx: Float = 0f): Boolean {
        val left = toScreenX(worldX, density)
        val top = toScreenY(worldY, density)
        val right = left + CanvasGeometry.CARD_W * density * scale
        val bottom = top + CanvasGeometry.CARD_H * density * scale
        return right >= -marginPx && bottom >= -marginPx && left <= widthPx + marginPx && top <= heightPx + marginPx
    }

    /** The spacing of the background dots: the grid, doubled until dots are not too close. */
    fun gridStep(density: Float): Float {
        var step = CanvasGeometry.GRID * density * scale
        while (step < MIN_DOT_SPACING_DP * density) step *= 2
        return step
    }

    fun gridStart(pan: Float, step: Float): Float = pan - floor(pan / step) * step

    companion object {
        const val MIN_SCALE = 0.4f
        const val MAX_SCALE = 2.5f
        const val MAX_FIT_SCALE = 1.25f
        const val STEP = 1.25f
        const val MIN_DOT_SPACING_DP = 14f
        val DEFAULT = Viewport()

        /** Shows all of [content] on a screen of that size, never zoomed in beyond [MAX_FIT_SCALE]. */
        fun fit(content: WorldRect, widthPx: Float, heightPx: Float, density: Float, marginPx: Float): Viewport {
            val availableW = maxOf(1f, widthPx - 2 * marginPx)
            val availableH = maxOf(1f, heightPx - 2 * marginPx)
            val scale = minOf(availableW / (content.width * density), availableH / (content.height * density)).coerceIn(MIN_SCALE, MAX_FIT_SCALE)
            val centerX = (content.left + content.width / 2) * density * scale
            val centerY = (content.top + content.height / 2) * density * scale
            return Viewport(widthPx / 2 - centerX, heightPx / 2 - centerY, scale)
        }
    }
}

/**
 * Remembers where each canvas was last looked at, as the world point at the top-left corner and
 * the zoom, so it reopens there on any screen size.
 */
class CanvasViewportStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(canvasId: Long, density: Float): Viewport? {
        val parts = prefs.getString(key(canvasId), null)?.split(',')?.mapNotNull { it.toFloatOrNull() } ?: return null
        if (parts.size != 3 || parts.any { it.isNaN() || it.isInfinite() }) return null
        val (worldX, worldY, rawScale) = parts
        val scale = rawScale.coerceIn(Viewport.MIN_SCALE, Viewport.MAX_SCALE)
        return Viewport(-worldX * density * scale, -worldY * density * scale, scale)
    }

    fun save(canvasId: Long, viewport: Viewport, density: Float) {
        val worldX = viewport.toWorldX(0f, density)
        val worldY = viewport.toWorldY(0f, density)
        prefs.edit().putString(key(canvasId), "$worldX,$worldY,${viewport.scale}").apply()
    }

    fun forget(canvasId: Long) {
        prefs.edit().remove(key(canvasId)).apply()
    }

    private fun key(canvasId: Long) = "canvas-$canvasId"

    private companion object {
        const val FILE = "canvas_viewports"
    }
}
