package app.drop.ocr

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors

/**
 * Runs the real page pipeline over a folder of decoded test pages, with the desktop Tesseract as
 * the engine, and writes what it read. Only runs when -DocrBench=<folder> is given; the scoring
 * lives with the benchmark pages, outside the app.
 */
class OcrBenchHarness {
    @Test
    fun readBenchPages() {
        val dir = System.getProperty("ocrBench")?.let(::File)
        assumeTrue("set -DocrBench to run", dir != null && dir.isDirectory)
        val tessdata = File(dir, "../fast").canonicalPath
        val pages = File(dir!!, "decoded").listFiles { f -> f.name.endsWith(".pgm") }!!.sortedBy { it.name }
        val pool = Executors.newFixedThreadPool(4)
        val results = pages.map { file ->
            pool.submit<String> {
                val image = load(file)
                val started = System.nanoTime()
                val text = CliTesseract(tessdata).use { PageReader(it).read(image) }
                val seconds = (System.nanoTime() - started) / 1e9
                file.nameWithoutExtension + "\t" + seconds + "\t" + text.replace("\\", "\\\\").replace("\n", "\\n")
            }
        }.map { it.get() }
        pool.shutdown()
        File(dir, "kotlin_out.tsv").writeText(results.joinToString("\n"))
    }

    private fun load(file: File): GrayImage = readPgm(file)
}

/** Desktop Tesseract with the same settings as the app: single block, Sauvola thresholding. */
private class CliTesseract(private val tessdata: String) : OcrEngine {
    override fun recognize(page: GrayImage): Recognized {
        val input = File.createTempFile("page", ".pgm")
        val out = File.createTempFile("ocr", "")
        try {
            writePgm(page, input)
            val p = ProcessBuilder(
                "tesseract", input.path, out.path, "--tessdata-dir", tessdata, "-l", "eng+tur", "--oem", "1", "--psm", "6",
                "-c", "thresholding_method=2", "-c", "tessedit_create_tsv=1", "-c", "tessedit_create_txt=1",
            ).redirectErrorStream(true).apply { environment()["OMP_THREAD_LIMIT"] = "1" }.start()
            p.inputStream.readBytes()
            p.waitFor()
            val text = File(out.path + ".txt").readText()
            val confs = File(out.path + ".tsv").readLines().drop(1).mapNotNull { line ->
                val c = line.split("\t")
                if (c.size == 12 && c[0] == "5" && c[11].isNotBlank()) c[10].toDoubleOrNull()?.takeIf { it >= 0 } else null
            }
            return Recognized(text, if (confs.isEmpty()) 0 else confs.average().toInt())
        } finally {
            input.delete()
            out.delete()
            File(out.path + ".txt").delete()
            File(out.path + ".tsv").delete()
        }
    }

    override fun close() = Unit
}

internal fun writePgm(page: GrayImage, file: File) {
    file.outputStream().buffered().use {
        it.write("P5\n${page.width} ${page.height}\n255\n".toByteArray())
        it.write(page.pixels)
    }
}

internal fun readPgm(file: File): GrayImage {
    val bytes = file.readBytes()
    var pos = 0
    fun token(): String {
        while (bytes[pos].toInt().toChar().isWhitespace()) pos++
        val start = pos
        while (!bytes[pos].toInt().toChar().isWhitespace()) pos++
        return String(bytes, start, pos - start)
    }
    check(token() == "P5")
    val w = token().toInt()
    val h = token().toInt()
    token()
    pos++
    return GrayImage(w, h, bytes.copyOfRange(pos, pos + w * h))
}
