package com.shalom.slmsys.data

/**
 * Mesma lista pronta do site (`SLM_PECAS_PADRAO` no index.html).
 * Entra no app como sugestão de **Nome** e **Categoria** ao cadastrar peça,
 * junto com o que já veio cadastrado no servidor.
 */
object CatalogDefaults {

    data class Item(val grupo: String, val nome: String, val sigla: String)

    val itens: List<Item> = listOf(
        Item("Roupas íntimas", "Calcinha", "CN"),
        Item("Roupas íntimas", "Cueca", "CU"),
        Item("Roupas íntimas", "Sutiã", "SU"),
        Item("Roupas íntimas", "Body", "BD"),
        Item("Roupas íntimas", "Camisola", "CS"),
        Item("Roupas íntimas", "Pijama", "PJ"),
        Item("Roupas íntimas", "Robe", "RB"),
        Item("Roupas íntimas", "Meia-calça", "MC"),
        Item("Roupas íntimas", "Meias", "ME"),
        Item("Roupas íntimas", "Cinta", "CT"),

        Item("Roupas superiores", "Camisa", "CM"),
        Item("Roupas superiores", "Camiseta", "T"),
        Item("Roupas superiores", "Blusa", "B"),
        Item("Roupas superiores", "Regata", "RG"),
        Item("Roupas superiores", "Polo", "PO"),
        Item("Roupas superiores", "Suéter", "SE"),
        Item("Roupas superiores", "Cardigã", "CD"),
        Item("Roupas superiores", "Moletom", "MO"),
        Item("Roupas superiores", "Cropped", "CRP"),
        Item("Roupas superiores", "Top", "TP"),
        Item("Roupas superiores", "Colete", "CO"),

        Item("Roupas inferiores", "Calça", "C"),
        Item("Roupas inferiores", "Bermuda", "BM"),
        Item("Roupas inferiores", "Shorts", "SH"),
        Item("Roupas inferiores", "Saia", "S"),
        Item("Roupas inferiores", "Legging", "LG"),
        Item("Roupas inferiores", "Culote", "CUL"),

        Item("Vestidos e macacões", "Vestido", "V"),
        Item("Vestidos e macacões", "Macacão", "M"),
        Item("Vestidos e macacões", "Jardineira", "JD"),
        Item("Vestidos e macacões", "Kaftan", "K"),
        Item("Vestidos e macacões", "Túnica", "TU"),

        Item("Casacos e jaquetas", "Casaco", "CC"),
        Item("Casacos e jaquetas", "Jaqueta", "J"),
        Item("Casacos e jaquetas", "Blazer", "BZ"),
        Item("Casacos e jaquetas", "Trench coat", "TC"),
        Item("Casacos e jaquetas", "Parka", "PK"),
        Item("Casacos e jaquetas", "Capa", "CPA"),
        Item("Casacos e jaquetas", "Poncho", "PN"),
        Item("Casacos e jaquetas", "Xale", "X"),

        Item("Roupas esportivas e de banho", "Agasalho", "AG"),
        Item("Roupas esportivas e de banho", "Maiô", "MA"),
        Item("Roupas esportivas e de banho", "Biquíni", "BQ"),
        Item("Roupas esportivas e de banho", "Sunga", "SG"),
        Item("Roupas esportivas e de banho", "Canga", "CA"),

        Item("Roupas formais e especiais", "Terno", "TR"),
        Item("Roupas formais e especiais", "Smoking", "SM"),
        Item("Roupas formais e especiais", "Uniforme", "UN"),
        Item("Roupas formais e especiais", "Quimono", "Q"),
        Item("Roupas formais e especiais", "Sari", "SR"),

        Item("Calçados", "Sapato", "SP"),
        Item("Calçados", "Tênis", "TN"),
        Item("Calçados", "Sandália", "SD"),
        Item("Calçados", "Chinelo", "CH"),
        Item("Calçados", "Bota", "BT"),
        Item("Calçados", "Sapatilha", "SL"),
        Item("Calçados", "Mocassim", "MS"),

        Item("Acessórios", "Cinto", "CI"),
        Item("Acessórios", "Gravata", "G"),
        Item("Acessórios", "Lenço", "LC"),
        Item("Acessórios", "Cachecol", "CAC"),
        Item("Acessórios", "Chapéu", "CP"),
        Item("Acessórios", "Boné", "BO"),
        Item("Acessórios", "Luvas", "L"),
        Item("Acessórios", "Bolsa", "BS"),
        Item("Acessórios", "Mochila", "MH"),
        Item("Acessórios", "Carteira", "CR"),
        Item("Acessórios", "Óculos", "OC"),
        Item("Acessórios", "Relógio", "RL"),
        Item("Acessórios", "Bijuteria", "BJ"),
        Item("Acessórios", "Joia", "JO"),

        Item("Outros itens comuns", "Suspensório", "SS"),
        Item("Outros itens comuns", "Porta-níqueis", "PQ"),
        Item("Outros itens comuns", "Necessaire", "NC"),
        Item("Outros itens comuns", "Porta-maquiagem", "PM")
    )

    /** Todos os nomes da lista pronta (para Nome e Categoria). */
    val nomes: List<String> = itens.map { it.nome }

    /**
     * Junta o que veio do servidor com a lista pronta, sem duplicar
     * (ignora maiúsculas/acentos de forma simples).
     */
    fun mergeWith(serverNames: List<String>): List<String> {
        val seen = linkedSetOf<String>()
        val out = mutableListOf<String>()
        fun add(n: String) {
            val key = n.trim().lowercase()
            if (key.isEmpty() || key in seen) return
            seen += key
            out += n.trim()
        }
        // Servidor primeiro (já cadastrados na loja)
        serverNames.forEach { add(it) }
        // Depois a lista pronta embutida
        nomes.forEach { add(it) }
        return out.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
}
