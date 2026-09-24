package com.shalom.slmsys.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.shalom.slmsys.data.Customer
import com.shalom.slmsys.data.CustomerDraft
import com.shalom.slmsys.ui.SlmColors
import com.shalom.slmsys.ui.SlmViewModel
import com.shalom.slmsys.ui.UiState

@Composable
fun ClientesScreen(state: UiState, vm: SlmViewModel) {
    var showForm by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Customer?>(null) }
    val q = state.customerQuery.trim().lowercase()
    val filtered = state.customers.filter {
        q.isBlank() ||
            it.nome.lowercase().contains(q) ||
            it.telefone.contains(q) ||
            it.cpf.contains(q)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(SlmColors.bg)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Clientes", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${filtered.size} cadastrados", color = SlmColors.muted, fontSize = 12.sp)
            }
            Button(
                onClick = { showForm = true },
                colors = ButtonDefaults.buttonColors(containerColor = SlmColors.orange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Novo")
            }
        }

        OutlinedTextField(
            value = state.customerQuery,
            onValueChange = { vm.setCustomerQuery(it) },
            placeholder = { Text("Buscar nome, telefone, CPF…", color = Color(0xFF6B7280)) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = SlmColors.muted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SlmColors.orange,
                unfocusedBorderColor = Color(0xFF2A2A2A),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.customers.isEmpty()) "Nenhum cliente no servidor.\nCadastre ou sincronize."
                    else "Nenhum resultado.",
                    color = Color(0xFF6B7280)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filtered, key = { it.id }) { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SlmColors.surface)
                            .clickable { detail = c }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2A2A2A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, null, tint = SlmColors.orange)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.nome, color = Color.White, fontWeight = FontWeight.SemiBold)
                            if (c.telefone.isNotBlank()) {
                                Text(c.telefone, color = SlmColors.muted, fontSize = 13.sp)
                            }
                            if (c.cpf.isNotBlank()) {
                                Text("CPF ${c.cpf}", color = Color(0xFF6B7280), fontSize = 11.sp)
                            }
                        }
                        if (c.pontos > 0) {
                            Text("${c.pontos} pts", color = SlmColors.orange, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        CustomerFormDialog(
            onDismiss = { showForm = false },
            onSave = {
                vm.saveCustomer(it)
                showForm = false
            }
        )
    }

    detail?.let { c ->
        AlertDialog(
            onDismissRequest = { detail = null },
            containerColor = SlmColors.surface,
            title = { Text(c.nome, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Telefone: ${c.telefone.ifBlank { "—" }}", color = SlmColors.muted)
                    Text("CPF: ${c.cpf.ifBlank { "—" }}", color = SlmColors.muted)
                    Text("Endereço: ${c.endereco.ifBlank { "—" }}", color = SlmColors.muted)
                    Text("Pontos: ${c.pontos}", color = SlmColors.muted)
                }
            },
            confirmButton = {
                TextButton(onClick = { detail = null }) {
                    Text("Fechar", color = SlmColors.orange)
                }
            }
        )
    }
}

@Composable
private fun CustomerFormDialog(onDismiss: () -> Unit, onSave: (CustomerDraft) -> Unit) {
    var nome by remember { mutableStateOf("") }
    var telefone by remember { mutableStateOf("") }
    var cpf by remember { mutableStateOf("") }
    var endereco by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlmColors.surface,
        title = { Text("Novo cliente", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                field("Nome *", nome) { nome = it }
                field("Telefone", telefone) { telefone = it }
                field("CPF", cpf) { cpf = it }
                field("Endereço", endereco) { endereco = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (nome.isBlank()) return@TextButton
                onSave(CustomerDraft(nome, telefone, cpf, endereco))
            }) { Text("Salvar", color = SlmColors.orange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = SlmColors.muted) }
        }
    )
}

@Composable
private fun field(label: String, value: String, onChange: (String) -> Unit) {
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
            unfocusedTextColor = Color.White
        )
    )
}
