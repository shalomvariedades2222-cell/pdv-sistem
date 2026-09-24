package com.shalom.slmsys.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import com.shalom.slmsys.BuildConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shalom.slmsys.ui.SlmColors
import com.shalom.slmsys.ui.SlmViewModel
import com.shalom.slmsys.ui.UiState

@Composable
fun AjustesScreen(state: UiState, vm: SlmViewModel) {
    val uriHandler = LocalUriHandler.current
    var storeName by remember(state.settings.storeName) { mutableStateOf(state.settings.storeName) }
    var contact by remember(state.settings.contact) { mutableStateOf(state.settings.contact) }
    var intervalText by remember(state.settings.syncIntervalSec) {
        mutableStateOf(state.settings.syncIntervalSec.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text("Ajustes", color = SlmColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("sysPdv · loja e servidor", color = SpmMuted(), fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = storeName,
            onValueChange = { storeName = it },
            label = { Text("Nome da loja", color = SpmMuted()) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = contact,
            onValueChange = { contact = it },
            label = { Text("Contato", color = SpmMuted()) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                vm.updateSettings(
                    state.settings.copy(
                        storeName = storeName.trim().ifEmpty { "Shalom Variedades" },
                        contact = contact.trim()
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = SlmColors.orange),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Salvar ajustes locais") }

        Spacer(Modifier.height(28.dp))
        Text(
            "SINCRONIZAÇÃO AUTOMÁTICA",
            color = Color(0xFF6B7280),
            fontSize = 11.sp,
            letterSpacing = 1.sp
        )
        Text(
            "Quando ligada, este aparelho busca sozinho no servidor produtos, categorias, modelos de etiqueta (layout, estilo, fotos, símbolos) e demais dados.",
            color = SpmMuted(),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SlmColors.surface2)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.settings.autoSyncEnabled) "Sincronização automática ligada"
                    else "Sincronização automática desligada",
                    color = SlmColors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (state.settings.autoSyncEnabled)
                        "Atualiza a cada ${state.settings.syncIntervalSec}s"
                    else
                        "Só atualiza quando você tocar em \"Atualizar estoque\"",
                    color = SpmMuted(),
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = state.settings.autoSyncEnabled,
                onCheckedChange = { on ->
                    vm.updateSettings(state.settings.copy(autoSyncEnabled = on))
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SlmColors.orange,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFF4B5563)
                )
            )
        }

        if (state.settings.autoSyncEnabled) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Intervalo (segundos)",
                color = SlmColors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Mínimo 10s · máximo 3600s (1 hora). Atalhos abaixo ou digite o valor.",
                color = SpmMuted(),
                fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            SyncSpeedPicker(
                currentSeconds = state.settings.syncIntervalSec,
                onPick = { secs ->
                    intervalText = secs.toString()
                    vm.updateSettings(state.settings.copy(syncIntervalSec = secs))
                }
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { v ->
                        intervalText = v.filter { it.isDigit() }.take(4)
                    },
                    label = { Text("Segundos", color = SpmMuted()) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = fieldColors()
                )
                Button(
                    onClick = {
                        val secs = intervalText.toIntOrNull()?.coerceIn(10, 3600) ?: 30
                        intervalText = secs.toString()
                        vm.updateSettings(state.settings.copy(syncIntervalSec = secs))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SlmColors.orange),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Aplicar") }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.refreshFromCloud() },
            enabled = !state.syncing,
            colors = ButtonDefaults.buttonColors(containerColor = SlmColors.surface2),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (state.syncing) "Sincronizando…" else "Atualizar estoque do servidor") }

        Spacer(Modifier.height(20.dp))
        Text("Atualização do app", color = SlmColors.text, fontWeight = FontWeight.SemiBold)
        Text(
            "Versão atual: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                "Quando você publicar um Release no GitHub com o APK, o app detecta aqui.",
            color = SpmMuted(),
            fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.checkAppUpdate() },
            colors = ButtonDefaults.buttonColors(containerColor = SlmColors.surface2),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Procurar atualização no GitHub") }
        if (state.updateAvailable && state.updateApkUrl.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { uriHandler.openUri(state.updateApkUrl) },
                colors = ButtonDefaults.buttonColors(containerColor = SlmColors.orange),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Baixar versão ${state.updateTag}") }
        }
        state.updateMessage?.let { msg ->
            Spacer(Modifier.height(6.dp))
            Text(msg, color = if (state.updateAvailable) SlmColors.green else SpmMuted(), fontSize = 12.sp)
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { vm.logout() },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Sair (trocar chave)", color = Color(0xFFEF4444)) }

        Spacer(Modifier.height(32.dp))
        Text(
            "SLMsys ${BuildConfig.VERSION_NAME} · editor de etiqueta local · BLE",
            color = SpmMuted(),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SyncSpeedPicker(currentSeconds: Int, onPick: (Int) -> Unit) {
    data class Opt(val label: String, val secs: Int)
    val options = listOf(
        Opt("15s", 15),
        Opt("30s", 30),
        Opt("1 min", 60),
        Opt("2 min", 120),
        Opt("5 min", 300)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            val selected = currentSeconds == opt.secs
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) SlmColors.orange else SpmSurface2())
                    .clickable { onPick(opt.secs) }
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    opt.label,
                    color = if (selected) Color.White else SpmMuted(),
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SpmOrange(),
    unfocusedBorderColor = SpmSurface2(),
    focusedTextColor = SlmColors.text,
    unfocusedTextColor = SlmColors.text,
    focusedContainerColor = SlmColors.surface,
    unfocusedContainerColor = SlmColors.surface,
    focusedLabelColor = SpmOrange(),
    unfocusedLabelColor = SpmMuted()
)

@Composable private fun SpmMuted() = SlmColors.muted
@Composable private fun SpmSurface2() = SlmColors.surface2
@Composable private fun SpmOrange() = SlmColors.orange
