package com.leaf.reader.data

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

data class PageSlice(val start: Int, val end: Int)

/** Layout-derived offsets remain stable for the same book, font settings and viewport. */
object EpubPagination {
    fun paginate(text: String, widthPx: Int, heightPx: Int, fontPx: Float): List<PageSlice> {
        if (text.isEmpty()) return listOf(PageSlice(0, 0))
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = fontPx
            typeface = android.graphics.Typeface.SERIF
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(fontPx * 0.15f, 1.15f)
            .build()
        val result = mutableListOf<PageSlice>()
        var firstLine = 0
        for (line in 1 until layout.lineCount) {
            if (layout.getLineBottom(line) - layout.getLineTop(firstLine) > heightPx.coerceAtLeast(1)) {
                result += PageSlice(layout.getLineStart(firstLine), layout.getLineStart(line))
                firstLine = line
            }
        }
        result += PageSlice(layout.getLineStart(firstLine), text.length)
        return result.filter { it.end > it.start }
    }

    fun pageForOffset(pages: List<PageSlice>, offset: Int): Int =
        pages.indexOfLast { it.start <= offset }.coerceAtLeast(0)
}
