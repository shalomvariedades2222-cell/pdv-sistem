package com.shalom.slmsys.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shalom.slmsys.printer.LabelRenderer
import com.shalom.slmsys.ui.PrinterStatus
import com.shalom.slmsys.ui.SlmColors
import com.shalom.slmsys.ui.SlmViewModel
import com.shalom.slmsys.ui.UiState

@SuppressLint("MissingPermission")
@Composable
fun ImpressoraScreen(
    state: UiState,
    vm: SlmViewModel,
    onRequestPermissions: () -> Unit = {}
) {
    var showDevicePicker by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    val statusLabel = when (state.printerStatus) {
        PrinterStatus.DISCONNECTED -> "Desconectada"
        PrinterStatus.SCANNING -> "Procurando…"
        PrinterStatus.CONNECTING -> "Conectando…"
        PrinterStatus.READY -> "Pronta"
        PrinterStatus.PRINTING -> "Imprimindo…"
        PrinterStatus.ERROR -> "Erro"
    }

    // Preview ao vivo: re-renderiza quando muda tamanho / layout / produto
    val sample = remember(state.products) {
        LabelRenderer.sampleProduct(state.products.firstOrNull())
    }
    val previewLabel = remember(
        state.settings.etiquetaWmm,
        state.settings.etiquetaHmm,
        state.settings.labelModelsJson,
        state.settings.etiquetaOrientacao,
        state.settings.showBarcodeBars,
        state.settings.storeName,
        state.settings.etiquetaQr,
        sample.id,
        sample.nome,
        sample.venda,
        sample.barcode
    ) {
        try {
            LabelRenderer.render(sample, state.settings)
        } catch (_: Exception) {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "sysPdv",
                color = SlmColors.muted,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Impressora",
                color = SlmColors.text,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Bluetooth LE · layout do designer · tamanho local",
                color = SlmColors.muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // ---------- PREVIEW ----------
        Text("Pré-visualização", color = SlmColors.text, fontWeight = FontWeight.SemiBold)
        Text(
            "Atualiza ao mudar largura/altura. Modelo do servidor + tamanho deste aparelho.",
            color = SlmColors.muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SlmColors.surface2)
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            val label = previewLabel
            if (label != null) {
                val ratio = (label.widthMm / label.heightMm).coerceIn(0.4f, 3f)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = label.bitmap.asImageBitmap(),
                        contentDescription = "Prévia da etiqueta",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .aspectRatio(ratio)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF3A2C22), RoundedCornerShape(4.dp))
                            .background(Color.White)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${label.widthMm.toInt()} × ${label.heightMm.toInt()} mm · ${label.bitmap.width}×${label.bitmap.height} px",
                        color = SlmColors.muted,
                        fontSize = 11.sp
                    )
                    if (state.labelModels.isNotEmpty()) {
                        Text(
                            "Modelo: " + state.labelModels.joinToString(),
                            color = SlmColors.muted,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                Text(
                    "Sem modelo no servidor.\nCadastre no designer do site e toque em Atualizar.",
                    color = SlmColors.danger,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---------- TAMANHO ----------
        Text("Tamanho físico (só neste aparelho)", color = SlmColors.text, fontWeight = FontWeight.SemiBold)
        Text(
            "Padrão 40×30 mm. Não grava no site. A prévia acima muda na hora.",
            color = SlmColors.muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SizeStepper(
                label = "Largura",
                valueMm = state.settings.etiquetaWmm,
                range = 15..100,
                onChange = { vm.updateSettings(state.settings.copy(etiquetaWmm = it)) },
                modifier = Modifier.weight(1f)
            )
            SizeStepper(
                label = "Altura",
                valueMm = state.settings.etiquetaHmm,
                range = 10..100,
                onChange = { vm.updateSettings(state.settings.copy(etiquetaHmm = it)) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))
        Text("Atalhos de tamanho", color = SlmColors.muted, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SizePreset("40×30", 40, 30, state) { w, h ->
                vm.updateSettings(state.settings.copy(etiquetaWmm = w, etiquetaHmm = h))
            }
            SizePreset("50×30", 50, 30, state) { w, h ->
                vm.updateSettings(state.settings.copy(etiquetaWmm = w, etiquetaHmm = h))
            }
            SizePreset("30×50", 30, 50, state) { w, h ->
                vm.updateSettings(state.settings.copy(etiquetaWmm = w, etiquetaHmm = h))
            }
            SizePreset("58×40", 58, 40, state) { w, h ->
                vm.updateSettings(state.settings.copy(etiquetaWmm = w, etiquetaHmm = h))
            }
        }

        Spacer(Modifier.height(12.dp))
        SizeStepper(
            label = "Avanço após etiqueta (mm)",
            valueMm = state.settings.feedMm,
            range = 0..30,
            onChange = { vm.updateSettings(state.settings.copy(feedMm = it)) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))

        // ---------- CONEXÃO ----------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SlmColors.surface)
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Status", color = SlmColors.muted, fontSize = 12.sp)
                    Text(
                        statusLabel,
                        color = SlmColors.text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        state.printerName.ifEmpty { "Nenhum aparelho nesta sessão" },
                        color = SlmColors.muted,
                        fontSize = 13.sp
                    )
                }
                if (state.printerStatus == PrinterStatus.READY) {
                    Icon(Icons.Default.CheckCircle, null, tint = SlmColors.green, modifier = Modifier.size(32.dp))
                } else {
                    Icon(Icons.Default.Bluetooth, null, tint = SlmColors.muted, modifier = Modifier.size(32.dp))
                }
            }

            if (state.printerError.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(state.printerError, color = SlmColors.danger, fontSize = 13.sp)
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    onRequestPermissions()
                    devices = vm.getBondedDevices()
                    showDevicePicker = true
                },
                enabled = !state.busy,
                colors = ButtonDefaults.buttonColors(containerColor = SlmColors.orange),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Conectar Bluetooth")
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.testPrint() },
                    enabled = state.printerStatus == PrinterStatus.READY && !state.busy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Print, null, tint = SlmColors.muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Teste", color = SlmColors.muted)
                }
                OutlinedButton(
                    onClick = { vm.disconnectPrinter() },
                    enabled = state.printerStatus != PrinterStatus.DISCONNECTED,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.LinkOff, null, tint = SlmColors.muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Desconectar", color = SlmColors.muted)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---------- TIPO / VELOCIDADE ----------
        Text("Tipo de impressora", color = SlmColors.text, fontWeight = FontWeight.Medium)
        Text(
            "Configuração local — não sincroniza com o site.",
            color = SlmColors.muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrinterTypeOption(
                label = "POS",
                sublabel = "ESC/POS",
                selected = state.settings.protocol != "tspl",
                onClick = { vm.updateSettings(state.settings.copy(protocol = "escpos")) },
                modifier = Modifier.weight(1f)
            )
            PrinterTypeOption(
                label = "TSC",
                sublabel = "TSPL",
                selected = state.settings.protocol == "tspl",
                onClick = { vm.updateSettings(state.settings.copy(protocol = "tspl")) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("Velocidade de impressão", color = SlmColors.text, fontWeight = FontWeight.Medium)
        Text(
            if (state.settings.protocol == "tspl")
                "TSPL: pol/s (1–8). Se sair borrada, diminua."
            else
                "POS: 1 = lenta/segura · 8 = rápida. Se falhar no BLE, diminua.",
            color = SlmColors.muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        PrinterSpeedPicker(
            current = state.settings.printSpeed,
            onPick = { vm.updateSettings(state.settings.copy(printSpeed = it.coerceIn(1, 8))) }
        )

        Spacer(Modifier.height(20.dp))
        Text("Layout da etiqueta", color = SlmColors.text, fontWeight = FontWeight.SemiBold)
        Text(
            if (state.settings.useLocalLabel)
                "Usando layout LOCAL deste aparelho (edições não vão pro site)."
            else
                "Usando layout do SERVIDOR. Você pode editar só neste aparelho.",
            color = SlmColors.muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.openLabelEditor() },
            colors = ButtonDefaults.buttonColors(containerColor = SlmColors.orange),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Editar etiqueta neste aparelho")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                vm.pullLabelFromServer { }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Atualizar modelo do servidor", color = SlmColors.muted)
        }
        Spacer(Modifier.height(28.dp))
    }

    if (showDevicePicker) {
        AlertDialog(
            onDismissRequest = { showDevicePicker = false },
            containerColor = SlmColors.surface,
            title = { Text("Escolher impressora", color = SlmColors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (devices.isEmpty()) {
                        Text(
                            "Nenhum dispositivo emparelhado.\nVá em Configurações → Bluetooth do Android, emparelhe a térmica e volte.",
                            color = SlmColors.muted,
                            fontSize = 13.sp
                        )
                    } else {
                        devices.forEach { device ->
                            val name = device.name ?: device.address ?: "Desconhecido"
                            OutlinedButton(
                                onClick = {
                                    showDevicePicker = false
                                    vm.connectToDevice(device)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Bluetooth, null, tint = SlmColors.orange, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(name, color = SlmColors.text)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevicePicker = false }) {
                    Text("Fechar", color = SlmColors.muted)
                }
            }
        )
    }
}

@Composable
private fun SizeStepper(
    label: String,
    valueMm: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SlmColors.surface)
            .padding(12.dp)
    ) {
        Text(label, color = SlmColors.muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlmColors.surface2)
                    .clickable {
                        onChange((valueMm - 1).coerceIn(range.first, range.last))
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Remove, null, tint = SlmColors.text, modifier = Modifier.size(20.dp))
            }
            Text(
                "$valueMm mm",
                color = SlmColors.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlmColors.surface2)
                    .clickable {
                        onChange((valueMm + 1).coerceIn(range.first, range.last))
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, null, tint = SlmColors.text, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SizePreset(
    label: String,
    w: Int,
    h: Int,
    state: UiState,
    onPick: (Int, Int) -> Unit
) {
    val selected = state.settings.etiquetaWmm == w && state.settings.etiquetaHmm == h
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) SlmColors.orange else SlmColors.surface2)
            .clickable { onPick(w, h) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else SlmColors.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PrinterTypeOption(
    label: String,
    sublabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) SlmColors.orange else SlmColors.surface2)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            color = if (selected) Color.White else SlmColors.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            sublabel,
            color = if (selected) Color.White.copy(alpha = 0.85f) else SlmColors.muted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun PrinterSpeedPicker(current: Int, onPick: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        (1..6).forEach { speed ->
            val selected = current == speed
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) SlmColors.orange else SlmColors.surface2)
                    .clickable { onPick(speed) }
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    "$speed",
                    color = if (selected) Color.White else SlmColors.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}