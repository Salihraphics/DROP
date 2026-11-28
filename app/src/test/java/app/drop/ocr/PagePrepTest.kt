package app.drop.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** Page preparation for OCR: finding and straightening the sheet, columns, turning, tidying text. */
class PagePrepTest {
    /** A "photo": a dark table with a bright sheet turned by [degrees] around the centre. */
    private fun photo(w: Int = 900, h: Int = 1200, sheetW: Double = 600.0, sheetH: Double = 840.0, degrees: Double = 4.0, shadowCorner: Boolean = false): Pair<GrayImage, List<Quad.Point>> {
        val img = GrayImage(w, h)
        val a = Math.toRadians(degrees)
        val cx = w / 2.0
        val cy = h / 2.0
        val corners = listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1).map { (sx, sy) ->
            val x = sx * sheetW / 2
            val y = sy * sheetH / 2
            Quad.Point(cx + x * cos(a) - y * sin(a), cy + x * sin(a) + y * cos(a))
        }
        for (y in 0 until h) for (x in 0 until w) {
            // Back into the sheet's own coordinates.
            val dx = x - cx
            val dy = y - cy
            val u = dx * cos(a) + dy * sin(a)
            val v = -dx * sin(a) + dy * cos(a)
            val onSheet = kotlin.math.abs(u) < sheetW / 2 && kotlin.math.abs(v) < sheetH / 2
            var value = if (onSheet) 235 else 70
            // Lines of "text": dark bars across the sheet.
            if (onSheet && u > -sheetW / 2 + 60 && u < sheetW / 2 - 60) {
                val row = ((v + sheetH / 2 - 60) / 30).toInt()
                val inRow = ((v + sheetH / 2 - 60) % 30)
                if (v + sheetH / 2 > 60 && v < sheetH / 2 - 60 && inRow < 4) value = 30
            }
            if (shadowCorner && x > w * 0.55 && y < h * 0.4) value = (value * 0.35).toInt()
            img[x, y] = value
        }
        return img to corners
    }

    @Test
    fun findsAndStraightensATurnedSheet() {
        val (img, truth) = photo()
        val quad = PagePrep.findPage(img)
        assertNotNull(quad)
        quad!!
        quad.corners.zip(truth).forEach { (found, real) ->
            assertTrue("corner $found should be near $real", PagePrep.distance(found, real) < 12)
        }
        val flat = PagePrep.straighten(img, quad)
        assertEquals(600.0, flat.width.toDouble(), 15.0)
        assertEquals(840.0, flat.height.toDouble(), 15.0)
        // After straightening, text lines run level: a row through a bar is dark from end to end.
        fun levelRows(g: GrayImage) = (g.height / 3 until g.height * 2 / 3).count { y -> (g.width / 4 until g.width * 3 / 4).all { x -> g[x, y] < 128 } }
        assertEquals(0, levelRows(img))
        assertTrue("level lines, found ${levelRows(flat)} fully dark rows", levelRows(flat) >= 15)
    }

    @Test
    fun leavesThePhotoAloneWhenItCannotBeSure() {
        // A shadow eats a corner: guessing corners there would crop text away.
        assertNull(PagePrep.findPage(photo(shadowCorner = true).first))
        // A scan or screenshot already fills the frame.
        val scan = GrayImage(800, 1000).also { img -> for (i in img.pixels.indices) img.pixels[i] = 240.toByte() }
        assertNull(PagePrep.findPage(scan))
        // No sheet at all.
        val dark = GrayImage(800, 1000).also { img -> for (i in img.pixels.indices) img.pixels[i] = (i % 97).toByte() }
        assertNull(PagePrep.findPage(dark))
    }

    @Test
    fun homographyMapsTheCornersExactly() {
        val from = listOf(Quad.Point(0.0, 0.0), Quad.Point(100.0, 0.0), Quad.Point(100.0, 200.0), Quad.Point(0.0, 200.0))
        val to = listOf(Quad.Point(12.0, 30.0), Quad.Point(118.0, 22.0), Quad.Point(131.0, 240.0), Quad.Point(4.0, 251.0))
        val h = PagePrep.homography(from, to)
        from.zip(to).forEach { (p, q) ->
            val w = h[6] * p.x + h[7] * p.y + 1
            assertEquals(q.x, (h[0] * p.x + h[1] * p.y + h[2]) / w, 1e-6)
            assertEquals(q.y, (h[3] * p.x + h[4] * p.y + h[5]) / w, 1e-6)
        }
    }

    /** A clean page with lines of "words" in the given x ranges. */
    private fun page(vararg blocks: IntRange, w: Int = 1600, h: Int = 2000, lineGap: Int = 44): GrayImage {
        val img = GrayImage(w, h)
        for (i in img.pixels.indices) img.pixels[i] = 245.toByte()
        var y = 120
        var line = 0
        while (y < h - 120) {
            for (b in blocks) {
                // Words of varying length with gaps, so single columns have no straight gutter.
                var x = b.first
                var word = line * 7
                while (x < b.last) {
                    val len = 40 + (word * 37 % 90)
                    for (yy in y until y + 18) for (xx in x until minOf(x + len, b.last)) img[xx, yy] = 25
                    x += len + 14
                    word++
                }
            }
            y += lineGap
            line++
        }
        return img
    }

    @Test
    fun splitsClearColumnsOnly() {
        val two = PagePrep.findColumns(page(100..740, 860..1500))
        assertEquals(2, two.size)
        assertTrue(two[0].last in 740..860 && two[1].first in 740..860)
        assertEquals(1, PagePrep.findColumns(page(100..1500)).size)
        // A receipt: item names on the left, a narrow price column on the right. Lines must stay whole.
        assertEquals(1, PagePrep.findColumns(page(100..900, 1320..1500)).size)
    }

    @Test
    fun turnsByQuarterTurns() {
        val img = GrayImage(3, 2, byteArrayOf(1, 2, 3, 4, 5, 6))
        val r90 = PagePrep.rotate(img, 90)
        assertEquals(2, r90.width)
        assertEquals(3, r90.height)
        assertEquals(listOf(3, 6, 2, 5, 1, 4), r90.pixels.map { it.toInt() })
        assertEquals(listOf(6, 5, 4, 3, 2, 1), PagePrep.rotate(img, 180).pixels.map { it.toInt() })
        assertEquals(img.pixels.toList(), PagePrep.rotate(PagePrep.rotate(img, 90), 270).pixels.toList())
    }

    @Test
    fun tidiesTextWithoutChangingIt() {
        val raw = """
            The meeting moved to April because the venue was not ready and the
            team needs another week to finish the onboarding flow for every-
            one who signed up.
            |
            Shopping list:
            eggs
            oat milk

            ~ —
            Second column starts here.
        """.trimIndent()
        assertEquals(
            "The meeting moved to April because the venue was not ready and the team needs another week to finish " +
                "the onboarding flow for everyone who signed up.\nShopping list:\neggs\noat milk\n\nSecond column starts here.",
            OcrText.tidy(raw),
        )
    }

    /** Pretends to read: confident only when the page is upright, which a dark top-left corner marks. */
    private class FakeEngine : OcrEngine {
        val calls = mutableListOf<Pair<Int, Int>>()

        override fun recognize(page: GrayImage): Recognized {
            calls += page.width to page.height
            val upright = page[2, 2] < 50
            return if (upright) Recognized("these are ten clear words read from an upright page", 92) else Recognized("x'; ,. ~~ |", 31)
        }

        override fun close() = Unit
    }

    private fun marked(w: Int, h: Int, corner: Int): GrayImage {
        val img = GrayImage(w, h)
        for (i in img.pixels.indices) img.pixels[i] = 240.toByte()
        val (cx, cy) = when (corner) {
            0 -> 0 to 0
            1 -> w - 10 to 0
            2 -> w - 10 to h - 10
            else -> 0 to h - 10
        }
        for (y in cy until cy + 10) for (x in cx until cx + 10) img[x, y] = 10
        return img
    }

    @Test
    fun uprightPagesAreReadOnce() {
        val engine = FakeEngine()
        assertEquals("these are ten clear words read from an upright page", PageReader(engine).read(marked(400, 600, 0)))
        assertEquals(1, engine.calls.size)
    }

    @Test
    fun aSidewaysPageIsTurnedAndReadAgain() {
        val engine = FakeEngine()
        // Marker at the top right: the page was turned a quarter clockwise.
        val text = PageReader(engine).read(marked(600, 400, 1))
        assertEquals("these are ten clear words read from an upright page", text)
        assertTrue(engine.calls.size in 3..5)
    }
}
