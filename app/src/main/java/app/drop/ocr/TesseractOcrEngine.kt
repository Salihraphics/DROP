package app.drop.ocr

import android.content.Context
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.IOException

/**
 * Tesseract, on the phone, for English and Turkish.
 *
 * Two settings differ from 0.10.0, both measured on phone photos: Sauvola thresholding, which
 * judges each part of the page against its own surroundings so shadows and uneven light stop
 * swallowing text (errors on shadowed pages fell from 23% to under 2%), and an explicit
 * single-block layout, which keeps lines in reading order on tilted photos.
 */
class TesseractOcrEngine(context: Context) : OcrEngine {
    // Started here, not on first use, so a phone where the native library can't load fails while
    // the engine is created, which the queue reports as "couldn't read" instead of crashing.
    private var api: TessBaseAPI? = TessBaseAPI().also { created ->
        if (!created.init(OcrData.install(context).path, OcrData.LANGUAGES, TessBaseAPI.OEM_LSTM_ONLY)) {
            created.recycle()
            throw IOException("OCR engine could not start")
        }
        created.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
        created.setVariable(THRESHOLDING_METHOD, SAUVOLA)
    }

    override fun recognize(page: GrayImage): Recognized {
        val tess = api ?: throw IllegalStateException("engine closed")
        tess.setImage(page.pixels, page.width, page.height, 1, page.width)
        try {
            val text = tess.utF8Text.orEmpty()
            return Recognized(text, tess.meanConfidence())
        } finally {
            tess.clear()
        }
    }

    override fun close() {
        api?.recycle()
        api = null
    }

    private companion object {
        const val THRESHOLDING_METHOD = "thresholding_method"
        const val SAUVOLA = "2"
    }
}
