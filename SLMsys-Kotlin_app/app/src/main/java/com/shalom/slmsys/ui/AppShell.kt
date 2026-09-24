package com.shalom.slmsys.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shalom.slmsys.ui.screens.AjustesScreen
import com.shalom.slmsys.ui.screens.ClientesScreen
import com.shalom.slmsys.ui.screens.EstoqueScreen
import com.shalom.slmsys.ui.screens.ImpressoraScreen
import com.shalom.slmsys.ui.screens.LoginScreen
import com.shalom.slmsys.ui.screens.LabelEditorScreen
import com.shalom.slmsys.ui.screens.MovimentosScreen

@Composable
fun AppShell(vm: SlmViewModel) {
    val state by vm.uiState.collectAsState()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        snack.showSnackbar(m)
        vm.clearMessage()
    }

    AnimatedContent(
        targetState = when {
            state.checkingAuth -> "boot"
            !state.loggedIn -> "login"
            else -> "app"
        },
        transitionSpec = {
            fadeIn(tween(280)) togetherWith fadeOut(tween(180))
        },
        label = "root"
    ) { mode ->
        when (mode) {
            "boot" -> Box(
                Modifier.fillMaxSize().background(SlmColors.bg),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = SlmColors.orange)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Abrindo SLMsys…",
                        color = SlmColors.muted,
                        fontSize = 14.sp
                    )
                }
            }
            "login" -> LoginScreen(state, vm)
            else -> {
                if (state.editingLabel) {
                    LabelEditorScreen(state, vm, onBack = { vm.closeLabelEditor() })
                    return@AnimatedContent
                }
                Scaffold(
                    containerColor = SlmColors.bg,
                    snackbarHost = {
                        SnackbarHost(snack) { data ->
                            Snackbar(
                                snackbarData = data,
                                containerColor = Color(0xFF1F1F1F),
                                contentColor = Color.White
                            )
                        }
                    },
                    bottomBar = { BottomNav(state.view) { vm.setView(it) } }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        AnimatedContent(
                            targetState = state.view,
                            transitionSpec = {
                                val forward = targetState.ordinal > initialState.ordinal
                                (slideInHorizontally(
                                    animationSpec = spring(
                                        dampingRatio = 0.82f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) { if (forward) it / 6 else -it / 6 } + fadeIn(tween(180))) togetherWith
                                    (slideOutHorizontally(
                                        animationSpec = tween(160)
                                    ) { if (forward) -it / 8 else it / 8 } + fadeOut(tween(140)))
                            },
                            label = "tab"
                        ) { view ->
                            when (view) {
                                AppView.ESTOQUE -> EstoqueScreen(state, vm)
                                AppView.MOVIMENTOS -> MovimentosScreen(state, vm)
                                AppView.CLIENTES -> ClientesScreen(state, vm)
                                AppView.IMPRESSORA -> ImpressoraScreen(state, vm)
                                AppView.AJUSTES -> AjustesScreen(state, vm)
                            }
                        }
                        SavingPill(
                            label = state.savingLabel
                                ?: if (state.syncing) "Conectando ao servidor…"
                                else if (state.busy) "Carregando..."
                                else null,
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp)
                        )
                        ConnBanner(
                            text = state.connBannerText,
                            kind = state.connBannerKind,
                            onDismiss = { vm.dismissConnBanner() },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Popup único de carregamento no canto superior da tela — cobre TODO tipo de
 * espera do app (salvando, sincronizando, imprimindo, conectando impressora,
 * excluindo etc.), igual ao indicador "Salvando arquivo no servidor..." do
 * site (#saveStatus): nunca bloqueia o toque no resto da tela e some sozinho
 * quando o ViewModel limpa o texto (savingLabel = null e busy/syncing = false).
 */
@Composable
private fun SavingPill(label: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = label != null,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(220)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1F1F1F))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            CircularProgressIndicator(
                color = SlmColors.orange,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(label ?: "", color = Color.White, fontSize = 12.sp)
        }
    }
}

/**
 * Notificação chamativa na parte de baixo da tela — "ficou offline" / "voltou a
 * internet" — igual ao #connNotify do site: aparece por alguns segundos e some
 * sozinha, sem travar nada por trás.
 */
@Composable
private fun ConnBanner(
    text: String?,
    kind: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(180)) { it / 2 },
        modifier = modifier
    ) {
        val color = if (kind == "online") Color(0xFF3FA564) else Color(0xFFE0B13F)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1F1F1F))
                .clickable { onDismiss() }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
            Spacer(Modifier.width(10.dp))
            Text(text ?: "", color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BottomNav(current: AppView, onSelect: (AppView) -> Unit) {
    data class Item(val view: AppView, val label: String, val icon: ImageVector)
    val items = listOf(
        Item(AppView.ESTOQUE, "Estoque", Icons.Default.Inventory2),
        Item(AppView.MOVIMENTOS, "Movimentos", Icons.Default.SwapVert),
        Item(AppView.CLIENTES, "Clientes", Icons.Default.People),
        Item(AppView.IMPRESSORA, "Impressora", Icons.Default.Print),
        Item(AppView.AJUSTES, "Ajustes", Icons.Default.Settings)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF121212))
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { item ->
            val selected = current == item.view
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.12f else 1f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
                label = "navScale"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .scale(scale)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(item.view) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = if (selected) SlmColors.orange else Color(0xFF6B7280),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    item.label,
                    color = if (selected) SlmColors.orange else Color(0xFF6B7280),
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
