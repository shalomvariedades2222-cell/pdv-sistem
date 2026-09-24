package com.shalom.slmsys.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val id: String,
    val nome: String,
    val categoria: String = "",
    val tamanho: String = "",
    val cor: String = "",
    val custo: Double = 0.0,
    val venda: Double = 0.0,
    val estoque: Int = 0,
    val estoqueMin: Int = 3,
    val barcode: String = "",
    /** Foto do produto (data URL / base64) se vier do servidor. */
    val foto: String = "",
    val dataCadastro: String = "",
    val cadastradoPor: String = "",
    /** Modelo de etiqueta PREFERIDO deste produto, exatamente como o site salva:
     * "" ou null = sem preferência (cai no padrão); "premium" ou "normal" = modelo
     * embutido; "custom:<id>" = um modelo personalizado específico (ver
     * StoreSettings.labelModelsJson). Antes o app nunca lia este campo e por isso
     * sempre imprimia o primeiro modelo personalizado da lista, ignorando o que
     * estava escolhido pra cada produto no site. */
    val etiquetaModelo: String = ""
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey val id: String,
    val nome: String,
    val telefone: String = "",
    val cpf: String = "",
    val endereco: String = "",
    val aniversario: String = "",
    val pontos: Int = 0
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val sigla: String = ""
)

@Entity(tableName = "movements")
data class Movement(
    @PrimaryKey val id: String,
    val productId: String,
    val kind: String, // entrada | saida
    val qty: Int,
    val note: String,
    val at: Long
)

@Entity(tableName = "print_jobs")
data class PrintJob(
    @PrimaryKey val id: String,
    val productId: String?,
    val productName: String,
    val barcode: String,
    val qty: Int,
    val ok: Boolean,
    val mode: String,
    val at: Long
)

@Entity(tableName = "settings")
data class StoreSettings(
    @PrimaryKey val id: Int = 1,
    val storeName: String = "Shalom Variedades",
    val contact: String = "",
    /** Logo da loja (data URL / base64) vinda de settings.logo no servidor. */
    val storeLogo: String = "",
    /**
     * Tamanho FÍSICO da etiqueta neste aparelho (mm). Padrão 40×30 como no site.
     * Configuração LOCAL do app — não grava no site e não é sobrescrita na sync.
     */
    val etiquetaWmm: Int = 40,
    val etiquetaHmm: Int = 30,
    val feedMm: Int = 6,
    /** Protocolo da impressora deste aparelho: "escpos" (POS) ou "tspl" (TSC). LOCAL. */
    val protocol: String = "escpos",
    /**
     * Velocidade LOCAL deste aparelho (1–8).
     * TSPL: comando SPEED (pol/s). ESC/POS: pausa entre blocos BLE (1=lenta, 8=rápida).
     */
    val printSpeed: Int = 5,
    val showBarcodeBars: Boolean = true,
    /** JSON array de etiquetaModelos do servidor (layout completo). O app sempre usa
     * exatamente o que está aqui — não existe mais um modelo próprio embutido no app. */
    val labelModelsJson: String = "[]",
    /**
     * Cópia LOCAL do layout de etiqueta (mesmo formato JSON do site).
     * Edições no app gravam só aqui — nunca sobem pro servidor.
     * Se [useLocalLabel] for true, a impressão usa este JSON em vez de [labelModelsJson].
     */
    val localLabelModelsJson: String = "",
    /** true = usar layout editado neste aparelho; false = usar o do servidor. */
    val useLocalLabel: Boolean = false,
    val etiquetaOrientacao: String = "horizontal",
    /** Vem do servidor (checkbox "Usar etiqueta personalizada" do site). Não é mais
     * usado para decidir o layout da etiqueta no app: o LabelRenderer sempre usa o
     * modelo salvo em [labelModelsJson] quando existe, independente deste valor. */
    val etiquetaModoCustom: Boolean = false,
    /** Cor de destaque da etiqueta (borda, preço, chip), igual ao seletor de cor
     * do site ("etiquetaCor"). */
    val etiquetaCor: String = "#000000",
    /** Link mostrado no QR code dos modelos embutidos Premium/Normal (o QR de
     * "aponte a câmera", não o do produto). Vem de "etiquetaQr" no site. */
    val etiquetaQr: String = "",
    /** Se o QR acima deve aparecer nos modelos Premium/Normal. Vem de
     * "etiquetaMostrarQr" no site (padrão: true). */
    val etiquetaMostrarQr: Boolean = true,
    /** Configurações LOCAIS do aparelho (não vêm do servidor / não são sobrescritas
     * pela sincronização — ver [com.shalom.slmsys.data.Repository.applyCloudResult]):
     * de quanto em quanto tempo o app busca atualizações automaticamente, e o tipo
     * ([protocol]) e a velocidade ([printSpeed]) da impressora deste aparelho. */
    val syncIntervalSec: Int = 30,
    /** Se a sincronização automática periódica está ligada neste aparelho. */
    val autoSyncEnabled: Boolean = true
)

/**
 * Fila de alterações feitas offline (sem internet) que ainda não subiram pro
 * servidor. Cada linha é uma ação pendente (produto novo, categoria/nome novo,
 * cliente novo); quando a internet volta o app reenvia tudo na ordem em que
 * foi criado e apaga a linha assim que o servidor confirma.
 */
@Entity(tableName = "pending_ops")
data class PendingOp(
    @PrimaryKey val id: String,
    /** "product" | "catalog" | "customer" */
    val type: String,
    /** Texto curto pra mostrar em notificação/lista, ex.: "Produto: Camiseta P". */
    val label: String,
    /** JSON com os dados pra reenviar ao servidor quando a internet voltar. */
    val payloadJson: String,
    val createdAt: Long
)

@Entity(tableName = "auth")
data class AuthState(
    @PrimaryKey val id: Int = 1,
    val accessKey: String = "",
    val deviceToken: String = "",
    val driveVersion: Long = 0,
    val rawDataJson: String = "{}",
    /** URL do Apps Script/servidor que este aparelho usa. Configurada uma vez na
     * tela de login (ou em Ajustes); vazio = usa o padrão embutido no app
     * ([com.shalom.slmsys.cloud.CloudApi.DEFAULT_APPSCRIPT_URL]). */
    val serverUrl: String = ""
)

data class ProductDraft(
    val nome: String = "",
    val categoria: String = "",
    val tamanho: String = "",
    val cor: String = "",
    val custo: String = "",
    val venda: String = "",
    val estoque: String = "1",
    val estoqueMin: String = "3",
    val barcode: String = "",
    val labelQty: String = "1"
)

data class CustomerDraft(
    val nome: String = "",
    val telefone: String = "",
    val cpf: String = "",
    val endereco: String = ""
)
