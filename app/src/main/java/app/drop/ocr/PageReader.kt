package app.drop.ocr

import java.io.Closeable

/** What the recogniser found on one picture. [confidence] is Tesseract's mean word confidence, 0 to 100. */
data class Recognized(val text: String, val confidence: Int)

/** Reads text from one prepared page. Implemented by Tesseract on phones and by fakes in tests. */
interface OcrEngine : Closeable {
    fun recognize(page: GrayImage): Recognized
}

/**
 * Reads one page: straighten the sheet, split clear columns, recognise each, and turn the page
 * when the text comes out as noise because the picture was sideways or upside down.
 */
class PageReader(private val engine: OcrEngine) {
    fun read(page: GrayImage): String {
        val first = readUpright(page)
        if (first.confidence >= LOW_CONFIDENCE) return OcrText.tidy(first.text)
        // Nonsense usually means the page is turned. Check the other three ways on a small copy;
        // upright pages never pay for this.
        val small = PagePrep.fit(page, ORIENTATION_SIDE)
        var bestAngle = 0
        var bestConfidence = first.confidence
        for (angle in intArrayOf(90, 180, 270)) {
            val tried = engine.recognize(PagePrep.rotate(small, angle))
            if (words(tried.text) >= MIN_WORDS && tried.confidence > bestConfidence + MARGIN) {
                bestAngle = angle
                bestConfidence = tried.confidence
            }
        }
        if (bestAngle == 0) return OcrText.tidy(first.text)
        return OcrText.tidy(readUpright(PagePrep.rotate(page, bestAngle)).text)
    }

    private fun readUpright(page: GrayImage): Recognized {
        val sheet = PagePrep.findPage(page)?.let { PagePrep.straighten(page, it) } ?: page
        val columns = PagePrep.findColumns(sheet)
        if (columns.size == 1) return engine.recognize(sheet)
        val parts = columns.map { engine.recognize(PagePrep.crop(sheet, it)) }
        val text = parts.map { it.text.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
        val confidence = parts.filter { it.text.isNotBlank() }.map { it.confidence }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
        return Recognized(text, confidence)
    }

    private fun words(text: String) = text.split(Regex("\\s+")).count { w -> w.any { it.isLetterOrDigit() } }

    companion object {
        /** Upright pages score 85 to 96 in measurements; sideways or upside down ones 30 to 57. */
        const val LOW_CONFIDENCE = 65
        const val ORIENTATION_SIDE = 1300
        const val MIN_WORDS = 10
        const val MARGIN = 10
    }
}

/** Cleans up recognised text without changing what it says. */
object OcrText {
    private val endsSentence = Regex("[.!?:;…)\\]\"”’»]$")

    /**
     * - Drops lines with no letters or digits (the "|" and "~" Tesseract reads from the paper's edge).
     * - Rejoins words split across lines with a hyphen ("ever-\ny" → "every").
     * - Joins lines that only wrapped because the page was narrow, so paragraphs read as
     *   paragraphs on a phone. Short lines (lists, receipts, addresses) are kept as they are.
     */
    fun tidy(raw: String): String {
        val blocks = raw.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
        return blocks.map { tidyBlock(it) }.filter { it.isNotEmpty() }.joinToString("\n\n")
    }

    private fun tidyBlock(block: String): String {
        val lines = block.lines()
            .map { it.trim().replace(Regex("[ \\t]+"), " ") }
            .filter { line -> line.any { it.isLetterOrDigit() } }
        if (lines.isEmpty()) return ""
        val longest = lines.maxOf { it.length }
        val out = StringBuilder(lines[0])
        for (i in 1 until lines.size) {
            val previous = lines[i - 1]
            val line = lines[i]
            val wrapped = previous.length >= WRAPPED_SHARE * longest && longest >= MIN_WRAP_LENGTH
            when {
                wrapped && previous.endsWith("-") && previous.length > 1 && previous[previous.length - 2].isLetter() && line.first().isLowerCase() -> {
                    out.setLength(out.length - 1)
                    out.append(line)
                }
                wrapped && !endsSentence.containsMatchIn(previous) -> out.append(' ').append(line)
                wrapped && line.first().isLowerCase() -> out.append(' ').append(line)
                else -> out.append('\n').append(line)
            }
        }
        return out.toString()
    }

    /** A line at least this share of the block's longest line probably ran to the right margin. */
    private const val WRAPPED_SHARE = 0.72
    private const val MIN_WRAP_LENGTH = 30
}
