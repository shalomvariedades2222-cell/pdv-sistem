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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shalom.slmsys.ui.SlmColors
import com.shalom.slmsys.ui.SlmViewModel
import com.shalom.slmsys.ui.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MovimentosScreen(state: UiState, vm: SlmViewModel) {
    val productMap = state.products.associateBy { it.id }
    val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
    val filter = state.movementFilter
    val list = state.movements.filter {
        filter.isBlank() || it.kind == filter
    }
    val entradas = state.movements.count { it.kind == "entrada" }
    val saidas = state.movements.count { it.kind == "saida" }
    val qtdIn = state.movements.filter { it.kind == "entrada" }.sumOf { it.qty }
    val qtdOut = state.movements.filter { it.kind == "saida" }.sumOf { it.qty }

    Column(
        Modifier
            .fillMaxSize()
            .background(SlmColors.bg)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Movimentos",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            "Entradas e saídas de estoque",
            color = SlmColors.muted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(12.dp))

        // Resumo
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard(Modifier.weight(1f), "Entradas", "$entradas · +$qtdIn", Color(0xFF22C55E))
            StatCard(Modifier.weight(1f), "Saídas", "$saidas · −$qtdOut", SlmColors.orange)
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("" to "Todos", "entrada" to "Entrada", "saida" to "Saída").forEach { (key, label) ->
                val sel = filter == key
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (sel) SlmColors.orange else Color(0xFF1F1F1F))
                        .clickable { vm.setMovementFilter(key) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(label, color = if (sel) Color.White else SlmColors.muted, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nenhum movimento.\nNo produto: Entrada ou Saída.",
                    color = Color(0xFF6B7280)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(list, key = { it.id }) { m ->
                    val product = productMap[m.productId]
                    val isIn = m.kind == "entrada"
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SlmColors.surface)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isIn) Color(0x3322C55E) else Color(0x33F97316))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                if (isIn) "ENTRADA" else "SAÍDA",
                                color = if (isIn) Color(0xFF22C55E) else SlmColors.orange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                product?.nome ?: "Produto removido",
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            Text(fmt.format(Date(m.at)), color = SlmColors.muted, fontSize = 11.sp)
                            if (m.note.isNotBlank()) {
                                Text(m.note, color = Color(0xFF9CA3AF), fontSize = 12.sp)
                            }
                        }
                        Text(
                            "${if (isIn) "+" else "−"}${m.qty}",
                            color = if (isIn) Color(0xFF22C55E) else SlmColors.orange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, title: String, value: String, accent: Color) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SlmColors.surface)
            .padding(14.dp)
    ) {
        Text(title, color = SlmColors.muted, fontSize = 12.sp)
        Text(value, color = accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
