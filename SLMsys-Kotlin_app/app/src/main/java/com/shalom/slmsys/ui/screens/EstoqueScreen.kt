package com.shalom.slmsys.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shalom.slmsys.data.Product
import com.shalom.slmsys.data.ProductDraft
import com.shalom.slmsys.ui.SlmColors
import com.shalom.slmsys.ui.SlmViewModel
import com.shalom.slmsys.ui.UiState
import com.shalom.slmsys.util.formatBRL

@Composable
fun EstoqueScreen(state: UiState, vm: SlmViewModel) {
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Product?>(null) }
    var detail by remember { mutableStateOf<Product?>(null) }
    var confirmDelete by remember { mutableStateOf<Product?>(null) }

    val q = state.query.trim().lowercase()
    val filtered = state.products.filter { p ->
        val catOk = state.categoryFilter.isBlank() || p.categoria.equals(state.categoryFilter, true)
        val textOk = q.isBlank() ||
            p.nome.lowercase().contains(q) ||
            p.barcode.lowercase().contains(q) ||
            p.categoria.lowercase().contains(q) ||
            p.cor.lowercase().contains(q)
        catOk && textOk
    }
    val cats = listOf("") + state.categories.map { it.name }.distinct().sorted()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlmColors.bg)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Estoque", color = SlmColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${filtered.size} de ${state.products.size} itens",
                    color = SlmColors.muted,
                    fontSize = 12.sp
                )
            }
            Button(
                onClick = {
                    editing = null
                    showForm = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = SlmColors.orange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cadastrar")
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = { vm.setQuery(it) },
            placeholder = { Text("Buscar nome, código, categoria…", color = Color(0xFF6B7280)) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = SlmColors.muted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SlmColors.orange,
                unfocusedBorderColor = Color(0xFF2A2A2A),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = SlmColors.orange
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cats.forEach { c ->
                val selected = state.categoryFilter == c || (c.isEmpty() && state.categoryFilter.isEmpty())
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) SlmColors.orange else Color(0xFF1F1F1F))
                        .clickable { vm.setCategoryFilter(c) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (c.isEmpty()) "Todas" else c,
                        color = if (selected) Color.White else SlmColors.muted,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.products.isEmpty()) "Nenhum produto. Conecte e sincronize ou cadastre."
                    else "Nenhum resultado para o filtro.",
                    color = Color(0xFF6B7280)
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { p ->
                    ProductCard(
                        product = p,
                        onClick = { detail = p },
                        onPrint = { vm.printProduct(p.id, 1) }
                    )
                }
            }
        }
    }

    if (showForm) {
        ProductFormDialog(
            product = editing,
            categories = state.categories.map { it.name }.distinct(),
            productNames = state.productNames,
            sizes = state.sizes,
            colors = state.colors,
            onRegisterCatalog = { type, value, onDone -> vm.registerCatalogEntry(type, value, onDone) },
            onDismiss = {
                showForm = false
                editing = null
            },
            onSave = { draft ->
                vm.upsertProduct(draft, editing?.id)
                showForm = false
                editing = null
            }
        )
    }

    detail?.let { p ->
        ProductDetailDialog(
            product = p,
            onDismiss = { detail = null },
            onPrint = { qty ->
                vm.printProduct(p.id, qty)
                detail = null
            },
            onEdit = {
                editing = p
                detail = null
                showForm = true
            },
            onDelete = {
                confirmDelete = p
                detail = null
            },
            onMove = { kind, qty, note ->
                vm.adjustStock(p.id, kind, qty, note)
                detail = null
            }
        )
    }

    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = SlmColors.surface,
            title = { Text("Excluir produto?", color = SlmColors.text) },
            text = {
                Text(
                    "\"${p.nome}\" será removido do estoque e do servidor (lixeira ~6h, como no site).",
                    color = SlmColors.muted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteProduct(p.id)
                    confirmDelete = null
                }) {
                    Text("Excluir", color = SlmColors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text("Cancelar", color = SlmColors.muted)
                }
            }
        )
    }
}

@Composable
private fun ProductCard(product: Product, onClick: () -> Unit, onPrint: () -> Unit) {
    val low = product.estoque <= product.estoqueMin
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SlmColors.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(product.nome, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            val meta = listOf(product.categoria, product.tamanho, product.cor)
                .filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, color = SlmColors.muted, fontSize = 12.sp)
            }
            Text(product.barcode, color = Color(0xFF6B7280), fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatBRL(product.venda), color = SlmColors.orange, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (low) {
                    Icon(Icons.Default.Warning, null, tint = SlmColors.danger, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                }
                Text(
                    "Est: ${product.estoque}",
                    color = if (low) SlmColors.danger else SlmColors.muted,
                    fontSize = 12.sp
                )
            }
            Icon(
                Icons.Default.Print,
                contentDescription = "Imprimir",
                tint = SlmColors.orange,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(22.dp)
                    .clickable(onClick = onPrint)
            )
        }
    }
}

@Composable
fun ProductFormDialog(
    product: Product?,
    categories: List<String>,
    productNames: List<String> = emptyList(),
    sizes: List<String> = emptyList(),
    colors: List<String> = emptyList(),
    onRegisterCatalog: (type: String, value: String, onDone: (Boolean) -> Unit) -> Unit = { _, _, done -> done(false) },
    onDismiss: () -> Unit,
    onSave: (ProductDraft) -> Unit
) {
    var name by remember { mutableStateOf(product?.nome ?: "") }
    var category by remember { mutableStateOf(product?.categoria ?: "") }
    var size by remember { mutableStateOf(product?.tamanho ?: "") }
    var color by remember { mutableStateOf(product?.cor ?: "") }
    var cost by remember { mutableStateOf(if (product != null) product.custo.toString() else "") }
    var price by remember { mutableStateOf(if (product != null) product.venda.toString() else "") }
    var stock by remember { mutableStateOf(product?.estoque?.toString() ?: "1") }
    var minStock by remember { mutableStateOf(product?.estoqueMin?.toString() ?: "3") }
    var labelQty by remember { mutableStateOf("1") }

    // Nomes/categorias registrados nesta sessão do formulário (some do botão de
    // "Cadastrar" assim que confirmado, mesmo antes de a lista vinda do servidor
    // atualizar).
    var justRegisteredNames by remember { mutableStateOf(setOf<String>()) }
    var justRegisteredCats by remember { mutableStateOf(setOf<String>()) }

    val nameSuggestions = remember(name, productNames) {
        productNames.filter { it.contains(name, ignoreCase = true) }.take(12)
    }
    val catSuggestions = remember(category, categories) {
        categories.filter { it.contains(category, ignoreCase = true) }.take(12)
    }
    val sizeSuggestions = remember(size, sizes) {
        sizes.filter { it.contains(size, ignoreCase = true) }.take(10)
    }
    val colorSuggestions = remember(color, colors) {
        colors.filter { it.contains(color, ignoreCase = true) }.take(10)
    }

    // Nome/categoria "andam em par" igual ao site: escolher um preenche o outro
    // sozinho quando ele ainda está vazio.
    fun onPickName(v: String) {
        name = v
        if (category.isBlank()) category = v
    }
    fun onPickCategory(v: String) {
        category = v
        if (name.isBlank()) name = v
    }

    val nameExists = productNames.any { it.equals(name.trim(), true) } || name.trim() in justRegisteredNames
    val catExists = categories.any { it.equals(category.trim(), true) } || category.trim() in justRegisteredCats
    val priceFilled = price.replace(",", ".").toDoubleOrNull()?.let { it > 0.0 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlmColors.surface,
        title = {
            Text(
                if (product == null) "Cadastrar peça" else "Editar peça",
                color = SlmColors.text
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FormField("Nome", name) { name = it }
                SuggestionChips(nameSuggestions) { onPickName(it) }
                if (product == null && name.isNotBlank() && !nameExists) {
                    NewCatalogEntryRow(
                        label = "nome",
                        value = name.trim(),
                        priceFilled = priceFilled,
                        onRegister = {
                            onRegisterCatalog("nome", name.trim()) { ok ->
                                if (ok) {
                                    justRegisteredNames = justRegisteredNames + name.trim()
                                    if (category.isBlank()) category = name.trim()
                                }
                            }
                        }
                    )
                }

                FormField("Categoria", category) { category = it }
                SuggestionChips(catSuggestions) { onPickCategory(it) }
                if (product == null && category.isNotBlank() && !catExists) {
                    NewCatalogEntryRow(
                        label = "categoria",
                        value = category.trim(),
                        priceFilled = priceFilled,
                        onRegister = {
                            onRegisterCatalog("categoria", category.trim()) { ok ->
                                if (ok) {
                                    justRegisteredCats = justRegisteredCats + category.trim()
                                    if (name.isBlank()) name = category.trim()
                                }
                            }
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        FormField("Tamanho", size) { size = it }
                    }
                    Box(Modifier.weight(1f)) {
                        FormField("Cor", color) { color = it }
                    }
                }
                SuggestionChips(sizeSuggestions) { size = it }
                SuggestionChips(colorSuggestions) { color = it }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { FormField("Custo", cost) { cost = it } }
                    Box(Modifier.weight(1f)) { FormField("Venda", price) { price = it } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { FormField("Estoque", stock) { stock = it } }
                    Box(Modifier.weight(1f)) { FormField("Estoque mín.", minStock) { minStock = it } }
                }
                if (product == null) {
                    FormField("Qtd. etiquetas ao salvar", labelQty) { labelQty = it }
                    Text(
                        "Código de barras gerado automaticamente (igual ao site).",
                        color = SlmColors.muted,
                        fontSize = 11.sp
                    )
                }
                if (productNames.isNotEmpty() || categories.isNotEmpty()) {
                    Text(
                        "${productNames.size} nomes · ${categories.size} categorias (servidor + lista pronta)",
                        color = SlmColors.muted,
                        fontSize = 11.sp
                    )
                } else {
                    Text(
                        "Sem catálogo ainda. Toque em Atualizar nos Ajustes após conectar.",
                        color = SlmColors.danger,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) return@TextButton
                    onSave(
                        ProductDraft(
                            nome = name,
                            categoria = category,
                            tamanho = size,
                            cor = color,
                            custo = cost,
                            venda = price,
                            estoque = stock,
                            estoqueMin = minStock,
                            barcode = product?.barcode.orEmpty(),
                            labelQty = labelQty
                        )
                    )
                }
            ) {
                Text("Salvar", color = SlmColors.orange)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = SlmColors.muted)
            }
        }
    )
}

@Composable
private fun SuggestionChips(items: List<String>, onPick: (String) -> Unit) {
    if (items.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { s ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2A2A2A))
                    .clickable { onPick(s) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(s, color = SlmColors.orange, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Linha "Cadastrar categoria/nome novo" — aparece quando o texto digitado no
 * cadastro de peça não bate com nada já cadastrado. Só deixa confirmar depois
 * que o preço de venda estiver preenchido (o produto precisa de preço mesmo).
 */
@Composable
private fun NewCatalogEntryRow(
    label: String,
    value: String,
    priceFilled: Boolean,
    onRegister: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF241C10))
            .padding(10.dp)
    ) {
        Text(
            "\"$value\" ainda não está cadastrado como $label.",
            color = SlmColors.orange,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        if (!priceFilled) {
            Text(
                "Informe o preço de venda abaixo para poder cadastrar.",
                color = SlmColors.muted,
                fontSize = 11.sp
            )
        } else {
            Button(
                onClick = onRegister,
                colors = ButtonDefaults.buttonColors(containerColor = SlmColors.orange),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cadastrar $label \"$value\"", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FormField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = SlmColors.muted) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SlmColors.orange,
            unfocusedBorderColor = Color(0xFF2A2A2A),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = SlmColors.orange
        )
    )
}

@Composable
fun ProductDetailDialog(
    product: Product,
    onDismiss: () -> Unit,
    onPrint: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (kind: String, qty: Int, note: String) -> Unit
) {
    var qty by remember { mutableIntStateOf(1) }
    var moveQty by remember { mutableIntStateOf(1) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlmColors.surface,
        title = { Text(product.nome, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(formatBRL(product.venda), color = SlmColors.orange, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    listOf(product.categoria, product.tamanho, product.cor)
                        .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "—" },
                    color = SlmColors.muted
                )
                Text("Código: ${product.barcode}", color = Color(0xFF9CA3AF), fontSize = 13.sp)
                Text("Estoque: ${product.estoque} (mín. ${product.estoqueMin})", color = Color.White)

                Text("IMPRESSÃO", color = Color(0xFF6B7280), fontSize = 11.sp, letterSpacing = 1.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = qty.toString(),
                        onValueChange = { qty = it.toIntOrNull()?.coerceAtLeast(1) ?: 1 },
                        label = { Text("Qtd", color = SlmColors.muted) },
                        singleLine = true,
                        modifier = Modifier.width(90.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SlmColors.orange,
                            unfocusedBorderColor = Color(0xFF2A2A2A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onPrint(qty) },
                        colors = ButtonDefaults.buttonColors(containerColor = SlmColors.orange),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Print, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Imprimir")
                    }
                }

                Text("MOVIMENTO", color = Color(0xFF6B7280), fontSize = 11.sp, letterSpacing = 1.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = moveQty.toString(),
                        onValueChange = { moveQty = it.toIntOrNull()?.coerceAtLeast(1) ?: 1 },
                        label = { Text("Qtd", color = SlmColors.muted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SlmColors.orange,
                            unfocusedBorderColor = Color(0xFF2A2A2A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Nota", color = SlmColors.muted) },
                        singleLine = true,
                        modifier = Modifier.weight(1.5f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SlmColors.orange,
                            unfocusedBorderColor = Color(0xFF2A2A2A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onMove("entrada", moveQty, note) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Entrada", color = Color(0xFF22C55E)) }
                    OutlinedButton(
                        onClick = { onMove("saida", moveQty, note) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Saída", color = SlmColors.orange) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit) { Text("Editar", color = SlmColors.orange) }
        },
        dismissButton = {
            TextButton(onClick = onDelete) { Text("Excluir", color = SlmColors.danger) }
        }
    )
}
