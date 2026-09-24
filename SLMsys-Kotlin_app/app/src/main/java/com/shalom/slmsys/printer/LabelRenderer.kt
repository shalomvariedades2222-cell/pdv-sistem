package com.shalom.slmsys.printer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.oned.Code128Writer
import com.google.zxing.qrcode.QRCodeWriter
import com.shalom.slmsys.data.Product
import com.shalom.slmsys.data.StoreSettings
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Replica o designer do site (`etiquetaModelos[].layout`).
 *
 * - Canvas do modelo custom: 50×30 mm (horizontal) ou 30×50 (vertical)
 * - x/y/w/h em **mm**
 * - fontSize em **pt**
 * - Ordem do array = ordem de desenho (camadas)
 * - Cada elemento é **clipado** na própria caixa (não invade o vizinho)
 * - Logo usa `settings.storeLogo` (settings.logo do servidor), não o nome do produto
 * - Depois escala o bitmap pro tamanho físico local (Impressora)
 */
object LabelRenderer {

    private const val DOTS_PER_MM = 8f
    /** 1 pt → dots @ 8 dpmm (site usa font-size:Npt) */
    private const val PT_TO_DOTS = 2.822f

    data class LabelBitmap(
        val bitmap: Bitmap,
        val widthMm: Float,
        val heightMm: Float
    )

    fun render(product: Product, settings: StoreSettings): LabelBitmap {
        val modelosArr = parseModelos(settings)
        if (modelosArr.length() == 0) {
            val w = (settings.etiquetaWmm.takeIf { it > 0 } ?: 40).toFloat()
            val h = (settings.etiquetaHmm.takeIf { it > 0 } ?: 30).toFloat()
            return LabelBitmap(renderSemModelo(w, h), w, h)
        }
        return renderFromServerModel(product, settings, modelosArr)
    }

    fun renderBitmap(product: Product, settings: StoreSettings): Bitmap =
        render(product, settings).bitmap

    fun sampleProduct(from: Product? = null): Product {
        if (from != null) return from
        return Product(
            id = "preview",
            nome = "Produto exemplo",
            categoria = "Categoria",
            tamanho = "M",
            cor = "Azul",
            venda = 29.90,
            barcode = "7891985141459",
            estoque = 1
        )
    }

    private fun parseModelos(settings: StoreSettings): JSONArray {
        // Preferência: layout editado só neste aparelho (nunca vai pro servidor)
        val raw = if (settings.useLocalLabel && settings.localLabelModelsJson.isNotBlank()) {
            settings.localLabelModelsJson
        } else {
            settings.labelModelsJson
        }
        return try {
            JSONArray(raw.ifBlank { "[]" })
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun pickModel(product: Product, modelosArr: JSONArray): JSONObject? {
        val pref = product.etiquetaModelo.ifBlank { null }
        if (pref != null && pref.startsWith("custom:")) {
            val id = pref.removePrefix("custom:")
            for (i in 0 until modelosArr.length()) {
                val m = modelosArr.optJSONObject(i) ?: continue
                if (m.optString("id") == id) return m
            }
        }
        if (modelosArr.length() > 0) return modelosArr.optJSONObject(0)
        return null
    }

    private fun renderFromServerModel(
        product: Product,
        settings: StoreSettings,
        modelosArr: JSONArray
    ): LabelBitmap {
        val escolhido = pickModel(product, modelosArr)
        val orient = escolhido?.optString("orientacao")?.ifBlank { null }
            ?: settings.etiquetaOrientacao.ifBlank { "horizontal" }

        // Canvas do designer no site: 50×30 (horizontal) ou 30×50 (vertical).
        // Modelos CUSTOM no site imprimem nesse tamanho — não no 40×30 da loja.
        val designW = if (orient == "vertical") 30f else 50f
        val designH = if (orient == "vertical") 50f else 30f

        // Tamanho físico: prioriza o que o usuário ajustou na tela Impressora;
        // se ainda for o padrão genérico 40×30 e o modelo é custom, usa o tamanho
        // do designer pra ficar igual ao preview do site.
        val userW = settings.etiquetaWmm.takeIf { it > 0 } ?: 40
        val userH = settings.etiquetaHmm.takeIf { it > 0 } ?: 30
        val physW: Float
        val physH: Float
        if (userW == 40 && userH == 30) {
            // padrão do app → igual ao site (custom = design)
            physW = designW
            physH = designH
        } else {
            physW = userW.toFloat()
            physH = userH.toFloat()
        }

        val layout = escolhido?.optJSONArray("layout")
            ?: escolhido?.optJSONArray("elementos")
            ?: escolhido?.optJSONArray("elements")

        if (layout == null || layout.length() == 0) {
            return LabelBitmap(renderSemModelo(physW, physH), physW, physH)
        }

        // Desenha nas coordenadas mm do designer (ordem do array = camadas do site)
        val designBmp = renderFromLayout(product, settings, layout, designW, designH)

        if (designW == physW && designH == physH) {
            return LabelBitmap(designBmp, physW, physH)
        }
        val outW = (physW * DOTS_PER_MM).roundToInt().coerceAtLeast(80)
        val outH = (physH * DOTS_PER_MM).roundToInt().coerceAtLeast(80)
        val scaled = Bitmap.createScaledBitmap(designBmp, outW, outH, true)
        if (scaled !== designBmp) designBmp.recycle()
        return LabelBitmap(scaled, physW, physH)
    }

    private fun renderFromLayout(
        product: Product,
        settings: StoreSettings,
        layout: JSONArray,
        wMm: Float,
        hMm: Float
    ): Bitmap {
        val width = (wMm * DOTS_PER_MM).roundToInt().coerceAtLeast(80)
        val height = (hMm * DOTS_PER_MM).roundToInt().coerceAtLeast(80)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        // Ordem do array = ordem de camadas do designer do site (não reordenar)
        for (i in 0 until layout.length()) {
            val el = layout.optJSONObject(i) ?: continue
            if (el.optBoolean("hidden", false) || el.optBoolean("oculto", false)) continue
            drawElement(canvas, el, product, settings)
        }
        return bmp
    }

    private fun drawElement(
        canvas: Canvas,
        el: JSONObject,
        product: Product,
        settings: StoreSettings
    ) {
        val type = el.optString("type").ifBlank {
            el.optString("tipo").ifBlank { el.optString("kind") }
        }.lowercase(Locale.ROOT)

        val xMm = el.num("x", "left", "posX")
        val yMm = el.num("y", "top", "posY")
        val wMm = el.num("w", "width", "largura").takeIf { it > 0 } ?: 10.0
        val hMm = el.num("h", "height", "altura").takeIf { it > 0 } ?: 5.0

        val x = (xMm * DOTS_PER_MM).toFloat()
        val y = (yMm * DOTS_PER_MM).toFloat()
        val w = (wMm * DOTS_PER_MM).toFloat().coerceAtLeast(1f)
        val h = (hMm * DOTS_PER_MM).toFloat().coerceAtLeast(1f)

        val fontPt = el.num("fontSize", "fonte", "size", "font").takeIf { it > 0 } ?: 8.0
        val textSize = (fontPt * PT_TO_DOTS).toFloat().coerceIn(6f, 200f)

        val bold = el.optBoolean("bold", false) ||
            el.optBoolean("negrito", false) ||
            el.optString("fontWeight").equals("bold", true)

        val color = parseColor(
            el.optString("color").ifBlank {
                el.optString("cor").ifBlank { el.optString("fill", "#000000") }
            }
        )
        val align = el.optString("align").ifBlank {
            el.optString("alinhamento").ifBlank { el.optString("textAlign", "left") }
        }.lowercase(Locale.ROOT)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = textSize
            this.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.textAlign = when {
                align.startsWith("c") || align == "middle" -> Paint.Align.CENTER
                align.startsWith("r") || align == "end" -> Paint.Align.RIGHT
                else -> Paint.Align.LEFT
            }
        }

        // Clip: nada deste elemento vaza pra cima do vizinho
        canvas.save()
        canvas.clipRect(x, y, x + w, y + h)

        try {
            when (type) {
                "nome", "name", "produto", "product" ->
                    drawTextInBox(canvas, product.nome, x, y, w, h, paint)

                "preco", "preço", "price", "precovenda", "venda" ->
                    drawPrice(canvas, product.venda, x, y, w, h, paint)

                "categoria", "category" ->
                    drawTextInBox(canvas, product.categoria, x, y, w, h, paint)

                "tamanho", "size", "tam" ->
                    drawTextInBox(canvas, product.tamanho, x, y, w, h, paint)

                "cor", "color", "colour" ->
                    drawTextInBox(canvas, product.cor, x, y, w, h, paint)

                "codigo", "código", "barcode", "codigobarras", "ean" ->
                    drawTextInBox(canvas, product.barcode, x, y, w, h, paint)

                "nomeloja", "nome_loja", "store" ->
                    drawTextInBox(
                        canvas,
                        settings.storeName.ifBlank { "Loja" },
                        x, y, w, h, paint
                    )

                "texto", "text", "label", "simbolo", "símbolo", "symbol" ->
                    drawTextInBox(canvas, elementText(el), x, y, w, h, paint)

                "logo" -> drawLogo(canvas, el, settings, x, y, w, h, paint)

                "linha", "line", "hr" -> {
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(x, y, x + w, y + h.coerceAtLeast(1.2f), paint)
                }

                "retangulo", "rectangle", "rect", "box", "borda" -> {
                    val radius = (el.num("raio", "radius", "rx") * DOTS_PER_MM).toFloat()
                    val filled = el.optBoolean("preenchido", false) || el.optBoolean("filled", false)
                    val rect = RectF(x, y, x + w, y + h)
                    if (filled) {
                        paint.style = Paint.Style.FILL
                        canvas.drawRoundRect(rect, radius, radius, paint)
                    } else {
                        paint.style = Paint.Style.STROKE
                        val bw = (el.num("larguraBorda", "borderWidth", "strokeWidth")
                            .takeIf { it > 0 } ?: 0.4) * DOTS_PER_MM
                        paint.strokeWidth = bw.toFloat().coerceAtLeast(1f)
                        paint.color = parseColor(
                            el.optString("corBorda").ifBlank {
                                el.optString("borderColor").ifBlank { el.optString("color", "#000000") }
                            }
                        )
                        canvas.drawRoundRect(rect, radius, radius, paint)
                    }
                }

                "qrcode", "qr", "qr_code", "qrloja", "qrproduto" -> {
                    val data = resolveQrData(el, type, product, settings)
                    drawQrPlain(canvas, data, x, y, w, h)
                }

                "barras", "barcodebars", "code128", "barrascodigo" -> {
                    if (settings.showBarcodeBars && product.barcode.isNotBlank()) {
                        val showNum = el.optBoolean("mostrarNumero", false) ||
                            el.optBoolean("showNumber", false)
                        val larguraPct = el.num("larguraPct", "barWidthPct").takeIf { it > 0 } ?: 100.0
                        val barW = (w * (larguraPct / 100.0)).toFloat().coerceAtLeast(w * 0.5f)
                        val barX = x + (w - barW) / 2f
                        drawBarcode(canvas, product.barcode, barX, y, barW, h, showNum)
                    }
                }

                "infocode" -> {
                    if (el.optString("formatoInfo").equals("qr", true)) {
                        val txt = listOf(product.categoria, product.tamanho, formatBRL(product.venda))
                            .filter { it.isNotBlank() }.joinToString(" ")
                        drawQrPlain(canvas, txt.ifBlank { product.barcode }, x, y, w, h)
                    } else if (product.barcode.isNotBlank()) {
                        drawBarcode(
                            canvas, product.barcode, x, y, w, h,
                            el.optBoolean("mostrarNumero", false)
                        )
                    }
                }

                "foto" -> {
                    // Foto do produto (servidor) — object-fit: cover como no site
                    val src = product.foto.trim().ifBlank { imageSrcStrict(el) }
                    if (src.isNotBlank()) drawImageFromSrc(canvas, src, x, y, w, h, cover = true)
                }
                "imagem", "image", "img", "simboloimg", "icon" -> {
                    val src = imageSrcStrict(el)
                    if (src.isNotBlank()) drawImageFromSrc(canvas, src, x, y, w, h, cover = true)
                }

                else -> {
                    val src = imageSrcStrict(el)
                    if (src.isNotBlank()) {
                        drawImageFromSrc(canvas, src, x, y, w, h)
                    } else {
                        val t = elementText(el)
                        if (t.isNotBlank()) drawTextInBox(canvas, t, x, y, w, h, paint)
                    }
                }
            }
        } finally {
            canvas.restore()
        }
    }

    /**
     * Logo da loja: prioridade = settings.storeLogo (servidor) → src do elemento.
     * Nunca usa o nome do produto. Fallback = nome da loja **dentro da caixa**.
     */
    private fun drawLogo(
        canvas: Canvas,
        el: JSONObject,
        settings: StoreSettings,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        paint: Paint
    ) {
        val fromSettings = settings.storeLogo.trim()
        val fromEl = imageSrcStrict(el)
        val src = when {
            isLikelyImageData(fromSettings) -> fromSettings
            isLikelyImageData(fromEl) -> fromEl
            else -> ""
        }
        if (src.isNotBlank() && drawImageFromSrc(canvas, src, x, y, w, h)) {
            return
        }
        // Fallback: só o nome da loja, clipado na caixa do logo
        val namePaint = Paint(paint).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = min(paint.textSize, h * 0.55f).coerceAtLeast(8f)
        }
        drawTextInBox(
            canvas,
            settings.storeName.ifBlank { "Loja" },
            x, y, w, h, namePaint
        )
    }

    private fun resolveQrData(
        el: JSONObject,
        type: String,
        product: Product,
        settings: StoreSettings
    ): String {
        val explicit = el.optString("content").ifBlank {
            el.optString("value").ifBlank {
                el.optString("texto").ifBlank {
                    el.optString("url").ifBlank { el.optString("data") }
                }
            }
        }.trim()
        if (explicit.isNotBlank() && !isLikelyImageData(explicit)) return explicit

        val field = el.optString("campo").ifBlank {
            el.optString("field").ifBlank { el.optString("bind") }
        }.lowercase(Locale.ROOT)

        when {
            type.contains("loja") || field in listOf("loja", "store", "url", "link") ->
                return settings.etiquetaQr.ifBlank { product.barcode.ifBlank { product.id } }
            field in listOf("barcode", "codigo", "produto", "product") ->
                return product.barcode.ifBlank { product.id }
            field == "nome" -> return product.nome
            field in listOf("preco", "preço") -> return formatBRL(product.venda)
        }
        return product.barcode.ifBlank { settings.etiquetaQr.ifBlank { product.id } }
    }

    private fun elementText(el: JSONObject): String =
        el.optString("content").ifBlank {
            el.optString("texto").ifBlank {
                el.optString("value").ifBlank {
                    el.optString("label").ifBlank { el.optString("text") }
                }
            }
        }

    /** Só aceita campos que realmente parecem imagem (evita desenhar texto como "imagem"). */
    private fun imageSrcStrict(el: JSONObject): String {
        val candidates = listOf(
            el.optString("src"),
            el.optString("image"),
            el.optString("base64"),
            el.optString("data")
        )
        for (c in candidates) {
            if (isLikelyImageData(c)) return c.trim()
        }
        return ""
    }

    private fun isLikelyImageData(s: String): Boolean {
        val t = s.trim()
        if (t.length < 32) return false
        if (t.startsWith("data:image", ignoreCase = true)) return true
        if (t.startsWith("http://", true) || t.startsWith("https://", true)) return false // app offline
        // base64 puro longo
        val pure = if ("base64," in t) t.substringAfter("base64,") else t
        if (pure.length < 64) return false
        val sample = pure.take(80)
        return sample.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' || it.isWhitespace() }
    }

    private fun JSONObject.num(vararg keys: String): Double {
        for (k in keys) {
            if (has(k) && !isNull(k)) {
                val d = optDouble(k, Double.NaN)
                if (!d.isNaN()) return d
                val s = optString(k).replace(",", ".")
                    .filter { it.isDigit() || it == '.' || it == '-' }
                s.toDoubleOrNull()?.let { return it }
            }
        }
        return 0.0
    }

    private fun drawImageFromSrc(
        canvas: Canvas, src: String, x: Float, y: Float, w: Float, h: Float,
        cover: Boolean = false
    ): Boolean {
        return try {
            val pure = when {
                "base64," in src -> src.substringAfter("base64,")
                src.startsWith("data:") && "base64," !in src -> return false
                src.startsWith("data:") -> src.substringAfter("base64,")
                else -> src
            }
            val bytes = Base64.decode(pure, Base64.DEFAULT)
            if (bytes.isEmpty()) return false
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            if (cover) {
                // object-fit: cover — preenche a caixa (como foto no site)
                val scale = maxOf(w / bmp.width, h / bmp.height)
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                val dx = x + (w - dw) / 2f
                val dy = y + (h - dh) / 2f
                canvas.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), paint)
            } else {
                // object-fit: contain — logo etc.
                val scale = min(w / bmp.width, h / bmp.height)
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                val dx = x + (w - dw) / 2f
                val dy = y + (h - dh) / 2f
                canvas.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), paint)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun renderSemModelo(wMm: Float, hMm: Float): Bitmap {
        val width = (wMm * DOTS_PER_MM).roundToInt().coerceAtLeast(160)
        val height = (hMm * DOTS_PER_MM).roundToInt().coerceAtLeast(120)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(4f, 4f, width - 4f, height - 4f, border)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        drawTextInBox(
            canvas,
            "Cadastre um modelo no designer do site",
            8f, height / 2f - 18f, (width - 16f).toFloat(), 36f,
            text
        )
        return bmp
    }

    private fun drawPrice(
        canvas: Canvas,
        value: Double,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        base: Paint
    ) {
        val txt = formatBRL(value)
        val paint = Paint(base)
        var size = min(h * 0.85f, paint.textSize).coerceAtLeast(10f)
        paint.textSize = size
        while (size > 8f && paint.measureText(txt) > w * 0.98f) {
            size *= 0.92f
            paint.textSize = size
        }
        drawTextInBox(canvas, txt, x, y, w, h, paint)
    }

    private fun drawTextInBox(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        paint: Paint
    ) {
        if (text.isBlank() || w <= 1f || h <= 1f) return
        var size = paint.textSize
        val minSize = 5f
        while (size > minSize && paint.measureText(text) > w * 0.98f) {
            size *= 0.94f
            paint.textSize = size
        }
        if (paint.textSize > h * 0.95f) {
            paint.textSize = h * 0.9f
        }
        val fm = paint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val baseline = y + (h + textHeight) / 2f - fm.descent
        val tx = when (paint.textAlign) {
            Paint.Align.CENTER -> x + w / 2f
            Paint.Align.RIGHT -> x + w - 1f
            else -> x + 1f
        }
        canvas.drawText(text, tx, baseline, paint)
    }

    private fun drawQrPlain(canvas: Canvas, data: String, x: Float, y: Float, w: Float, h: Float) {
        if (data.isBlank()) return
        try {
            val size = min(w, h).toInt().coerceAtLeast(32)
            val hints = mapOf(
                EncodeHintType.MARGIN to 0,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size, hints)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (px in 0 until size) {
                for (py in 0 until size) {
                    bmp.setPixel(px, py, if (matrix[px, py]) Color.BLACK else Color.WHITE)
                }
            }
            val side = min(w, h)
            val dx = x + (w - side) / 2f
            val dy = y + (h - side) / 2f
            canvas.drawBitmap(bmp, null, RectF(dx, dy, dx + side, dy + side), null)
        } catch (_: Exception) {
            val p = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            canvas.drawRect(x, y, x + w, y + h, p)
        }
    }

    private fun drawBarcode(
        canvas: Canvas,
        code: String,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        showNumber: Boolean
    ) {
        if (code.isBlank()) return
        try {
            val numH = if (showNumber) (h * 0.26f).coerceAtLeast(10f) else 0f
            val bw = w.toInt().coerceAtLeast(40)
            val bh = (h - numH).toInt().coerceAtLeast(12)
            val matrix = Code128Writer().encode(code, BarcodeFormat.CODE_128, bw, bh)
            val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            for (px in 0 until bw) {
                for (py in 0 until bh) {
                    bmp.setPixel(px, py, if (matrix[px, py]) Color.BLACK else Color.WHITE)
                }
            }
            canvas.drawBitmap(bmp, null, RectF(x, y, x + w, y + h - numH), null)
            if (showNumber) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = (numH * 0.85f).coerceAtLeast(8f)
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                }
                canvas.drawText(code, x + w / 2f, y + h - 2f, p)
            }
        } catch (_: Exception) {
        }
    }

    private fun parseColor(hex: String): Int {
        return try {
            val h = hex.trim()
            when {
                h.startsWith("#") -> Color.parseColor(h)
                h.isBlank() -> Color.BLACK
                else -> Color.parseColor("#$h")
            }
        } catch (_: Exception) {
            Color.BLACK
        }
    }

    private fun formatBRL(value: Double): String {
        return try {
            NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)
        } catch (_: Exception) {
            "R$ %.2f".format(Locale.US, value)
        }
    }
}
