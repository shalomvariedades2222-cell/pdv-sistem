package com.shalom.slmsys.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shalom.slmsys.R
import com.shalom.slmsys.ui.SlmColors
import com.shalom.slmsys.ui.SlmViewModel
import com.shalom.slmsys.ui.UiState
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(state: UiState, vm: SlmViewModel) {
    var key by remember { mutableStateOf("") }
    // Pré-preenchido com o que já estiver salvo no aparelho (padrão do app na
    // primeira vez). O usuário só precisa mexer aqui se for usar outro servidor.
    var serverUrl by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var showServerField by remember { mutableStateOf(false) }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(40)
        appeared = true
    }
    val enter by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(420),
        label = "loginIn"
    )
    val pulse = rememberInfiniteTransition(label = "logoPulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "ps"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlmColors.bg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = enter
                    translationY = (1f - enter) * 28f
                    scaleX = 0.94f + 0.06f * enter
                    scaleY = 0.94f + 0.06f * enter
                }
                .clip(RoundedCornerShape(20.dp))
                .background(SlmColors.surface)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.logo_syspdv),
                contentDescription = "sysPdv",
                modifier = Modifier
                    .size(96.dp)
                    .scale(pulseScale)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                "sysPdv",
                color = SlmColors.orange,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text("Shalom Variedades · PDV Android", color = SlmColors.muted, fontSize = 13.sp)

            if (state.accessRequestId != null) {
                Spacer(Modifier.height(8.dp))
                Text("Pedido enviado", color = SlmColors.text, fontWeight = FontWeight.SemiBold)
                Text(
                    when (state.accessStatus) {
                        "pendente" -> "Aguardando o administrador aceitar…"
                        "recusado" -> "Pedido recusado"
                        else -> "Status: ${state.accessStatus}"
                    },
                    color = SlmColors.muted,
                    fontSize = 13.sp
                )
                if (state.busy || state.accessStatus == "pendente") {
                    CircularProgressIndicator(color = SlmColors.orange, modifier = Modifier.size(28.dp))
                }
                TextButton(onClick = { vm.cancelAccessRequest() }) {
                    Text("Cancelar", color = SlmColors.muted)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Cole a chave de acesso do site, ou peça autorização ao administrador.",
                    color = SlmColors.muted,
                    fontSize = 13.sp
                )

                TextButton(onClick = { showServerField = !showServerField }) {
                    Text(
                        if (showServerField) "Ocultar servidor" else "Configurar servidor (opcional)",
                        color = SlmColors.muted,
                        fontSize = 12.sp
                    )
                }
                if (showServerField) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("URL do servidor (Apps Script)", color = SlmColors.muted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SlmColors.orange,
                            unfocusedBorderColor = SlmColors.surface2,
                            focusedTextColor = SlmColors.text,
                            unfocusedTextColor = SlmColors.text,
                            cursorColor = SlmColors.orange
                        )
                    )
                    Text(
                        "Configurado uma vez neste aparelho. Deixe em branco para usar o servidor padrão.",
                        color = SlmColors.muted,
                        fontSize = 11.sp
                    )
                }

                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Chave de acesso", color = SlmColors.muted) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SlmColors.orange,
                        unfocusedBorderColor = SlmColors.surface2,
                        focusedTextColor = SlmColors.text,
                        unfocusedTextColor = SlmColors.text,
                        cursorColor = SlmColors.orange
                    )
                )
                if (state.loginError != null) {
                    Text(state.loginError, color = SlmColors.danger, fontSize = 13.sp)
                }
                Button(
                    onClick = { vm.loginWithKey(key, serverUrl) },
                    enabled = !state.busy && key.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = SlmColors.orange),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Conectar com a chave")
                    }
                }
                OutlinedButton(
                    onClick = { vm.requestDeviceAccess(serverUrl) },
                    enabled = !state.busy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pedir autorização ao administrador", color = SlmColors.text)
                }
            }
        }
    }
}
