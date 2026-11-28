package app.drop.ocr

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A grey picture, one byte per pixel from 0 (black) to 255 (white), row after row. */
class GrayImage(val width: Int, val height: Int, val pixels: ByteArray = ByteArray(width * height)) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height)
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x].toInt() and 0xFF

    operator fun set(x: Int, y: Int, value: Int) {
        pixels[y * width + x] = value.coerceIn(0, 255).toByte()
    }

    companion object {
        /** Luminance of packed ARGB pixels, as Android's Bitmap.getPixels gives them. */
        fun fromArgb(argb: IntArray, width: Int, height: Int): GrayImage {
            val out = ByteArray(width * height)
            for (i in out.indices) out[i] = luminance(argb[i]).toByte()
            return GrayImage(width, height, out)
        }

        fun luminance(argb: Int): Int {
            val r = argb shr 16 and 0xFF
            val g = argb shr 8 and 0xFF
            val b = argb and 0xFF
            return (299 * r + 587 * g + 114 * b + 500) / 1000
        }
    }
}

/** Four corners, clockwise from the top left, in pixels. */
data class Quad(val tl: Point, val tr: Point, val br: Point, val bl: Point) {
    data class Point(val x: Double, val y: Double)

    val corners: List<Point> get() = listOf(tl, tr, br, bl)
}

/**
 * Gets a photographed or scanned page ready for text recognition. 0.10.0 handed Tesseract the
 * photo as it was; these steps are what made recognition reliable on real phone photos:
 *
 * - [findPage] and [straighten]: find the sheet of paper and flatten its perspective, so lines
 *   run straight and the table around the sheet is gone.
 * - [findColumns]: split clear text columns, so they are read one after the other instead of
 *   line by line across the page.
 *
 * Measured on 33 phone-photo pages with known text (shadows, dim light, blur, receipts, two
 * columns, a sideways page): character errors fell from 23.1% to 1.3% with the rest of the
 * changes in [PageReader].
 */
object PagePrep {
    private const val FIND_SIDE = 360
    private const val ANALYSIS_WIDTH = 800

    // ---- Finding the sheet ------------------------------------------------------------------

    /**
     * The corners of the sheet of paper in [image], or null when there is no clear sheet (it fills
     * the frame already, a shadow hides a corner, or the picture is not of a page). When in doubt
     * the answer is null, because cropping away text would be worse than not straightening.
     */
    fun findPage(image: GrayImage): Quad? {
        val f = FIND_SIDE.toDouble() / max(image.width, image.height)
        val sw = max(1, (image.width * f).toInt())
        val sh = max(1, (image.height * f).toInt())
        val small = resizeBox(image, sw, sh)
        val threshold = otsu(small)
        val mask = BooleanArray(sw * sh) { (small.pixels[it].toInt() and 0xFF) > threshold }
        val sheet = largestComponent(mask, sw, sh) ?: return null
        if (sheet.size < 0.2 * sw * sh) return null
        // A sheet touching three edges of the frame is already as cropped as it gets.
        val touches = listOf(sheet.minX <= 1, sheet.minY <= 1, sheet.maxX >= sw - 2, sheet.maxY >= sh - 2).count { it }
        if (touches >= 3) return null
        val quad = sheet.quad
        val area = polygonArea(quad)
        if (area <= 0 || sheet.size / area < 0.85) return null
        if (!plausible(quad)) return null
        fun full(p: Quad.Point) = Quad.Point((p.x + 0.5) / f, (p.y + 0.5) / f)
        return Quad(full(quad.tl), full(quad.tr), full(quad.br), full(quad.bl))
    }

    /** A photographed sheet: opposite sides of similar length and corners not far from square. */
    internal fun plausible(q: Quad, minSideRatio: Double = 0.8, maxAngleError: Double = 20.0): Boolean {
        fun len(a: Quad.Point, b: Quad.Point) = hypot(b.x - a.x, b.y - a.y)
        val top = len(q.tl, q.tr)
        val bottom = len(q.bl, q.br)
        val left = len(q.tl, q.bl)
        val right = len(q.tr, q.br)
        if (min(top, bottom) < minSideRatio * max(top, bottom)) return false
        if (min(left, right) < minSideRatio * max(left, right)) return false
        val c = q.corners
        for (i in 0 until 4) {
            val a = c[(i + 3) % 4]
            val b = c[i]
            val d = c[(i + 1) % 4]
            val ux = a.x - b.x
            val uy = a.y - b.y
            val vx = d.x - b.x
            val vy = d.y - b.y
            val cos = (ux * vx + uy * vy) / (hypot(ux, uy) * hypot(vx, vy) + 1e-9)
            val degrees = Math.toDegrees(acos(cos.coerceIn(-1.0, 1.0)))
            if (abs(degrees - 90) > maxAngleError) return false
        }
        return true
    }

    private fun polygonArea(q: Quad): Double {
        val c = q.corners
        var sum = 0.0
        for (i in 0 until 4) {
            val a = c[i]
            val b = c[(i + 1) % 4]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2
    }

    internal class Component(val size: Int, val minX: Int, val minY: Int, val maxX: Int, val maxY: Int, val quad: Quad)

    /** The biggest 4-connected region of [mask], with the corners of its outline. */
    internal fun largestComponent(mask: BooleanArray, w: Int, h: Int): Component? {
        val seen = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        var best: Component? = null
        for (start in mask.indices) {
            if (!mask[start] || seen[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            seen[start] = true
            var minX = w
            var minY = h
            var maxX = -1
            var maxY = -1
            // Corners: smallest and largest x+y, largest and smallest x-y.
            var tl = start
            var br = start
            var tr = start
            var bl = start
            fun s(i: Int) = i % w + i / w
            fun d(i: Int) = i % w - i / w
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                val y = i / w
                minX = min(minX, x)
                maxX = max(maxX, x)
                minY = min(minY, y)
                maxY = max(maxY, y)
                if (s(i) < s(tl)) tl = i
                if (s(i) > s(br)) br = i
                if (d(i) > d(tr)) tr = i
                if (d(i) < d(bl)) bl = i
                if (x + 1 < w) visit(i + 1, mask, seen, queue, tail).also { if (it) tail++ }
                if (x > 0) visit(i - 1, mask, seen, queue, tail).also { if (it) tail++ }
                if (y + 1 < h) visit(i + w, mask, seen, queue, tail).also { if (it) tail++ }
                if (y > 0) visit(i - w, mask, seen, queue, tail).also { if (it) tail++ }
            }
            if (best == null || tail > best.size) {
                fun p(i: Int) = Quad.Point((i % w).toDouble(), (i / w).toDouble())
                best = Component(tail, minX, minY, maxX, maxY, Quad(p(tl), p(tr), p(br), p(bl)))
            }
        }
        return best
    }

    private fun visit(i: Int, mask: BooleanArray, seen: BooleanArray, queue: IntArray, tail: Int): Boolean {
        if (!mask[i] || seen[i]) return false
        seen[i] = true
        queue[tail] = i
        return true
    }

    /** Otsu's threshold: the grey level that best separates dark from light. */
    internal fun otsu(image: GrayImage): Int {
        val hist = LongArray(256)
        for (b in image.pixels) hist[b.toInt() and 0xFF]++
        val total = image.pixels.size.toDouble()
        var sumAll = 0.0
        for (t in 0 until 256) sumAll += t * hist[t].toDouble()
        var wB = 0.0
        var sumB = 0.0
        var best = -1.0
        var bestT = 127
        for (t in 0 until 256) {
            wB += hist[t]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += t * hist[t].toDouble()
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) {
                best = between
                bestT = t
            }
        }
        return bestT
    }

    // ---- Straightening ----------------------------------------------------------------------

    /** Maps the sheet inside [quad] onto an upright rectangle, dropping a thin rim at its edge. */
    fun straighten(image: GrayImage, quad: Quad, inset: Double = 0.006): GrayImage {
        fun len(a: Quad.Point, b: Quad.Point) = hypot(b.x - a.x, b.y - a.y)
        val width = max(1, max(len(quad.tl, quad.tr), len(quad.bl, quad.br)).toInt())
        val height = max(1, max(len(quad.tl, quad.bl), len(quad.tr, quad.br)).toInt())
        val ix = width * inset
        val iy = height * inset
        val from = listOf(Quad.Point(-ix, -iy), Quad.Point(width + ix, -iy), Quad.Point(width + ix, height + iy), Quad.Point(-ix, height + iy))
        val h = homography(from, quad.corners)
        val out = GrayImage(width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Sample at the pixel's centre, the way image libraries do.
                val px = x + 0.5
                val py = y + 0.5
                val wz = h[6] * px + h[7] * py + 1.0
                val sx = (h[0] * px + h[1] * py + h[2]) / wz
                val sy = (h[3] * px + h[4] * py + h[5]) / wz
                out[x, y] = sampleBilinear(image, sx - 0.5, sy - 0.5)
            }
        }
        return out
    }

    /** The 3x3 perspective map (with h[8] = 1) taking each of [from] to the matching [to]. */
    internal fun homography(from: List<Quad.Point>, to: List<Quad.Point>): DoubleArray {
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val (x, y) = from[i]
            val (u, v) = to[i]
            a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        // Gaussian elimination with partial pivoting.
        for (col in 0 until 8) {
            var pivot = col
            for (r in col + 1 until 8) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
            val tmp = a[col]
            a[col] = a[pivot]
            a[pivot] = tmp
            val p = a[col][col]
            require(abs(p) > 1e-12) { "corners do not form a quadrilateral" }
            for (k in col until 9) a[col][k] /= p
            for (r in 0 until 8) {
                if (r == col) continue
                val factor = a[r][col]
                if (factor == 0.0) continue
                for (k in col until 9) a[r][k] -= factor * a[col][k]
            }
        }
        return DoubleArray(8) { a[it][8] }
    }

    private fun sampleBilinear(image: GrayImage, x: Double, y: Double): Int {
        if (x < -0.5 || y < -0.5 || x > image.width - 0.5 || y > image.height - 0.5) return 255
        val cx = x.coerceIn(0.0, image.width - 1.0)
        val cy = y.coerceIn(0.0, image.height - 1.0)
        val x0 = floor(cx).toInt()
        val y0 = floor(cy).toInt()
        val x1 = min(x0 + 1, image.width - 1)
        val y1 = min(y0 + 1, image.height - 1)
        val fx = cx - x0
        val fy = cy - y0
        val top = image[x0, y0] * (1 - fx) + image[x1, y0] * fx
        val bottom = image[x0, y1] * (1 - fx) + image[x1, y1] * fx
        return (top * (1 - fy) + bottom * fy).roundToInt()
    }

    // ---- Columns ----------------------------------------------------------------------------

    /**
     * The x ranges of the page's text columns, left to right; a single range when it is one
     * column. Only clear gutters split a page: each column must be wide and run down most of the
     * page, so receipts with a price column and tables stay whole and keep their lines together.
     */
    fun findColumns(image: GrayImage, minGutter: Double = 0.025, minColumn: Double = 0.28, leak: Double = 0.03): List<IntRange> {
        val whole = listOf(0 until image.width)
        val f = ANALYSIS_WIDTH.toDouble() / image.width
        val ink = inkMask(image)
        val w = ink.width
        val h = ink.height
        val rowInk = IntArray(h)
        val colInk = IntArray(w)
        for (y in 0 until h) for (x in 0 until w) if (ink.dark[y * w + x]) {
            rowInk[y]++
            colInk[x]++
        }
        val rows = (0 until h).filter { rowInk[it] > 2 }
        val cols = (0 until w).filter { colInk[it] > 2 }
        if (rows.size < 20 || cols.size < 20) return whole
        val top = rows.first()
        val bottom = rows.last()
        val left = cols.first()
        val right = cols.last()
        val textH = bottom - top + 1
        val textW = right - left + 1
        val profile = IntArray(textW)
        for (y in top..bottom) for (x in left..right) if (ink.dark[y * w + x]) profile[x - left]++
        val gutters = mutableListOf<IntRange>()
        var x = 0
        while (x < textW) {
            if (profile[x] <= leak * textH) {
                val start = x
                while (x < textW && profile[x] <= leak * textH) x++
                if (x - start >= minGutter * textW) gutters += start until x
            } else {
                x++
            }
        }
        if (gutters.isEmpty()) return whole
        val edges = listOf(0) + gutters.flatMap { listOf(it.first, it.last + 1) } + textW
        val spans = edges.chunked(2).map { (a, b) -> a to b }
        for ((a, b) in spans) {
            if (b - a < minColumn * textW) return whole
            // The column's text must run down most of the page, not be a picture or a caption.
            var first = -1
            var last = -1
            for (y in top..bottom) {
                for (xx in left + a until left + b) if (ink.dark[y * w + xx]) {
                    if (first < 0) first = y
                    last = y
                    break
                }
            }
            if (first < 0 || (last - first + 1).toDouble() / textH < 0.5) return whole
        }
        if (spans.size < 2) return whole
        return spans.mapIndexed { i, (a, b) ->
            val x0 = if (i == 0) 0 else ((left + (spans[i - 1].second + a) / 2.0) / f).toInt()
            val x1 = if (i == spans.lastIndex) image.width else ((left + (b + spans[i + 1].first) / 2.0) / f).toInt()
            x0 until x1
        }
    }

    internal class InkMask(val width: Int, val height: Int, val dark: BooleanArray)

    /** Which pixels are ink: darker than the paper around them, whatever the lighting. */
    internal fun inkMask(image: GrayImage): InkMask {
        val sw = ANALYSIS_WIDTH
        val sh = max(1, (image.height * ANALYSIS_WIDTH.toDouble() / image.width).toInt())
        val small = resizeBox(image, sw, sh)
        val cell = max(1, (max(sw, sh) / 160.0).roundToInt())
        val tiny = resizeBox(small, max(1, sw / cell), max(1, sh / cell))
        val paper = resizeBilinear(boxBlur(maxFilter(tiny, 3), 3), sw, sh)
        val dark = BooleanArray(sw * sh) { i ->
            val bg = max(1, paper.pixels[i].toInt() and 0xFF)
            (small.pixels[i].toInt() and 0xFF) < 0.72 * bg
        }
        return InkMask(sw, sh, dark)
    }

    // ---- Basic image operations -------------------------------------------------------------

    /** Area-averaging resize, for shrinking. */
    fun resizeBox(image: GrayImage, w: Int, h: Int): GrayImage {
        val sx = image.width.toDouble() / w
        val sy = image.height.toDouble() / h
        // Horizontal pass into a float buffer, then vertical.
        val mid = FloatArray(w * image.height)
        for (x in 0 until w) {
            val a = x * sx
            val b = a + sx
            for (y in 0 until image.height) {
                var sum = 0.0
                var px = floor(a).toInt()
                while (px < b && px < image.width) {
                    val cover = min(b, px + 1.0) - max(a, px.toDouble())
                    if (cover > 0) sum += cover * image[px, y]
                    px++
                }
                mid[y * w + x] = (sum / sx).toFloat()
            }
        }
        val out = GrayImage(w, h)
        for (y in 0 until h) {
            val a = y * sy
            val b = a + sy
            for (x in 0 until w) {
                var sum = 0.0
                var py = floor(a).toInt()
                while (py < b && py < image.height) {
                    val cover = min(b, py + 1.0) - max(a, py.toDouble())
                    if (cover > 0) sum += cover * mid[py * w + x]
                    py++
                }
                out[x, y] = (sum / sy).roundToInt()
            }
        }
        return out
    }

    /** Bilinear resize, sampling at pixel centres. */
    fun resizeBilinear(image: GrayImage, w: Int, h: Int): GrayImage {
        val out = GrayImage(w, h)
        val sx = image.width.toDouble() / w
        val sy = image.height.toDouble() / h
        for (y in 0 until h) for (x in 0 until w) {
            out[x, y] = sampleBilinear(image, (x + 0.5) * sx - 0.5, (y + 0.5) * sy - 0.5)
        }
        return out
    }

    private fun maxFilter(image: GrayImage, r: Int): GrayImage {
        val w = image.width
        val h = image.height
        val rowMax = GrayImage(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0
            for (dx in -r..r) m = max(m, image[(x + dx).coerceIn(0, w - 1), y])
            rowMax[x, y] = m
        }
        val out = GrayImage(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0
            for (dy in -r..r) m = max(m, rowMax[x, (y + dy).coerceIn(0, h - 1)])
            out[x, y] = m
        }
        return out
    }

    private fun boxBlur(image: GrayImage, r: Int): GrayImage {
        val w = image.width
        val h = image.height
        val k = 2 * r + 1
        val mid = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0
            for (dx in -r..r) s += image[(x + dx).coerceIn(0, w - 1), y]
            mid[y * w + x] = s.toFloat() / k
        }
        val out = GrayImage(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f
            for (dy in -r..r) s += mid[(y + dy).coerceIn(0, h - 1) * w + x]
            out[x, y] = (s / k).roundToInt()
        }
        return out
    }

    /** Turns the picture by a multiple of 90 degrees, counter-clockwise like a photo app's rotate button. */
    fun rotate(image: GrayImage, degrees: Int): GrayImage {
        val w = image.width
        val h = image.height
        return when (((degrees % 360) + 360) % 360) {
            0 -> image
            90 -> GrayImage(h, w).also { o -> for (y in 0 until h) for (x in 0 until w) o[y, w - 1 - x] = image[x, y] }
            180 -> GrayImage(w, h).also { o -> for (y in 0 until h) for (x in 0 until w) o[w - 1 - x, h - 1 - y] = image[x, y] }
            270 -> GrayImage(h, w).also { o -> for (y in 0 until h) for (x in 0 until w) o[h - 1 - y, x] = image[x, y] }
            else -> throw IllegalArgumentException("only quarter turns: $degrees")
        }
    }

    fun crop(image: GrayImage, xs: IntRange): GrayImage {
        val x0 = xs.first.coerceIn(0, image.width - 1)
        val x1 = (xs.last + 1).coerceIn(x0 + 1, image.width)
        val w = x1 - x0
        val out = ByteArray(w * image.height)
        for (y in 0 until image.height) System.arraycopy(image.pixels, y * image.width + x0, out, y * w, w)
        return GrayImage(w, image.height, out)
    }

    /** Shrinks so the longest side is at most [maxSide]; smaller pictures are returned as they are. */
    fun fit(image: GrayImage, maxSide: Int): GrayImage {
        val longest = max(image.width, image.height)
        if (longest <= maxSide) return image
        val f = maxSide.toDouble() / longest
        return resizeBox(image, max(1, (image.width * f).toInt()), max(1, (image.height * f).toInt()))
    }

    internal fun distance(a: Quad.Point, b: Quad.Point) = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
}
