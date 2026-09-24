package com.shalom.slmsys.cloud

import com.shalom.slmsys.data.Customer
import com.shalom.slmsys.data.Product
import com.shalom.slmsys.data.StoreSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente do mesmo Google Apps Script do index.html.
 * URL EXATA do HTML (não alterar o ID do deployment).
 */
object CloudApi {

    // Valor padrão (mesmo do index.html). Usado quando o aparelho ainda não
    // configurou uma URL própria na tela de login.
    const val DEFAULT_APPSCRIPT_URL =
        "https://script.google.com/macros/s/AKfycbxz3bQjCtYPCOzgfqGwIg4ltM5elzlz7fm9-13Zpl6VwClLILEDa9NImMTWtY_1MfNV/exec"

    /** URL do servidor efetivamente usada nas chamadas. Configurável pelo usuário
     * na tela de login (uma vez por aparelho — [Repository.saveServerUrl] grava
     * no banco local e atualiza este valor). Começa com o padrão até o app
     * carregar o que estiver salvo. */
    @Volatile
    var baseUrl: String = DEFAULT_APPSCRIPT_URL

    private const val TIMEOUT_MS = 25_000

    data class CloudResult(
        val ok: Boolean,
        val error: String? = null,
        val version: Long = 0,
        val products: List<Product> = emptyList(),
        val customers: List<Customer> = emptyList(),
        val settings: StoreSettings = StoreSettings(),
        val categories: List<String> = emptyList(),
        val productNames: List<String> = emptyList(),
        val sizes: List<String> = emptyList(),
        val colors: List<String> = emptyList(),
        val rawDataJson: String = "{}",
        val labelModelNames: List<String> = emptyList(),
        val defaultLabelModel: String = ""
    )

    data class SaveResult(
        val ok: Boolean,
        val error: String? = null,
        val version: Long = 0,
        val conflict: Boolean = false,
        val rawDataJson: String? = null
    )

    data class AccessRequestResult(
        val ok: Boolean,
        val error: String? = null,
        val requestId: String? = null,
        val desativado: Boolean = false
    )

    data class AccessStatusResult(
        val ok: Boolean,
        val status: String = "",
        val deviceToken: String? = null,
        val error: String? = null
    )

    suspend fun load(key: String = "", deviceToken: String = ""): CloudResult =
        withContext(Dispatchers.IO) {
            try {
                val params = mutableListOf<String>()
                when {
                    key.isNotBlank() -> params += "key=" + URLEncoder.encode(key.trim(), "UTF-8")
                    deviceToken.isNotBlank() -> params += "deviceToken=" + URLEncoder.encode(deviceToken.trim(), "UTF-8")
                    else -> return@withContext CloudResult(ok = false, error = "Informe a chave de acesso")
                }
                params += "_=" + System.currentTimeMillis()
                val text = httpGet("$baseUrl?${params.joinToString("&")}")
                    ?: return@withContext CloudResult(ok = false, error = "Sem resposta do servidor")
                parseLoad(text)
            } catch (e: Exception) {
                CloudResult(ok = false, error = e.message ?: "Falha de conexão")
            }
        }

    suspend fun save(
        key: String,
        deviceToken: String,
        version: Long,
        rawDataJson: String
    ): SaveResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("version", version)
                put("data", JSONObject(rawDataJson))
                put("key", key)
                put("deviceToken", deviceToken)
            }
            val text = httpPost(body.toString())
                ?: return@withContext SaveResult(ok = false, error = "Sem resposta ao salvar")
            val json = try {
                JSONObject(text)
            } catch (_: Exception) {
                return@withContext SaveResult(ok = false, error = "Resposta inválida ao salvar: ${text.take(80)}")
            }
            if (json.optBoolean("ok", true) == false) {
                return@withContext SaveResult(
                    ok = false,
                    error = json.optString("error", "Falha ao salvar"),
                    conflict = json.optBoolean("conflict", false),
                    version = json.optLong("version", version),
                    rawDataJson = json.optJSONObject("data")?.toString()
                )
            }
            SaveResult(ok = true, version = json.optLong("version", version + 1))
        } catch (e: Exception) {
            SaveResult(ok = false, error = e.message ?: "Falha ao salvar no servidor")
        }
    }

    suspend fun requestDeviceAccess(deviceId: String, deviceName: String): AccessRequestResult =
        withContext(Dispatchers.IO) {
            try {
                val params = listOf(
                    "acao=solicitarAcessoDispositivo",
                    "deviceId=" + URLEncoder.encode(deviceId, "UTF-8"),
                    "deviceName=" + URLEncoder.encode(deviceName.take(180), "UTF-8"),
                    "locale=pt-BR",
                    "timezone=America/Sao_Paulo",
                    "city=Sao%20Paulo",
                    "_=" + System.currentTimeMillis()
                )
                val text = httpGet("$baseUrl?${params.joinToString("&")}")
                    ?: return@withContext AccessRequestResult(ok = false, error = "Sem resposta")
                val json = try {
                    JSONObject(text)
                } catch (_: Exception) {
                    return@withContext AccessRequestResult(ok = false, error = "Resposta inválida")
                }
                if (json.optBoolean("desativado", false)) {
                    return@withContext AccessRequestResult(ok = false, desativado = true, error = "Pedidos desativados")
                }
                if (!json.optBoolean("ok", false)) {
                    return@withContext AccessRequestResult(
                        ok = false,
                        error = json.optString("error", "Não foi possível enviar a solicitação")
                    )
                }
                AccessRequestResult(ok = true, requestId = json.optString("requestId").ifBlank { null })
            } catch (e: Exception) {
                AccessRequestResult(ok = false, error = e.message ?: "Falha de conexão")
            }
        }

    suspend fun checkAccessRequest(requestId: String, deviceId: String): AccessStatusResult =
        withContext(Dispatchers.IO) {
            try {
                val params = listOf(
                    "acao=consultarSolicitacaoAcesso",
                    "requestId=" + URLEncoder.encode(requestId, "UTF-8"),
                    "deviceId=" + URLEncoder.encode(deviceId, "UTF-8"),
                    "_=" + System.currentTimeMillis()
                )
                val text = httpGet("$baseUrl?${params.joinToString("&")}")
                    ?: return@withContext AccessStatusResult(ok = false, error = "Sem resposta")
                val json = try {
                    JSONObject(text)
                } catch (_: Exception) {
                    return@withContext AccessStatusResult(ok = false, error = "Resposta inválida")
                }
                if (!json.optBoolean("ok", false)) {
                    return@withContext AccessStatusResult(ok = false, error = json.optString("error", "Erro"))
                }
                AccessStatusResult(
                    ok = true,
                    status = json.optString("status", "pendente"),
                    deviceToken = json.optString("deviceToken").ifBlank { null }
                )
            } catch (e: Exception) {
                AccessStatusResult(ok = false, error = e.message)
            }
        }

    private fun httpGet(urlStr: String): String? {
        var current = urlStr
        // Apps Script redireciona; seguimos até 5 hops
        repeat(5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "SLMsys-Android/1.0")
                setRequestProperty("Accept", "application/json,text/plain,*/*")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                if (loc.isNullOrBlank()) return null
                current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
                // Não chama disconnect(): drena o corpo (se houver) e deixa a JVM
                // devolver a conexão pro pool de keep-alive quando possível, em vez
                // de forçar o fechamento do socket a cada hop/chamada.
                try {
                    conn.inputStream?.use { it.readBytes() }
                } catch (_: Exception) { /* redirect sem corpo, ignora */ }
                return@repeat
            }
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty().ifBlank { null }
        }
        return null
    }

    private fun httpPost(body: String): String? {
        // POST no endpoint /exec; Apps Script pode devolver 302 — seguimos o redirect com GET do resultado
        val conn = (URL(baseUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "text/plain;charset=utf-8")
            setRequestProperty("User-Agent", "SLMsys-Android/1.0")
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        return stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty().ifBlank { null }
    }

    private fun parseLoad(text: String): CloudResult {
        if (text.isBlank()) return CloudResult(ok = false, error = "Resposta vazia do servidor")
        // Às vezes o Apps Script devolve HTML de login/erro
        if (text.trimStart().startsWith("<")) {
            return CloudResult(ok = false, error = "Servidor devolveu HTML (URL/chave incorreta?)")
        }
        val json = try {
            JSONObject(text)
        } catch (_: Exception) {
            return CloudResult(ok = false, error = "Resposta inválida: ${text.take(100)}")
        }
        if (json.optBoolean("ok", true) == false && json.has("error") && !json.has("data")) {
            return CloudResult(ok = false, error = json.optString("error", "Chave inválida ou sem permissão"))
        }
        val data = json.optJSONObject("data")
            ?: return CloudResult(ok = false, error = json.optString("error", "Sem dados do servidor"))
        val version = json.optLong("version", 0)
        val products = parseProducts(data.optJSONArray("products"))
        val customers = parseCustomers(data.optJSONArray("customers"))
        val settingsObj = data.optJSONObject("settings") ?: JSONObject()
        val settings = parseSettings(settingsObj)

        // Categorias e nomes pré-cadastrados no servidor (arrays do Drive)
        val categories = linkedSetOf<String>()
        data.optJSONArray("categories")?.let { arr ->
            for (i in 0 until arr.length()) {
                val n = when {
                    arr.optJSONObject(i) != null -> {
                        val o = arr.optJSONObject(i)
                        o.optString("nome").ifBlank { o.optString("name") }.ifBlank { o.optString("titulo") }
                    }
                    else -> arr.optString(i)
                }.trim()
                if (n.isNotEmpty()) categories += n
            }
        }
        products.map { it.categoria.trim() }.filter { it.isNotEmpty() }.forEach { categories += it }

        val productNames = linkedSetOf<String>()
        data.optJSONArray("productNames")?.let { arr ->
            for (i in 0 until arr.length()) {
                val n = when {
                    arr.optJSONObject(i) != null -> {
                        val o = arr.optJSONObject(i)
                        o.optString("nome").ifBlank { o.optString("name") }.ifBlank { o.optString("titulo") }
                    }
                    else -> arr.optString(i)
                }.trim()
                if (n.isNotEmpty()) productNames += n
            }
        }
        products.map { it.nome.trim() }.filter { it.isNotEmpty() }.forEach { productNames += it }

        val sizes = products.map { it.tamanho.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        val colors = products.map { it.cor.trim() }.filter { it.isNotEmpty() }.distinct().sorted()

        val modelNames = mutableListOf<String>()
        var defaultModel = ""
        settingsObj.optJSONArray("etiquetaModelos")?.let { arr ->
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val n = m.optString("nome").ifBlank {
                    m.optString("name").ifBlank { m.optString("id") }
                }.trim()
                if (n.isNotEmpty()) {
                    modelNames += n
                    if (defaultModel.isEmpty()) defaultModel = n
                }
            }
        }

        return CloudResult(
            ok = true,
            version = version,
            products = products,
            customers = customers,
            settings = settings,
            categories = categories.toList().sorted(),
            productNames = productNames.toList().sorted(),
            sizes = sizes,
            colors = colors,
            rawDataJson = data.toString(),
            labelModelNames = modelNames,
            defaultLabelModel = defaultModel
        )
    }

    private fun parseSettings(o: JSONObject): StoreSettings {
        val w = o.optInt("etiquetaLarguraMm", 0).takeIf { it > 0 } ?: 40
        val h = o.optInt("etiquetaAlturaMm", 0).takeIf { it > 0 } ?: 30
        val feed = if (o.has("etiquetaAvancoEscposMm")) o.optInt("etiquetaAvancoEscposMm", 6) else 6
        val protocol = o.optString("etiquetaProtocoloDireto").ifBlank { "escpos" }
        val showBars = if (o.has("etiquetaMostrarBarras")) o.optBoolean("etiquetaMostrarBarras", true) else true
        val modelsArr = o.optJSONArray("etiquetaModelos")
        val modelsJson = modelsArr?.toString() ?: "[]"
        // Se tem modelo custom, usa orientação do primeiro modelo ou settings
        var orient = o.optString("etiquetaOrientacao").ifBlank { "horizontal" }
        if (modelsArr != null && modelsArr.length() > 0) {
            val m0 = modelsArr.optJSONObject(0)
            if (m0 != null && m0.optString("orientacao").isNotBlank()) {
                orient = m0.optString("orientacao")
            }
        }
        // Padrão físico do site: 40×30 mm (cfg_etiquetaLarguraMm / AlturaMm).
        // No app esses valores são LOCAIS e preservados na sync (ver Repository).
        val finalW = if (w > 0) w.coerceIn(15, 100) else if (orient == "vertical") 30 else 40
        val finalH = if (h > 0) h.coerceIn(10, 100) else if (orient == "vertical") 50 else 30
        return StoreSettings(
            storeName = o.optString("nomeLoja").ifBlank { "Shalom Variedades" },
            contact = o.optString("contato"),
            storeLogo = o.optString("logo").ifBlank { o.optString("logoBase64") },
            etiquetaWmm = finalW,
            etiquetaHmm = finalH,
            feedMm = feed.coerceIn(0, 30),
            protocol = if (protocol == "tspl") "tspl" else "escpos",
            showBarcodeBars = showBars,
            labelModelsJson = modelsJson,
            etiquetaOrientacao = orient,
            // Mesmo campo/mesma regra do checkbox "cfg_etiquetaModoCustom" no HTML.
            etiquetaModoCustom = o.optBoolean("etiquetaModoCustom", false),
            etiquetaCor = o.optString("etiquetaCor").ifBlank { "#000000" },
            etiquetaQr = o.optString("etiquetaQr"),
            etiquetaMostrarQr = if (o.has("etiquetaMostrarQr")) o.optBoolean("etiquetaMostrarQr", true) else true
            // syncIntervalSec fica de fora de propósito: é config local do aparelho,
            // o Repository preserva o valor atual ao aplicar o resultado da nuvem.
        )
    }

    
    fun parseCustomers(arr: JSONArray?): List<Customer> {
        if (arr == null) return emptyList()
        val list = mutableListOf<Customer>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").ifBlank { "cli_${i}" }
            list += Customer(
                id = id,
                nome = o.optString("nome").ifBlank { o.optString("name") },
                telefone = o.optString("telefone").ifBlank { o.optString("phone") },
                cpf = o.optString("cpf"),
                endereco = o.optString("endereco").ifBlank { o.optString("address") },
                aniversario = o.optString("aniversario"),
                pontos = o.optInt("pontos", 0)
            )
        }
        return list
    }

fun parseProducts(arr: JSONArray?): List<Product> {
        if (arr == null) return emptyList()
        val list = mutableListOf<Product>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list += productFromJson(o)
        }
        return list
    }

    fun productFromJson(o: JSONObject): Product {
        val id = o.optString("id").ifBlank { "p${System.currentTimeMillis()}" }
        return Product(
            id = id,
            nome = o.optString("nome").ifBlank { o.optString("name") },
            categoria = o.optString("categoria").ifBlank { o.optString("category") },
            tamanho = o.optString("tamanho").ifBlank { o.optString("size") },
            cor = o.optString("cor").ifBlank { o.optString("color") },
            custo = o.optDouble("custo", o.optDouble("cost", 0.0)),
            venda = o.optDouble("venda", o.optDouble("price", 0.0)),
            estoque = o.optInt("estoque", o.optInt("stock", 0)),
            estoqueMin = o.optInt("estoqueMin", o.optInt("minStock", 3)),
            barcode = o.optString("barcode"),
            foto = o.optString("foto").ifBlank { o.optString("photo") },
            dataCadastro = o.optString("dataCadastro"),
            cadastradoPor = o.optString("cadastradoPor"),
            etiquetaModelo = o.optString("etiquetaModelo")
        )
    }

    fun productToJson(p: Product): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("nome", p.nome)
        put("categoria", p.categoria)
        put("tamanho", p.tamanho)
        put("cor", p.cor)
        put("custo", p.custo)
        put("venda", p.venda)
        put("estoque", p.estoque)
        put("estoqueMin", p.estoqueMin)
        put("barcode", p.barcode)
        put("foto", p.foto)
        put("dataCadastro", p.dataCadastro)
        put("cadastradoPor", p.cadastradoPor.ifBlank { "App Android" })
        put("etiquetaModelo", p.etiquetaModelo)
    }

    fun genBarcode(existing: Collection<String>): String {
        val set = existing.map { it.trim() }.filter { it.isNotEmpty() }.toHashSet()
        repeat(50) {
            val code = "789" + (1000000000L + (Math.random() * 8999999999L).toLong()).toString().take(10)
            if (code !in set) return code
        }
        return "789" + System.currentTimeMillis().toString().takeLast(10)
    }
}
