package com.shalom.slmsys.data

import android.content.Context
import android.provider.Settings
import com.shalom.slmsys.cloud.CloudApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class Repository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val dao = db.dao()

    val products: Flow<List<Product>> = dao.observeProducts()
    val customers: Flow<List<Customer>> = dao.observeCustomers()
    val categories: Flow<List<Category>> = dao.observeCategories()
    val movements: Flow<List<Movement>> = dao.observeMovements()
    val printJobs: Flow<List<PrintJob>> = dao.observePrintJobs()
    val settings: Flow<StoreSettings> = dao.observeSettings().map { it ?: StoreSettings() }
    /** Quantas ações feitas offline ainda não foram enviadas pro servidor. */
    val pendingCount: Flow<Int> = dao.observePendingCount()

    /** Resultado de um salvamento: se [offline] for true, ficou só neste aparelho
     * (sem internet ou o servidor recusou) e entrou na fila de sincronização. */
    data class ProductSaveResult(val product: Product, val offline: Boolean)
    data class CatalogSaveResult(val value: String, val offline: Boolean)
    data class CustomerSaveResult(val customer: Customer, val offline: Boolean)
    data class PendingFlushResult(val sent: Int, val remaining: Int, val lastError: String? = null)

    private val _productNames = MutableStateFlow<List<String>>(emptyList())
    private val _sizes = MutableStateFlow<List<String>>(emptyList())
    private val _colors = MutableStateFlow<List<String>>(emptyList())
    private val _labelModels = MutableStateFlow<List<String>>(emptyList())
    val productNames = _productNames.asStateFlow()
    val sizes = _sizes.asStateFlow()
    val colors = _colors.asStateFlow()
    val labelModels = _labelModels.asStateFlow()

    fun deviceId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        return "android-$androidId"
    }

    fun deviceName(): String = "Android SLMsys"

    suspend fun getAuth(): AuthState = dao.getAuth() ?: AuthState()

    suspend fun saveAuth(
        key: String = "",
        deviceToken: String = "",
        version: Long = 0,
        rawDataJson: String = "{}"
    ) {
        val cur = getAuth()
        dao.upsertAuth(
            AuthState(
                accessKey = key.ifBlank { cur.accessKey },
                deviceToken = deviceToken.ifBlank { cur.deviceToken },
                driveVersion = if (version > 0) version else cur.driveVersion,
                rawDataJson = if (rawDataJson != "{}") rawDataJson else cur.rawDataJson,
                serverUrl = cur.serverUrl
            )
        )
    }

    suspend fun clearAuth() {
        // preserva a URL do servidor: ela é config do aparelho (definida uma vez),
        // não deve ser perdida ao fazer logout / trocar de chave.
        val cur = getAuth()
        dao.upsertAuth(AuthState(serverUrl = cur.serverUrl))
        _productNames.value = emptyList()
        _sizes.value = emptyList()
        _colors.value = emptyList()
        _labelModels.value = emptyList()
    }

    /** URL do servidor configurada neste aparelho (ou o padrão embutido, se
     * o usuário nunca alterou). */
    suspend fun getServerUrl(): String {
        val stored = getAuth().serverUrl
        return stored.ifBlank { CloudApi.DEFAULT_APPSCRIPT_URL }
    }

    /** Carrega em [CloudApi.baseUrl] a URL salva no banco. Chamado uma vez ao
     * abrir o app, antes de qualquer chamada de rede. */
    suspend fun applyStoredServerUrl() {
        CloudApi.baseUrl = getServerUrl()
    }

    /**
     * Reconstrói nomes e categorias a partir do cache local + lista pronta
     * (`SLM_PECAS_PADRAO`), pra o formulário de cadastro já ter as sugestões
     * mesmo antes/sem uma sync nova.
     */
    suspend fun reloadCatalogFromLocal() {
        val serverNames = mutableListOf<String>()
        val serverCats = mutableListOf<String>()
        try {
            val auth = getAuth()
            if (auth.rawDataJson.isNotBlank() && auth.rawDataJson != "{}") {
                val data = JSONObject(auth.rawDataJson)
                fun readNames(key: String, into: MutableList<String>) {
                    data.optJSONArray(key)?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val n = when {
                                arr.optJSONObject(i) != null -> {
                                    val o = arr.optJSONObject(i)
                                    o.optString("nome").ifBlank { o.optString("name") }
                                }
                                else -> arr.optString(i)
                            }.trim()
                            if (n.isNotEmpty()) into += n
                        }
                    }
                }
                readNames("productNames", serverNames)
                readNames("categories", serverCats)
                data.optJSONArray("products")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        o.optString("nome").trim().takeIf { it.isNotEmpty() }?.let { serverNames += it }
                        o.optString("categoria").trim().takeIf { it.isNotEmpty() }?.let { serverCats += it }
                    }
                }
            }
        } catch (_: Exception) { }
        val mergedNames = CatalogDefaults.mergeWith(serverNames)
        val mergedCats = CatalogDefaults.mergeWith(serverCats)
        _productNames.value = mergedNames
        dao.clearCategories()
        mergedCats.forEachIndexed { i, name ->
            dao.upsertCategory(Category(id = "c$i", name = name))
        }
    }

    /**
     * Grava a URL do servidor escolhida pelo usuário na tela de login.
     * Feito uma única vez por aparelho: fica salvo no banco local e é
     * reaplicado sozinho nas próximas aberturas do app (ver [applyStoredServerUrl]).
     * Campo em branco = volta a usar o padrão embutido no app.
     */
    suspend fun saveServerUrl(url: String) {
        val trimmed = url.trim()
        val cur = getAuth()
        dao.upsertAuth(cur.copy(serverUrl = trimmed))
        CloudApi.baseUrl = trimmed.ifBlank { CloudApi.DEFAULT_APPSCRIPT_URL }
    }

    suspend fun syncFromCloud(key: String = "", deviceToken: String = ""): Result<CloudApi.CloudResult> {
        val auth = getAuth()
        val k = key.ifBlank { auth.accessKey }
        val t = deviceToken.ifBlank { auth.deviceToken }
        val result = CloudApi.load(k, t)
        if (!result.ok) return Result.failure(Exception(result.error ?: "Falha ao sincronizar"))

        applyCloudResult(result, k, t)
        return Result.success(result)
    }

    private suspend fun applyCloudResult(result: CloudApi.CloudResult, key: String, deviceToken: String) {
        dao.clearProducts()
        if (result.products.isNotEmpty()) dao.upsertProducts(result.products)
        dao.clearCustomers()
        if (result.customers.isNotEmpty()) dao.upsertCustomers(result.customers)
        // categorias preenchidas abaixo (servidor + lista pronta)
        // Config LOCAL do aparelho (não mexe no site): tamanho físico da etiqueta,
        // avanço, protocolo, velocidade, sync automática. O layout (labelModelsJson)
        // continua vindo do servidor.
        val previous = dao.getSettingsOnce()
        dao.upsertSettings(
            result.settings.copy(
                syncIntervalSec = previous?.syncIntervalSec ?: result.settings.syncIntervalSec,
                autoSyncEnabled = previous?.autoSyncEnabled ?: result.settings.autoSyncEnabled,
                protocol = previous?.protocol ?: result.settings.protocol,
                printSpeed = previous?.printSpeed ?: result.settings.printSpeed,
                etiquetaWmm = previous?.etiquetaWmm ?: result.settings.etiquetaWmm,
                etiquetaHmm = previous?.etiquetaHmm ?: result.settings.etiquetaHmm,
                feedMm = previous?.feedMm ?: result.settings.feedMm,
                // Layout editado só neste aparelho — nunca sobrescreve na sync
                localLabelModelsJson = previous?.localLabelModelsJson ?: "",
                useLocalLabel = previous?.useLocalLabel ?: false
            )
        )
        // Categorias e nomes: servidor + lista pronta do site (SLM_PECAS_PADRAO)
        val mergedNames = CatalogDefaults.mergeWith(result.productNames)
        val mergedCats = CatalogDefaults.mergeWith(result.categories)
        dao.clearCategories()
        mergedCats.forEachIndexed { i, name ->
            dao.upsertCategory(Category(id = "c$i", name = name))
        }
        _productNames.value = mergedNames
        _sizes.value = result.sizes
        _colors.value = result.colors
        _labelModels.value = result.labelModelNames
        if (key.isNotBlank() || deviceToken.isNotBlank()) {
            saveAuth(key, deviceToken, result.version, result.rawDataJson)
        }
    }

    suspend fun createProductAndSync(draft: ProductDraft): Result<ProductSaveResult> {
        val prepared = prepareNewProduct(draft)
            ?: return Result.failure(Exception("Não conectado ao servidor"))
        return persistNewProduct(prepared, draft)
    }

    /**
     * Monta o produto (com código de barras já definido) SEM tocar no servidor —
     * usado para poder imprimir a etiqueta antes de salvar, igual o fluxo pedido:
     * primeiro imprime, depois salva no servidor.
     */
    suspend fun prepareNewProduct(draft: ProductDraft): Product? {
        val auth = getAuth()
        if (auth.accessKey.isBlank() && auth.deviceToken.isBlank()) return null
        if (auth.rawDataJson.isBlank() || auth.rawDataJson == "{}") {
            val load = syncFromCloud()
            if (load.isFailure) return null
        }
        val auth2 = getAuth()
        val usedCodes = mutableSetOf<String>()
        try {
            val data = JSONObject(auth2.rawDataJson)
            val productsArr = data.optJSONArray("products")
            if (productsArr != null) {
                for (i in 0 until productsArr.length()) {
                    val bc = productsArr.optJSONObject(i)?.optString("barcode") ?: continue
                    if (bc.isNotBlank()) usedCodes += bc
                }
            }
        } catch (_: Exception) { /* cache inválido: segue sem checar colisão local */ }

        val barcode = draft.barcode.trim().ifEmpty { CloudApi.genBarcode(usedCodes) }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return Product(
            id = "prod_" + UUID.randomUUID().toString().replace("-", "").take(12),
            nome = draft.nome.trim(),
            categoria = draft.categoria.trim(),
            tamanho = draft.tamanho.trim(),
            cor = draft.cor.trim(),
            custo = draft.custo.replace(",", ".").filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0,
            venda = draft.venda.replace(",", ".").filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0,
            estoque = draft.estoque.toIntOrNull() ?: 1,
            estoqueMin = draft.estoqueMin.toIntOrNull() ?: 3,
            barcode = barcode,
            dataCadastro = today,
            cadastradoPor = "App Android"
        )
    }

    /**
     * Segunda etapa: grava o produto (já montado por [prepareNewProduct]).
     *
     * Sempre local-first: o produto entra no banco deste aparelho na hora
     * (funciona sem internet). Em seguida tenta mandar pro servidor; se não
     * conseguir (sem internet, servidor fora do ar etc.), a alteração fica
     * guardada na fila de pendências e é reenviada automaticamente quando a
     * conexão voltar — mas a peça já aparece salva e utilizável no aparelho.
     */
    suspend fun persistNewProduct(product: Product, @Suppress("UNUSED_PARAMETER") draft: ProductDraft): Result<ProductSaveResult> {
        dao.upsertProduct(product)
        if (product.nome.isNotBlank() && product.nome !in _productNames.value) {
            _productNames.value = (_productNames.value + product.nome).distinct().sorted()
        }
        if (product.categoria.isNotBlank()) {
            applyLocalCatalogAddition("categoria", product.categoria)
        }

        val push = pushProductToCloud(product)
        if (push.isSuccess) {
            return Result.success(ProductSaveResult(product, offline = false))
        }
        enqueuePending(
            type = "product",
            label = "Produto: ${product.nome} (${product.barcode})",
            payloadJson = CloudApi.productToJson(product).toString()
        )
        return Result.success(ProductSaveResult(product, offline = true))
    }

    /**
     * Edição de um produto já existente (tela de Estoque → editar peça).
     *
     * Antes esta função só gravava no banco local do aparelho e NUNCA chegava
     * a tocar no servidor — por isso um preço/nome editado no app não aparecia
     * no site nem em outro aparelho. Agora segue o mesmo padrão local-first dos
     * outros salvamentos: grava local na hora (funciona sem internet) e tenta
     * mandar pro servidor; se não conseguir, entra na fila de pendências e é
     * reenviada automaticamente quando a conexão voltar.
     */
    suspend fun updateProductAndSync(product: Product): Result<ProductSaveResult> {
        dao.upsertProduct(product)

        val push = pushProductUpdateToCloud(product)
        if (push.isSuccess) {
            return Result.success(ProductSaveResult(product, offline = false))
        }
        enqueuePending(
            type = "product_update",
            label = "Edição: ${product.nome} (${product.barcode})",
            payloadJson = CloudApi.productToJson(product).toString()
        )
        return Result.success(ProductSaveResult(product, offline = true))
    }

    /**
     * Igual [pushProductToCloud], mas para EDIÇÃO: se o produto já existe no
     * cache do servidor (mesmo id), substitui os dados em vez de ignorar —
     * [pushProductToCloud] pula produtos que já existem (ela é só para
     * cadastro novo) e por isso nunca serviria para levar uma edição ao site.
     */
    private suspend fun pushProductUpdateToCloud(product: Product): Result<Unit> {
        val auth2 = getAuth()
        if (auth2.rawDataJson.isBlank() || auth2.rawDataJson == "{}") {
            return Result.failure(Exception("Sem dados do servidor"))
        }
        val data = try {
            JSONObject(auth2.rawDataJson)
        } catch (_: Exception) {
            return Result.failure(Exception("Cache do servidor inválido — atualize o estoque"))
        }
        val productsArr = data.optJSONArray("products") ?: JSONArray().also { data.put("products", it) }

        var found = false
        for (i in 0 until productsArr.length()) {
            if (productsArr.optJSONObject(i)?.optString("id") == product.id) {
                productsArr.put(i, CloudApi.productToJson(product))
                found = true
                break
            }
        }
        if (!found) productsArr.put(CloudApi.productToJson(product))

        if (product.categoria.isNotBlank()) {
            val catsArr = data.optJSONArray("categories") ?: JSONArray().also { data.put("categories", it) }
            var catExists = false
            for (i in 0 until catsArr.length()) {
                if (catsArr.optJSONObject(i)?.optString("nome")?.equals(product.categoria, true) == true) {
                    catExists = true
                    break
                }
            }
            if (!catExists) {
                catsArr.put(JSONObject().put("id", "cat_" + UUID.randomUUID().toString().take(8)).put("nome", product.categoria))
            }
        }

        data.put("products", productsArr)
        val newRaw = data.toString()

        val save = CloudApi.save(auth2.accessKey, auth2.deviceToken, auth2.driveVersion, newRaw)
        if (!save.ok) {
            return Result.failure(Exception(save.error ?: "Falha ao salvar no servidor"))
        }
        saveAuth(auth2.accessKey, auth2.deviceToken, save.version, newRaw)
        return Result.success(Unit)
    }

    /** Só a parte de rede de [persistNewProduct] — reaproveitada também ao reenviar a fila de pendências. */
    private suspend fun pushProductToCloud(product: Product): Result<Unit> {
        val auth2 = getAuth()
        if (auth2.rawDataJson.isBlank() || auth2.rawDataJson == "{}") {
            return Result.failure(Exception("Sem dados do servidor"))
        }
        val data = try {
            JSONObject(auth2.rawDataJson)
        } catch (_: Exception) {
            return Result.failure(Exception("Cache do servidor inválido — atualize o estoque"))
        }
        val productsArr = data.optJSONArray("products") ?: JSONArray().also { data.put("products", it) }

        // evita duplicar se essa mesma peça já tiver ido pro servidor numa tentativa anterior
        var alreadyThere = false
        for (i in 0 until productsArr.length()) {
            if (productsArr.optJSONObject(i)?.optString("id") == product.id) {
                alreadyThere = true
                break
            }
        }
        if (!alreadyThere) productsArr.put(CloudApi.productToJson(product))

        // também registra nome no catálogo productNames se não existir
        val namesArr = data.optJSONArray("productNames") ?: JSONArray().also { data.put("productNames", it) }
        var nameExists = false
        for (i in 0 until namesArr.length()) {
            if (namesArr.optJSONObject(i)?.optString("nome")?.equals(product.nome, true) == true) {
                nameExists = true
                break
            }
        }
        if (!nameExists && product.nome.isNotBlank()) {
            namesArr.put(JSONObject().put("id", "pn_" + UUID.randomUUID().toString().take(8)).put("nome", product.nome))
        }

        // idem para a categoria — cadastro automático, igual ao picker do site
        if (product.categoria.isNotBlank()) {
            val catsArr = data.optJSONArray("categories") ?: JSONArray().also { data.put("categories", it) }
            var catExists = false
            for (i in 0 until catsArr.length()) {
                if (catsArr.optJSONObject(i)?.optString("nome")?.equals(product.categoria, true) == true) {
                    catExists = true
                    break
                }
            }
            if (!catExists) {
                catsArr.put(JSONObject().put("id", "cat_" + UUID.randomUUID().toString().take(8)).put("nome", product.categoria))
            }
        }

        data.put("products", productsArr)
        data.put("productNames", namesArr)
        val newRaw = data.toString()

        val save = CloudApi.save(auth2.accessKey, auth2.deviceToken, auth2.driveVersion, newRaw)
        if (!save.ok) {
            return Result.failure(Exception(save.error ?: "Falha ao salvar no servidor"))
        }
        saveAuth(auth2.accessKey, auth2.deviceToken, save.version, newRaw)
        return Result.success(Unit)
    }

    /**
     * Cadastro manual de categoria/nome novo direto na tela de cadastro de peça
     * (botão "Cadastrar categoria/nome" quando o texto digitado ainda não existe).
     * Salva no servidor na hora, igual o "picker" do site (pickerAddItem), pra ficar
     * disponível pra qualquer aparelho que sincronizar depois.
     */
    suspend fun registerCatalogEntry(type: String, rawValue: String): Result<CatalogSaveResult> {
        val value = rawValue.trim()
        if (value.isBlank()) return Result.failure(Exception("Valor vazio"))

        // local-first: já aparece na lista deste aparelho mesmo sem internet
        applyLocalCatalogAddition(type, value)

        val push = pushCatalogToCloud(type, value)
        if (push.isSuccess) {
            return Result.success(CatalogSaveResult(value, offline = false))
        }
        enqueuePending(
            type = "catalog",
            label = "${if (type == "categoria") "Categoria" else "Nome"}: $value",
            payloadJson = JSONObject().put("type", type).put("value", value).toString()
        )
        return Result.success(CatalogSaveResult(value, offline = true))
    }

    private suspend fun pushCatalogToCloud(type: String, value: String): Result<Unit> {
        val auth = getAuth()
        if (auth.rawDataJson.isBlank() || auth.rawDataJson == "{}") {
            val load = syncFromCloud()
            if (load.isFailure) return Result.failure(load.exceptionOrNull() ?: Exception("Sem dados do servidor"))
        }
        val auth2 = getAuth()
        val data = try {
            JSONObject(auth2.rawDataJson)
        } catch (_: Exception) {
            return Result.failure(Exception("Cache do servidor inválido"))
        }
        val key = if (type == "categoria") "categories" else "productNames"
        val arr = data.optJSONArray(key) ?: JSONArray().also { data.put(key, it) }
        for (i in 0 until arr.length()) {
            if (arr.optJSONObject(i)?.optString("nome")?.equals(value, true) == true) {
                return Result.success(Unit) // já existe no servidor
            }
        }
        arr.put(
            JSONObject()
                .put("id", (if (type == "categoria") "cat_" else "pn_") + UUID.randomUUID().toString().take(8))
                .put("nome", value)
        )
        data.put(key, arr)
        val newRaw = data.toString()
        val save = CloudApi.save(auth2.accessKey, auth2.deviceToken, auth2.driveVersion, newRaw)
        if (!save.ok) {
            return Result.failure(Exception(save.error ?: "Falha ao cadastrar no servidor"))
        }
        saveAuth(auth2.accessKey, auth2.deviceToken, save.version, newRaw)
        return Result.success(Unit)
    }

    private suspend fun applyLocalCatalogAddition(type: String, value: String) {
        if (type == "categoria") {
            // id determinístico a partir do nome: upsert vira "ligar se não existir",
            // sem precisar checar a lista atual (evita duplicar linha com id diferente).
            val id = "cat_" + value.trim().lowercase().hashCode()
            dao.upsertCategory(Category(id = id, name = value))
        } else {
            if (value !in _productNames.value) {
                _productNames.value = (_productNames.value + value).distinct().sorted()
            }
        }
    }

    /** Exclui do aparelho e do Drive (products + tombstone + trash). */
    suspend fun deleteProductAndSync(id: String): Result<Unit> {
        val p = dao.getProduct(id) ?: return Result.failure(Exception("Produto não encontrado"))
        val auth = getAuth()
        if (auth.rawDataJson.isBlank() || auth.rawDataJson == "{}") {
            // tenta sincronizar de novo
            val load = syncFromCloud()
            if (load.isFailure) {
                // exclui só local se não houver servidor
                dao.deleteProduct(id)
                return Result.failure(Exception("Excluído só no aparelho (sem cache do servidor)"))
            }
        }
        val auth2 = getAuth()
        if (auth2.rawDataJson.isBlank() || auth2.rawDataJson == "{}") {
            dao.deleteProduct(id)
            return Result.failure(Exception("Excluído só no aparelho"))
        }
        val data = try {
            JSONObject(auth2.rawDataJson)
        } catch (_: Exception) {
            dao.deleteProduct(id)
            return Result.failure(Exception("Cache inválido — excluído só no aparelho"))
        }
        val productsArr = data.optJSONArray("products") ?: JSONArray()
        val newArr = JSONArray()
        for (i in 0 until productsArr.length()) {
            val o = productsArr.optJSONObject(i) ?: continue
            if (o.optString("id") != id) newArr.put(o)
        }
        data.put("products", newArr)

        val deleted = data.optJSONObject("deleted") ?: JSONObject().also { data.put("deleted", it) }
        val delProducts = deleted.optJSONObject("products") ?: JSONObject().also { deleted.put("products", it) }
        delProducts.put(id, System.currentTimeMillis())

        val trash = data.optJSONArray("trashProducts") ?: JSONArray().also { data.put("trashProducts", it) }
        trash.put(
            JSONObject()
                .put("id", "trash_" + UUID.randomUUID().toString().take(8))
                .put("product", CloudApi.productToJson(p))
                .put("deletedAt", System.currentTimeMillis())
        )

        val newRaw = data.toString()
        val save = CloudApi.save(auth2.accessKey, auth2.deviceToken, auth2.driveVersion, newRaw)
        if (!save.ok) {
            dao.deleteProduct(id)
            return Result.failure(Exception((save.error ?: "Falha no servidor") + " — removido no aparelho"))
        }
        dao.deleteProduct(id)
        saveAuth(auth2.accessKey, auth2.deviceToken, save.version, newRaw)
        return Result.success(Unit)
    }

    suspend fun requestAccess(): Result<String> {
        val r = CloudApi.requestDeviceAccess(deviceId(), deviceName())
        if (!r.ok) return Result.failure(Exception(r.error ?: "Falha ao solicitar acesso"))
        val id = r.requestId ?: return Result.failure(Exception("Servidor não retornou requestId"))
        return Result.success(id)
    }

    suspend fun pollAccess(requestId: String): Result<CloudApi.AccessStatusResult> {
        val r = CloudApi.checkAccessRequest(requestId, deviceId())
        if (!r.ok) return Result.failure(Exception(r.error ?: "Erro ao consultar"))
        if (r.status == "aceito" && !r.deviceToken.isNullOrBlank()) {
            saveAuth(deviceToken = r.deviceToken)
            val sync = syncFromCloud(deviceToken = r.deviceToken)
            if (sync.isFailure) {
                return Result.success(r) // token ok mas sync falhou — caller trata
            }
        }
        return Result.success(r)
    }

    
    suspend fun createCustomerAndSync(draft: CustomerDraft): Result<CustomerSaveResult> {
        val customer = Customer(
            id = "cli_" + UUID.randomUUID().toString().replace("-", "").take(10),
            nome = draft.nome.trim(),
            telefone = draft.telefone.trim(),
            cpf = draft.cpf.trim(),
            endereco = draft.endereco.trim()
        )
        // local-first: cliente já fica disponível neste aparelho mesmo sem internet
        dao.upsertCustomer(customer)

        val push = pushCustomerToCloud(customer)
        if (push.isSuccess) {
            return Result.success(CustomerSaveResult(customer, offline = false))
        }
        enqueuePending(
            type = "customer",
            label = "Cliente: ${customer.nome}",
            payloadJson = JSONObject().apply {
                put("id", customer.id)
                put("nome", customer.nome)
                put("telefone", customer.telefone)
                put("cpf", customer.cpf)
                put("endereco", customer.endereco)
            }.toString()
        )
        return Result.success(CustomerSaveResult(customer, offline = true))
    }

    private suspend fun pushCustomerToCloud(customer: Customer): Result<Unit> {
        val auth = getAuth()
        if (auth.rawDataJson.isBlank() || auth.rawDataJson == "{}") {
            val load = syncFromCloud()
            if (load.isFailure) return Result.failure(load.exceptionOrNull() ?: Exception("Sem dados do servidor"))
        }
        val auth2 = getAuth()
        val data = try { JSONObject(auth2.rawDataJson) } catch (_: Exception) {
            return Result.failure(Exception("Cache inválido"))
        }
        val arr = data.optJSONArray("customers") ?: JSONArray().also { data.put("customers", it) }
        var alreadyThere = false
        for (i in 0 until arr.length()) {
            if (arr.optJSONObject(i)?.optString("id") == customer.id) {
                alreadyThere = true
                break
            }
        }
        if (!alreadyThere) {
            arr.put(JSONObject().apply {
                put("id", customer.id)
                put("nome", customer.nome)
                put("telefone", customer.telefone)
                put("cpf", customer.cpf)
                put("endereco", customer.endereco)
                put("aniversario", "")
                put("pontos", 0)
                put("debts", JSONArray())
            })
        }
        data.put("customers", arr)
        val newRaw = data.toString()
        val save = CloudApi.save(auth2.accessKey, auth2.deviceToken, auth2.driveVersion, newRaw)
        if (!save.ok) return Result.failure(Exception(save.error ?: "Falha ao salvar cliente"))
        saveAuth(auth2.accessKey, auth2.deviceToken, save.version, newRaw)
        return Result.success(Unit)
    }

    suspend fun deleteCustomerLocal(id: String) = dao.deleteCustomer(id)

    suspend fun upsertProduct(product: Product) = dao.upsertProduct(product)
    suspend fun getProduct(id: String) = dao.getProduct(id)

    suspend fun adjustStock(productId: String, kind: String, qty: Int, note: String) {
        val p = dao.getProduct(productId) ?: return
        val newStock = when (kind) {
            "entrada" -> p.estoque + qty
            "saida" -> (p.estoque - qty).coerceAtLeast(0)
            else -> p.estoque
        }
        dao.upsertProduct(p.copy(estoque = newStock))
        dao.insertMovement(
            Movement(
                id = UUID.randomUUID().toString(),
                productId = productId,
                kind = kind,
                qty = qty,
                note = note,
                at = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateSettings(settings: StoreSettings) = dao.upsertSettings(settings)
    suspend fun getSettingsOnce(): StoreSettings = dao.getSettingsOnce() ?: StoreSettings()
    suspend fun insertPrintJob(job: PrintJob) = dao.insertPrintJob(job)

    private suspend fun enqueuePending(type: String, label: String, payloadJson: String) {
        dao.insertPendingOp(
            PendingOp(
                id = "pend_" + UUID.randomUUID().toString().replace("-", "").take(12),
                type = type,
                label = label,
                payloadJson = payloadJson,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * "Despeja" no servidor tudo que foi feito offline neste aparelho, na mesma
     * ordem em que foi criado. Chamado quando a internet volta. Para na primeira
     * falha (ex.: caiu de novo) e deixa o restante na fila pra próxima tentativa,
     * pra não perder a ordem nem duplicar nada.
     */
    suspend fun flushPendingOps(): PendingFlushResult {
        val ops = dao.getPendingOpsOnce()
        if (ops.isEmpty()) return PendingFlushResult(sent = 0, remaining = 0)

        var sent = 0
        var lastError: String? = null
        for (op in ops) {
            val result: Result<Unit> = try {
                when (op.type) {
                    "product" -> {
                        val product = CloudApi.productFromJson(JSONObject(op.payloadJson))
                        pushProductToCloud(product)
                    }
                    "product_update" -> {
                        val product = CloudApi.productFromJson(JSONObject(op.payloadJson))
                        pushProductUpdateToCloud(product)
                    }
                    "catalog" -> {
                        val json = JSONObject(op.payloadJson)
                        pushCatalogToCloud(json.optString("type"), json.optString("value"))
                    }
                    "customer" -> {
                        val json = JSONObject(op.payloadJson)
                        pushCustomerToCloud(
                            Customer(
                                id = json.optString("id"),
                                nome = json.optString("nome"),
                                telefone = json.optString("telefone"),
                                cpf = json.optString("cpf"),
                                endereco = json.optString("endereco")
                            )
                        )
                    }
                    else -> Result.success(Unit)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }

            if (result.isSuccess) {
                dao.deletePendingOp(op.id)
                sent++
            } else {
                lastError = result.exceptionOrNull()?.message
                break
            }
        }
        return PendingFlushResult(sent = sent, remaining = dao.pendingCount(), lastError = lastError)
    }
}
