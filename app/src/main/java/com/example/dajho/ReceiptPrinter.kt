package com.example.dajho

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import java.io.FileOutputStream

/** Uses Android's print services, including installed thermal/Bluetooth printer services. */
object ReceiptPrinter {
    fun print(activity: Activity, title: String, contentText: String) {
        val manager = activity.getSystemService(Activity.PRINT_SERVICE) as PrintManager
        manager.print(title, object : PrintDocumentAdapter() {
            override fun onLayout(
                old: PrintAttributes?,
                new: PrintAttributes?,
                cancellation: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: android.os.Bundle?
            ) {
                if (cancellation?.isCanceled == true) {
                    callback.onLayoutCancelled()
                } else {
                    callback.onLayoutFinished(
                        PrintDocumentInfo.Builder("$title.pdf")
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .build(),
                        true
                    )
                }
            }

            override fun onWrite(
                ranges: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellation: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                val document = PdfDocument()
                try {
                    val lines = contentText.split("\n")
                    val paint = Paint().apply {
                        typeface = Typeface.MONOSPACE
                        textSize = 12f
                        color = Color.BLACK
                        isAntiAlias = true
                    }
                    val pages = lines.chunked(44)
                    pages.forEachIndexed { index, pageLines ->
                        if (cancellation?.isCanceled == true) {
                            callback.onWriteCancelled()
                            return
                        }
                        val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                        val canvas: Canvas = page.canvas
                        pageLines.forEachIndexed { lineIdx, text ->
                            canvas.drawText(text, 36f, 48f + lineIdx * 17f, paint)
                        }
                        document.finishPage(page)
                    }
                    FileOutputStream(destination.fileDescriptor).use(document::writeTo)
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed("No se pudo generar el comprobante")
                } finally {
                    document.close()
                }
            }
        }, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }
}
