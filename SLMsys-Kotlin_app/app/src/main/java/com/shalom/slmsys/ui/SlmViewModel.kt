package com.shalom.slmsys.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shalom.slmsys.data.Customer
import com.shalom.slmsys.data.CustomerDraft
import com.shalom.slmsys.data.PrintJob
import com.shalom.slmsys.data.Product
import com.shalom.slmsys.data.ProductDraft
import com.shalom.slmsys.data.Repository
import com.shalom.slmsys.data.StoreSettings
import com.shalom.slmsys.util.AppUpdateChecker
import com.shalom.slmsys.printer.BlePrinter
import com.shalom.slmsys.printer.EscPos
import com.shalom.slmsys.util.parseMoney
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppView { ESTOQUE, MOVIMENTOS, CLIENTES, IMPRESSORA, AJUSTES }
enum class PrinterStatus { DISCONNECTED, SCANNING, CONNECTING, READY, PRINTING, ERROR }

data class UiState(
    val loggedIn: Boolean = false,
    val checkingAuth: Boolean = true,
    val view: AppView = AppView.ESTOQUE,
    val products: List<Product> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val categories: List<com.shalom.slmsys.data.Category> = emptyList(),
    val movements: List<com.shalom.slmsys.data.Movement> = emptyList(),
    val printJobs: List<PrintJob> = emptyList(),
    val settings: StoreSettings = StoreSettings(),
    val productNames: List<String> = emptyList(),
    val sizes: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val labelModels: List<String> = emptyList(),
    val query: String = "",
    val categoryFilter: String = "",
    val customerQuery: String = "",
    val movementFilter: String = "", // "" | entrada | saida
    val printerStatus: PrinterStatus = PrinterStatus.DISCONNECTED,
    val printerName: String = "",
    val printerError: String = "",
    val busy: Boolean = false,
    val syncing: Boolean = false,
    /** Texto do "popup" de salvamento (canto da tela, não bloqueia), igual ao
     * indicador "Salvando arquivo no servidor..." do site. Null = escondido. */
    val savingLabel: String? = null,
    val editingLabel: Boolean = false,
    val updateAvailable: Boolean = false,
    val updateTag: String = "",
    val updateApkUrl: String = "",
    val updateMessage: String? = null,
    val message: String? = null,
    val loginError: String? = null,
    val accessRequestId: String? = null,
    val accessStatus: String? = null,
    /** Se este aparelho está com internet agora (detectado de verdade pelo sistema,
     * igual ao evento online/offline do navegador no site). */
    val online: Boolean = true,
    /** Quantas alterações feitas offline ainda não subiram pro servidor. */
    val pendingCount: Int = 0,
    /** Notificação chamativa de conexão na parte de baixo da tela — "ficou offline"
     * / "voltou a internet" — igual ao #connNotify do site. Null = escondida. */
    val connBannerText: String? = null,
    val connBannerKind: String = "offline", // "offline" | "online"
    /** URL do servidor (Apps Script) configurada neste aparelho — mostrada/editável
     * na tela de login. Configurada uma vez; fica salva no banco local. */
    val serverUrl: String = ""
)

class SlmViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val ble = BlePrinter(app)
    private var pollJob: Job? = null

    private val _loggedIn = MutableStateFlow(false)
    private val _checkingAuth = MutableStateFlow(true)
    private val _view = MutableStateFlow(AppView.ESTOQUE)
    private val _query = MutableStateFlow("")
    private val _categoryFilter = MutableStateFlow("")
    private val _customerQuery = MutableStateFlow("")
    private val _movementFilter = MutableStateFlow("")
    private val _printerStatus = MutableStateFlow(PrinterStatus.DISCONNECTED)
    private val _printerName = MutableStateFlow("")
    private val _printerError = MutableStateFlow("")
    private val _busy = MutableStateFlow(false)
    private val _syncing = MutableStateFlow(false)
    private val _savingLabel = MutableStateFlow<String?>(null)
    private val _editingLabel = MutableStateFlow(false)
    private val _updateAvailable = MutableStateFlow(false)
    private val _updateTag = MutableStateFlow("")
    private val _updateApkUrl = MutableStateFlow("")
    private val _updateMessage = MutableStateFlow<String?>(null)
    private val _message = MutableStateFlow<String?>(null)
    private val _loginError = MutableStateFlow<String?>(null)
    private val _accessRequestId = MutableStateFlow<String?>(null)
    private val _accessStatus = MutableStateFlow<String?>(null)
    private val _online = MutableStateFlow(true)
    private val _pendingCount = MutableStateFlow(0)
    private val _connBannerText = MutableStateFlow<String?>(null)
    private val _connBannerKind = MutableStateFlow("offline")
    private val _serverUrl = MutableStateFlow("")
    private var bannerJob: Job? = null
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null

    // Combine in smaller groups to avoid 22-arg combine limits / cast hell
    private data class Bag1(
        val loggedIn: Boolean,
        val checkingAuth: Boolean,
        val view: AppView,
        val products: List<Product>,
        val customers: List<Customer>
    )
    private data class Bag2(
        val categories: List<com.shalom.slmsys.data.Category>,
        val movements: List<com.shalom.slmsys.data.Movement>,
        val printJobs: List<PrintJob>,
        val settings: StoreSettings,
        val productNames: List<String>
    )
    private data class Bag3(
        val sizes: List<String>,
        val colors: List<String>,
        val labelModels: List<String>,
        val query: String,
        val categoryFilter: String
    )
    private data class Bag4(
        val customerQuery: String,
        val movementFilter: String,
        val printerStatus: PrinterStatus,
        val printerName: String,
        val printerError: String
    )
    private data class Bag5(
        val busy: Boolean,
        val syncing: Boolean,
        val message: String?,
        val loginError: String?,
        val accessRequestId: String?,
        val savingLabel: String?
    )

    private val bag1 = combine(_loggedIn, _checkingAuth, _view, repo.products, repo.customers) {
            a, b, c, d, e -> Bag1(a, b, c, d, e)
    }
    private val bag2 = combine(repo.categories, repo.movements, repo.printJobs, repo.settings, repo.productNames) {
            a, b, c, d, e -> Bag2(a, b, c, d, e)
    }
    private val bag3 = combine(repo.sizes, repo.colors, repo.labelModels, _query, _categoryFilter) {
            a, b, c, d, e -> Bag3(a, b, c, d, e)
    }
    private val bag4 = combine(_customerQuery, _movementFilter, _printerStatus, _printerName, _printerError) {
            a, b, c, d, e -> Bag4(a, b, c, d, e)
    }
    private val bag5a = combine(_busy, _syncing, _message, _loginError, _accessRequestId) {
            a, b, c, d, e -> listOf(a, b, c, d, e)
    }
    private val bag5 = combine(bag5a, _savingLabel) { list, f ->
        @Suppress("UNCHECKED_CAST")
        Bag5(
            busy = list[0] as Boolean,
            syncing = list[1] as Boolean,
            message = list[2] as String?,
            loginError = list[3] as String?,
            accessRequestId = list[4] as String?,
            savingLabel = f
        )
    }
    private val bags = combine(bag1, bag2, bag3, bag4, bag5) { b1, b2, b3, b4, b5 ->
        listOf(b1, b2, b3, b4, b5)
    }

    private data class Bag6(
        val online: Boolean,
        val pendingCount: Int,
        val connBannerText: String?,
        val connBannerKind: String,
        val serverUrl: String
    )
    private val bag6 = combine(_online, _pendingCount, _connBannerText, _connBannerKind, _serverUrl) {
            a, b, c, d, e -> Bag6(a, b, c, d, e)
    }

    private data class Bag7(
        val editingLabel: Boolean,
        val updateAvailable: Boolean,
        val updateTag: String,
        val updateApkUrl: String,
        val updateMessage: String?
    )
    private val bag7 = combine(
        _editingLabel, _updateAvailable, _updateTag, _updateApkUrl, _updateMessage
    ) { a, b, c, d, e -> Bag7(a, b, c, d, e) }

    val uiState: StateFlow<UiState> = combine(bags, _accessStatus, bag6, bag7) { list, accessStatus, b6, b7 ->
        val b1 = list[0] as Bag1
        val b2 = list[1] as Bag2
        val b3 = list[2] as Bag3
        val b4 = list[3] as Bag4
        val b5 = list[4] as Bag5
        UiState(
            loggedIn = b1.loggedIn,
            checkingAuth = b1.checkingAuth,
            view = b1.view,
            products = b1.products,
            customers = b1.customers,
            categories = b2.categories,
            movements = b2.movements,
            printJobs = b2.printJobs,
            settings = b2.settings,
            productNames = b2.productNames,
            sizes = b3.sizes,
            colors = b3.colors,
            labelModels = b3.labelModels,
            query = b3.query,
            categoryFilter = b3.categoryFilter,
            customerQuery = b4.customerQuery,
            movementFilter = b4.movementFilter,
            printerStatus = b4.printerStatus,
            printerName = b4.printerName,
            printerError = b4.printerError,
            busy = b5.busy,
            syncing = b5.syncing,
            savingLabel = b5.savingLabel,
            editingLabel = b7.editingLabel,
            updateAvailable = b7.updateAvailable,
            updateTag = b7.updateTag,
            updateApkUrl = b7.updateApkUrl,
            updateMessage = b7.updateMessage,
            message = b5.message,
            loginError = b5.loginError,
            accessRequestId = b5.accessRequestId,
            accessStatus = accessStatus,
            online = b6.online,
            pendingCount = b6.pendingCount,
            connBannerText = b6.connBannerText,
            connBannerKind = b6.connBannerKind,
            serverUrl = b6.serverUrl
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    init {
        viewModelScope.launch {
            // 1) Entra rápido com dados locais — não trava na tela preta de boot
            repo.applyStoredServerUrl()
            _serverUrl.value = repo.getServerUrl()
            val auth = repo.getAuth()
            if (auth.accessKey.isNotBlank() || auth.deviceToken.isNotBlank()) {
                repo.reloadCatalogFromLocal()
                _loggedIn.value = true
                _checkingAuth.value = false
                // 2) Conecta ao servidor em background (com tentativas + animação)
                connectToServerInBackground(initial = true)
            } else {
                _checkingAuth.value = false
            }
        }
        startAutoSyncLoop()
        viewModelScope.launch { repo.pendingCount.collect { _pendingCount.value = it } }
        registerConnectivity()
    }

    /**
     * Tenta sincronizar com o servidor sem bloquear a UI.
     * Mostra "Conectando ao servidor…" com o pill animado e re-tenta se falhar.
     */
    private fun connectToServerInBackground(initial: Boolean = false) {
        viewModelScope.launch {
            if (_syncing.value) return@launch
            _syncing.value = true
            _savingLabel.value = "Conectando ao servidor…"
            var lastError: String? = null
            val attempts = if (initial) 4 else 2
            for (attempt in 1..attempts) {
                _savingLabel.value = if (attempt == 1) {
                    "Conectando ao servidor…"
                } else {
                    "Reconectando ao servidor… ($attempt/$attempts)"
                }
                val r = repo.syncFromCloud()
                if (r.isSuccess) {
                    val n = r.getOrNull()?.products?.size ?: 0
                    val c = r.getOrNull()?.customers?.size ?: 0
                    _savingLabel.value = null
                    _syncing.value = false
                    if (initial || attempt > 1) {
                        _message.value = "Conectado · $n produtos · $c clientes"
                    }
                    return@launch
                }
                lastError = r.exceptionOrNull()?.message
                repo.reloadCatalogFromLocal()
                if (attempt < attempts) {
                    kotlinx.coroutines.delay(1500L * attempt)
                }
            }
            _savingLabel.value = null
            _syncing.value = false
            // Offline: app continua com cache local; não força logout
            if (initial) {
                showConnBanner(
                    lastError?.takeIf { it.isNotBlank() }
                        ?: "Sem conexão. Usando dados deste aparelho.",
                    "offline"
                )
            }
        }
    }

    /**
     * Fica de olho na internet do aparelho de verdade (via ConnectivityManager),
     * igual aos eventos 'offline'/'online' do navegador no site: mostra a
     * notificação chamativa embaixo da tela e, assim que a conexão volta,
     * despeja automaticamente tudo que ficou pendente no servidor.
     */
    private fun registerConnectivity() {
        val cm = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        _online.value = cm.activeNetwork != null

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val wasOffline = !_online.value
                _online.value = true
                if (wasOffline) {
                    showConnBanner("De volta à internet. Reconectando…", "online")
                    connectToServerInBackground(initial = false)
                    viewModelScope.launch { flushPendingOps() }
                }
            }

            override fun onLost(network: Network) {
                // só marca offline se não sobrou nenhuma outra rede ativa
                if (cm.activeNetwork == null) {
                    _online.value = false
                    showConnBanner("Você está offline. Os dados continuam salvos neste aparelho.", "offline")
                }
            }
        }
        connectivityCallback = callback
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
    }

    private fun showConnBanner(text: String, kind: String) {
        bannerJob?.cancel()
        _connBannerText.value = text
        _connBannerKind.value = kind
        bannerJob = viewModelScope.launch {
            delay(7000)
            _connBannerText.value = null
        }
    }

    fun dismissConnBanner() {
        bannerJob?.cancel()
        _connBannerText.value = null
    }

    /**
     * Reenvia pro servidor tudo que foi salvo neste aparelho enquanto estava
     * offline. Usa só o popup de topo (savingLabel) — não bloqueia a tela.
     */
    private suspend fun flushPendingOps() {
        _savingLabel.value = "Enviando alterações pendentes ao servidor…"
        val result = repo.flushPendingOps()
        _savingLabel.value = null
        _message.value = when {
            result.sent == 0 && result.remaining == 0 -> return
            result.remaining == 0 -> "Tudo sincronizado · ${result.sent} pendência(s) enviada(s)"
            result.sent > 0 -> "${result.sent} enviada(s) · ${result.remaining} ainda pendente(s)"
            else -> "Ainda sem conseguir enviar ao servidor · ${result.remaining} pendente(s)"
        }
        if (result.remaining == 0 && result.sent > 0) {
            repo.syncFromCloud()
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectivityCallback?.let { cb ->
            val cm = getApplication<Application>()
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            try { cm?.unregisterNetworkCallback(cb) } catch (_: Exception) { }
        }
    }

    /**
     * Sincronização automática em segundo plano.
     * Liga/desliga e intervalo (segundos) em Ajustes.
     * Quando desligada, só sincroniza quando o usuário pedir manualmente.
     */
    private fun startAutoSyncLoop() {
        viewModelScope.launch {
            while (isActive) {
                val settings = uiState.value.settings
                val seconds = settings.syncIntervalSec.coerceIn(10, 3600)
                delay(seconds * 1000L)
                if (!settings.autoSyncEnabled) continue
                if (_loggedIn.value && !_syncing.value && !_busy.value) {
                    // silencioso: não mostra toast em falha automática
                    repo.syncFromCloud()
                }
            }
        }
    }

    /**
     * Login com a chave de acesso. [serverUrl] é gravado no aparelho antes de
     * conectar — configuração feita uma única vez; nas próximas aberturas do
     * app o valor salvo é usado automaticamente (ver [applyStoredServerUrl]
     * no init). Deixar em branco mantém/volta pro servidor padrão do app.
     */
    fun loginWithKey(key: String, serverUrl: String = "") {
        viewModelScope.launch {
            _loginError.value = null
            _busy.value = true
            repo.saveServerUrl(serverUrl)
            _serverUrl.value = repo.getServerUrl()
            val r = repo.syncFromCloud(key = key.trim())
            _busy.value = false
            if (r.isSuccess) {
                _loggedIn.value = true
                _accessRequestId.value = null
                val n = r.getOrNull()?.products?.size ?: 0
                val c = r.getOrNull()?.customers?.size ?: 0
                val names = repo.productNames.value.size
                _message.value = "Conectado · $n produtos · $c clientes · $names nomes no catálogo"
            } else {
                _loginError.value = r.exceptionOrNull()?.message ?: "Falha ao conectar"
            }
        }
    }

    fun requestDeviceAccess(serverUrl: String = "") {
        viewModelScope.launch {
            _loginError.value = null
            _busy.value = true
            repo.saveServerUrl(serverUrl)
            _serverUrl.value = repo.getServerUrl()
            val r = repo.requestAccess()
            _busy.value = false
            if (r.isFailure) {
                _loginError.value = r.exceptionOrNull()?.message
                return@launch
            }
            _accessRequestId.value = r.getOrNull()
            _accessStatus.value = "pendente"
            startPolling(r.getOrNull()!!)
        }
    }

    private fun startPolling(requestId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(5000)
                val r = repo.pollAccess(requestId)
                if (r.isFailure) continue
                val st = r.getOrNull()!!
                _accessStatus.value = st.status
                when (st.status) {
                    "aceito" -> {
                        _loggedIn.value = true
                        _accessRequestId.value = null
                        _message.value = "Acesso autorizado"
                        break
                    }
                    "recusado" -> {
                        _loginError.value = "Pedido recusado"
                        break
                    }
                }
            }
        }
    }

    fun cancelAccessRequest() {
        pollJob?.cancel()
        _accessRequestId.value = null
        _accessStatus.value = null
    }

    fun logout() {
        viewModelScope.launch {
            pollJob?.cancel()
            repo.clearAuth()
            _loggedIn.value = false
        }
    }

    fun refreshFromCloud() {
        viewModelScope.launch {
            _syncing.value = true
            _savingLabel.value = "Conectando ao servidor…"
            val r = repo.syncFromCloud()
            _syncing.value = false
            _savingLabel.value = null
            if (r.isSuccess) {
                _message.value = "Atualizado · ${r.getOrNull()?.products?.size ?: 0} produtos · ${r.getOrNull()?.customers?.size ?: 0} clientes"
            } else {
                _message.value = r.exceptionOrNull()?.message ?: "Falha ao atualizar"
            }
        }
    }

    fun setView(v: AppView) { _view.value = v }
    fun setQuery(q: String) { _query.value = q }
    fun setCategoryFilter(c: String) { _categoryFilter.value = c }
    fun setCustomerQuery(q: String) { _customerQuery.value = q }
    fun setMovementFilter(f: String) { _movementFilter.value = f }
    fun clearMessage() { _message.value = null }

    fun upsertProduct(draft: ProductDraft, editingId: String? = null) {
        viewModelScope.launch {
            if (editingId != null) {
                val existing = repo.getProduct(editingId) ?: return@launch
                val updated = existing.copy(
                    nome = draft.nome.trim(),
                    categoria = draft.categoria.trim(),
                    tamanho = draft.tamanho.trim(),
                    cor = draft.cor.trim(),
                    custo = parseMoney(draft.custo),
                    venda = parseMoney(draft.venda),
                    estoque = draft.estoque.toIntOrNull() ?: existing.estoque,
                    estoqueMin = draft.estoqueMin.toIntOrNull() ?: existing.estoqueMin
                )
                // Antes só gravava no aparelho; agora salva local e também no
                // servidor (com fila de pendências se estiver sem internet) —
                // igual ao cadastro novo, para nunca ficar diferente do site.
                _savingLabel.value = "Salvando..."
                val result = repo.updateProductAndSync(updated)
                _savingLabel.value = null
                val outcome = result.getOrNull()
                _message.value = when {
                    outcome == null -> result.exceptionOrNull()?.message ?: "Falha ao salvar"
                    outcome.offline -> "Produto atualizado neste aparelho · será enviado ao servidor quando a internet voltar"
                    else -> "Produto atualizado e sincronizado com o servidor"
                }
                return@launch
            }

            // 1) Monta o produto (código de barras etc.) SEM gravar em lugar nenhum.
            val prepared = repo.prepareNewProduct(draft)
            if (prepared == null) {
                _message.value = "Não conectado ao servidor"
                return@launch
            }

            // 2) IMPRIME primeiro e ESPERA terminar. Nada de salvar em paralelo.
            //    Layout da etiqueta vem 100% do servidor (labelModelsJson / designer).
            val qty = draft.labelQty.toIntOrNull()?.coerceAtLeast(1) ?: 1
            var printedOk = false
            if (ble.isConnected) {
                _printerStatus.value = PrinterStatus.PRINTING
                _savingLabel.value = "Imprimindo etiqueta..."
                val settings = repo.getSettingsOnce() // layout/designer do servidor
                val bytes = if (settings.protocol == "tspl") {
                    EscPos.buildTsplLabel(prepared, settings, qty)
                } else {
                    EscPos.buildLabel(prepared, settings, qty)
                }
                // await: só continua quando o BLE terminar de enviar todos os chunks
                val printResult = ble.write(bytes, settings.printSpeed)
                repo.insertPrintJob(
                    PrintJob(
                        id = UUID.randomUUID().toString(),
                        productId = prepared.id,
                        productName = prepared.nome,
                        barcode = prepared.barcode,
                        qty = qty,
                        ok = printResult.isSuccess,
                        mode = "bluetooth",
                        at = System.currentTimeMillis()
                    )
                )
                if (printResult.isSuccess) {
                    printedOk = true
                    _printerStatus.value = PrinterStatus.READY
                    // pequeno respiro pro buffer da impressora drenar antes de seguir
                    delay(300)
                } else {
                    _printerStatus.value = PrinterStatus.ERROR
                    _printerError.value = printResult.exceptionOrNull()?.message ?: "Erro"
                    _savingLabel.value = null
                    _message.value = "Falha na impressão: ${_printerError.value}. Nada foi salvo."
                    return@launch
                }
            }

            // 3) SÓ AGORA salva (aparelho + servidor). Nunca junto com a impressão.
            _savingLabel.value = "Salvando no servidor..."
            val result = repo.persistNewProduct(prepared, draft)
            _savingLabel.value = null

            if (result.isFailure) {
                _message.value = result.exceptionOrNull()?.message ?: "Falha ao salvar"
                return@launch
            }
            val outcome = result.getOrNull()!!
            _message.value = when {
                outcome.offline && printedOk ->
                    "Etiqueta impressa · salvo neste aparelho (sem internet) · cód. ${outcome.product.barcode}"
                outcome.offline ->
                    "Salvo neste aparelho · será enviado ao servidor quando a internet voltar · cód. ${outcome.product.barcode}"
                printedOk ->
                    "Etiqueta impressa · salvo no servidor · cód. ${outcome.product.barcode}"
                else ->
                    "Salvo no servidor · cód. ${outcome.product.barcode}"
            }
        }
    }

    /**
     * Cadastra na hora uma categoria ou nome que ainda não existe no catálogo,
     * chamado pelo botão "Cadastrar categoria/nome" da tela de cadastro de peça
     * quando o texto digitado não bate com nada da lista. Não bloqueia a tela;
     * funciona sem internet e sincroniza depois.
     */
    fun registerCatalogEntry(type: String, value: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (value.isBlank()) {
                onDone(false)
                return@launch
            }
            _savingLabel.value = "Salvando..."
            val r = repo.registerCatalogEntry(type, value)
            _savingLabel.value = null
            if (r.isSuccess) {
                val outcome = r.getOrNull()!!
                val nomeCampo = if (type == "categoria") "Categoria" else "Nome"
                _message.value = if (outcome.offline) {
                    "$nomeCampo salvo neste aparelho (sem internet): ${outcome.value}"
                } else {
                    "$nomeCampo cadastrado: ${outcome.value}"
                }
                onDone(true)
            } else {
                _message.value = r.exceptionOrNull()?.message ?: "Falha ao cadastrar"
                onDone(false)
            }
        }
    }

    fun deleteProduct(id: String) {
        viewModelScope.launch {
            _busy.value = true
            _savingLabel.value = "Excluindo..."
            val r = repo.deleteProductAndSync(id)
            _busy.value = false
            _savingLabel.value = null
            _message.value = if (r.isSuccess) "Excluído do servidor" else
                "Falha: ${r.exceptionOrNull()?.message}"
        }
    }

    fun saveCustomer(draft: CustomerDraft) {
        viewModelScope.launch {
            if (draft.nome.isBlank()) {
                _message.value = "Informe o nome do cliente"
                return@launch
            }
            // não bloqueia a tela — só o popup de topo; funciona sem internet.
            _savingLabel.value = "Salvando..."
            val r = repo.createCustomerAndSync(draft)
            _savingLabel.value = null
            if (r.isSuccess) {
                val outcome = r.getOrNull()!!
                _message.value = if (outcome.offline) {
                    "Cliente salvo neste aparelho · será enviado quando a internet voltar"
                } else {
                    "Cliente salvo no servidor"
                }
            } else {
                _message.value = r.exceptionOrNull()?.message ?: "Falha ao salvar cliente"
            }
        }
    }

    fun adjustStock(productId: String, kind: String, qty: Int, note: String) {
        viewModelScope.launch {
            if (qty <= 0) return@launch
            repo.adjustStock(productId, kind, qty, note)
            _message.value = if (kind == "entrada") "Entrada +$qty" else "Saída −$qty"
        }
    }

    fun updateSettings(patch: StoreSettings) {
        viewModelScope.launch {
            repo.updateSettings(patch)
            _message.value = "Ajustes salvos"
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            _busy.value = true
            _savingLabel.value = "Conectando à impressora..."
            _printerStatus.value = PrinterStatus.CONNECTING
            _printerError.value = ""
            val result = ble.connect(device)
            if (result.isSuccess) {
                _printerStatus.value = PrinterStatus.READY
                _printerName.value = ble.deviceName
                _message.value = "Impressora pronta"
            } else {
                _printerStatus.value = PrinterStatus.ERROR
                _printerError.value = result.exceptionOrNull()?.message ?: "Falha"
            }
            _busy.value = false
            _savingLabel.value = null
        }
    }

    fun disconnectPrinter() {
        ble.disconnect()
        _printerStatus.value = PrinterStatus.DISCONNECTED
        _printerName.value = ""
        _message.value = "Impressora desconectada"
    }

    fun printProduct(productId: String, qty: Int) {
        viewModelScope.launch {
            val product = repo.getProduct(productId) ?: run {
                _message.value = "Produto não encontrado"
                return@launch
            }
            if (!ble.isConnected) {
                _message.value = "Conecte a impressora em Impressora"
                return@launch
            }
            _busy.value = true
            _savingLabel.value = "Imprimindo etiqueta..."
            _printerStatus.value = PrinterStatus.PRINTING
            // Layout do designer (etiquetaModelos) vem do servidor via settings
            val settings = repo.getSettingsOnce()
            val bytes = if (settings.protocol == "tspl") {
                EscPos.buildTsplLabel(product, settings, qty)
            } else {
                EscPos.buildLabel(product, settings, qty)
            }
            val result = ble.write(bytes, settings.printSpeed)
            repo.insertPrintJob(
                PrintJob(
                    id = UUID.randomUUID().toString(),
                    productId = product.id,
                    productName = product.nome,
                    barcode = product.barcode,
                    qty = qty,
                    ok = result.isSuccess,
                    mode = "bluetooth",
                    at = System.currentTimeMillis()
                )
            )
            if (result.isSuccess) {
                _printerStatus.value = PrinterStatus.READY
                _message.value = "Etiqueta enviada · ${product.nome}"
            } else {
                _printerStatus.value = PrinterStatus.ERROR
                _printerError.value = result.exceptionOrNull()?.message ?: "Erro"
                _message.value = "Falha na impressão"
            }
            _busy.value = false
            _savingLabel.value = null
        }
    }

    fun testPrint() {
        viewModelScope.launch {
            if (!ble.isConnected) {
                _message.value = "Conecte a impressora primeiro"
                return@launch
            }
            _busy.value = true
            _savingLabel.value = "Imprimindo teste..."
            _printerStatus.value = PrinterStatus.PRINTING
            val result = ble.write(EscPos.buildTest(uiState.value.settings.storeName), uiState.value.settings.printSpeed)
            _printerStatus.value = if (result.isSuccess) PrinterStatus.READY else PrinterStatus.ERROR
            _message.value = if (result.isSuccess) "Teste enviado" else "Falha no teste"
            _busy.value = false
            _savingLabel.value = null
        }
    }


    fun openLabelEditor() { _editingLabel.value = true }
    fun closeLabelEditor() { _editingLabel.value = false }

    /** Grava o layout editado só neste aparelho e passa a usá-lo na impressão. */
    fun saveLocalLabelLayout(json: String) {
        viewModelScope.launch {
            val s = uiState.value.settings
            repo.updateSettings(
                s.copy(
                    localLabelModelsJson = json,
                    useLocalLabel = true
                )
            )
            _message.value = "Layout salvo neste aparelho (não vai pro site)"
            _editingLabel.value = false
        }
    }

    /** Copia o modelo atual do servidor para o editor local. */
    fun pullLabelFromServer(onDone: (String?) -> Unit) {
        viewModelScope.launch {
            _savingLabel.value = "Baixando modelo do servidor…"
            val r = repo.syncFromCloud()
            _savingLabel.value = null
            val s = uiState.value.settings
            val server = s.labelModelsJson
            if (server.isBlank() || server == "[]") {
                _message.value = "Servidor sem modelo de etiqueta"
                onDone(null)
                return@launch
            }
            repo.updateSettings(
                s.copy(
                    localLabelModelsJson = server,
                    useLocalLabel = true
                )
            )
            _message.value = "Modelo do servidor copiado para este aparelho"
            onDone(server)
        }
    }

    /** Consulta GitHub Releases por atualização do APK. */
    fun checkAppUpdate() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _savingLabel.value = "Procurando atualização…"
            val info = AppUpdateChecker.check()
            _savingLabel.value = null
            _updateAvailable.value = info.available
            _updateTag.value = info.latestTag
            _updateApkUrl.value = info.apkUrl
            _updateMessage.value = when {
                info.available -> "Nova versão ${info.latestTag} disponível"
                info.error != null -> info.error
                else -> "Você já está na versão mais recente"
            }
            if (info.available) {
                _message.value = "Atualização ${info.latestTag} disponível"
            }
        }
    }

    fun clearUpdateMessage() { _updateMessage.value = null }

    fun getBondedDevices(): List<BluetoothDevice> = ble.getBondedDevices()
    fun isBluetoothEnabled(): Boolean = ble.isBluetoothEnabled()
}
