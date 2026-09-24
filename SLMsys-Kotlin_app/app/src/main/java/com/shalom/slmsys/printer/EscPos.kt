package com.shalom.slmsys.printer

import android.graphics.Bitmap
import android.graphics.Color
import com.shalom.slmsys.data.Product
import com.shalom.slmsys.data.StoreSettings

/**
 * Desenha a etiqueta pelo LabelRenderer (layout do servidor) e gera
 * comando ESC/POS ou TSPL usando o **mesmo tamanho em mm** do designer —
 * sem forçar 50×30 e distorcer o layout.
 */
object EscPos {

    private const val DOTS_PER_MM = 8
    private const val ESC = 0x1B
    private const val BAND = 24

    fun buildLabel(product: Product, settings: StoreSettings, copies: Int = 1): ByteArray {
        val label = LabelRenderer.render(product, settings)
        val wMm = label.widthMm.toInt().coerceIn(20, 100)
        val hMm = label.heightMm.toInt().coerceIn(15, 120)
        val single = if (settings.protocol == "tspl") {
            commandTspl(label.bitmap, wMm, hMm, settings.printSpeed)
        } else {
            commandEscPos(label.bitmap, wMm, hMm, settings.feedMm)
        }
        if (!label.bitmap.isRecycled) {
            // bitmap ainda pode ser reutilizado em cópias; não recicla aqui
        }
        if (copies <= 1) return single
        val out = ByteArray(single.size * copies)
        for (i in 0 until copies) {
            System.arraycopy(single, 0, out, i * single.size, single.size)
        }
        return out
    }

    fun buildTsplLabel(product: Product, settings: StoreSettings, copies: Int = 1): ByteArray {
        val label = LabelRenderer.render(product, settings)
        val wMm = label.widthMm.toInt().coerceIn(20, 100)
        val hMm = label.heightMm.toInt().coerceIn(15, 120)
        val single = commandTspl(label.bitmap, wMm, hMm, settings.printSpeed)
        if (copies <= 1) return single
        val out = ByteArray(single.size * copies)
        for (i in 0 until copies) System.arraycopy(single, 0, out, i * single.size, single.size)
        return out
    }

    fun buildTest(storeName: String): ByteArray {
        val settings = StoreSettings(
            storeName = storeName,
            etiquetaWmm = 50,
            etiquetaHmm = 30,
            etiquetaOrientacao = "horizontal"
        )
        val product = Product(
            id = "test",
            nome = "Teste impressao",
            categoria = "Calça",
            tamanho = "M",
            cor = "Preto",
            venda = 0.0,
            barcode = "7891985141459"
        )
        return buildLabel(product, settings, 1)
    }

    private fun commandEscPos(src: Bitmap, wMm: Int, hMm: Int, feedMm: Int): ByteArray {
        val mono = toMono(src, wMm, hMm)
        val width = mono.width
        val height = mono.height
        val parts = mutableListOf<ByteArray>()

        parts += byteArrayOf(ESC.toByte(), 0x40)

        for (y0 in 0 until height step BAND) {
            val bandH = minOf(BAND, height - y0)
            val data = ByteArray(width * 3)
            for (x in 0 until width) {
                for (bytePos in 0 until 3) {
                    var v = 0
                    for (bit in 0 until 8) {
                        val y = y0 + bytePos * 8 + bit
                        if (y < y0 + bandH && y < height && mono.black(x, y)) {
                            v = v or (0x80 shr bit)
                        }
                    }
                    data[x * 3 + bytePos] = v.toByte()
                }
            }
            val nL = width and 0xFF
            val nH = (width shr 8) and 0xFF
            parts += byteArrayOf(ESC.toByte(), 0x2A, 33, nL.toByte(), nH.toByte())
            parts += data
            parts += byteArrayOf(ESC.toByte(), 0x4A, bandH.toByte())
        }

        var rest = (feedMm * DOTS_PER_MM).coerceIn(0, 400)
        while (rest > 0) {
            val step = rest.coerceAtMost(200)
            parts += byteArrayOf(ESC.toByte(), 0x4A, step.toByte())
            rest -= step
        }
        parts += byteArrayOf(0x0A)

        return concat(parts)
    }

    private fun commandTspl(src: Bitmap, wMm: Int, hMm: Int, speed: Int = 4): ByteArray {
        val mono = toMono(src, wMm, hMm)
        val bytesPerLine = mono.width / 8
        val bitmap = ByteArray(bytesPerLine * mono.height)
        for (y in 0 until mono.height) {
            for (x in 0 until mono.width) {
                if (mono.black(x, y)) {
                    bitmap[y * bytesPerLine + (x shr 3)] =
                        (bitmap[y * bytesPerLine + (x shr 3)].toInt() or (0x80 shr (x % 8))).toByte()
                }
            }
        }
        val speedOk = speed.coerceIn(1, 8)
        val header = """
            SIZE $wMm mm,$hMm mm
            GAP 2 mm,0 mm
            SPEED $speedOk
            DIRECTION 1
            REFERENCE 0,0
            CLS
            BITMAP 0,0,$bytesPerLine,${mono.height},0,
        """.trimIndent().replace("\n", "\r\n") + "\r\n"
        val footer = "\r\nPRINT 1,1\r\n"
        val h = header.toByteArray(Charsets.US_ASCII)
        val f = footer.toByteArray(Charsets.US_ASCII)
        return concat(listOf(h, bitmap, f))
    }

    private data class Mono(val width: Int, val height: Int, private val pixels: BooleanArray) {
        fun black(x: Int, y: Int): Boolean = pixels[y * width + x]
    }

    private fun toMono(src: Bitmap, wMm: Int, hMm: Int): Mono {
        val larguraPontos = (maxOf(8, (wMm * DOTS_PER_MM / 8) * 8))
        val alturaPontos = maxOf(8, hMm * DOTS_PER_MM)
        val scaled = if (src.width == larguraPontos && src.height == alturaPontos) {
            src
        } else {
            Bitmap.createScaledBitmap(src, larguraPontos, alturaPontos, true)
        }
        // Leitura em lote (bem mais rápido que getPixel por pixel)
        val argb = IntArray(larguraPontos * alturaPontos)
        scaled.getPixels(argb, 0, larguraPontos, 0, 0, larguraPontos, alturaPontos)
        val pixels = BooleanArray(larguraPontos * alturaPontos)
        for (i in argb.indices) {
            val c = argb[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            pixels[i] = (r * 299 + g * 587 + b * 114) < 170_000
        }
        if (scaled !== src) scaled.recycle()
        return Mono(larguraPontos, alturaPontos, pixels)
    }

    private fun concat(parts: List<ByteArray>): ByteArray {
        val total = parts.sumOf { it.size }
        val out = ByteArray(total)
        var o = 0
        for (p in parts) {
            System.arraycopy(p, 0, out, o, p.size)
            o += p.size
        }
        return out
    }
}
