package com.shalom.slmsys.ui.screens

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shalom.slmsys.data.StoreSettings
import com.shalom.slmsys.printer.LabelRenderer
import com.shalom.slmsys.ui.SlmColors
import com.shalom.slmsys.ui.SlmViewModel
import com.shalom.slmsys.ui.UiState
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Editor de etiqueta **só neste aparelho**.
 * Mesmo formato JSON do designer do site (x/y/w/h em mm, fontSize em pt).
 * Não envia nada ao servidor — só grava em [StoreSettings.localLabelModelsJson].
 */
@Composable
fun LabelEditorScreen(state: UiState, vm: SlmViewModel, onBack: () -> Unit) {
    val settings = state.settings
    val sourceJson = when {
        settings.useLocalLabel && settings.localLabelModelsJson.isNotBlank() ->
            settings.localLabelModelsJson
        settings.labelModelsJson.isNotBlank() && settings.labelModelsJson != "[]" ->
            settings.labelModelsJson
        else -> "[]"
    }

    var modelsJson by remember(sourceJson) { mutableStateOf(sourceJson) }
    var selectedIdx by remember { mutableIntStateOf(0) }
    var dirty by remember { mutableStateOf(false) }

    val models = remember(modelsJson) { parseModels(modelsJson) }
    val model = models.firstOrNull()
    val layout = model?.optJSONArray("layout") ?: JSONArray()
    val orient = model?.optString("orientacao")?.ifBlank { "horizontal" } ?: "horizontal"
    val designW = if (orient == "vertical") 30 else 50
    val designH = if (orient == "vertical") 50 else 30

    val elements = remember(layout.toString()) {
        (0 until layout.length()).mapNotNull { layout.optJSONObject(it) }
    }
    if (selectedIdx >= elements.size) selectedIdx = 0
    val selected = elements.getOrNull(selectedIdx)

    val previewSettings = remember(modelsJson, settings) {
        settings.copy(
            localLabelModelsJson = modelsJson,
            useLocalLabel = true,
            etiquetaWmm = designW,
            etiquetaHmm = designH
        )
    }
    val sample = remember(state.products) {
        LabelRenderer.sampleProduct(state.products.firstOrNull())
    }
    val preview = remember(modelsJson, sample.id, sample.nome, sample.venda) {
        try {
            LabelRenderer.render(sample, previewSettings)
        } catch (_: Exception) {
            null
        }
    }

    fun mutateSelected(block: (JSONObject) -> Unit) {
        if (model == null || selected == null) return
        block(selected)
        val arr = JSONArray()
        models.forEach { arr.put(it) }
        modelsJson = arr.toString()
        dirty = true
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(SlmColors.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = SlmColors.text)
            }
            Column(Modifier.weight(1f)) {
                Text("Editor de etiqueta", color = SlmColors.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Só neste aparelho · não envia ao site",
                    color = SlmColors.muted,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Preview
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SlmColors.surface2)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val label = preview
            if (label != null) {
                val ratio = (label.widthMm / label.heightMm).coerceIn(0.4f, 3f)
                Image(
                    bitmap = label.bitmap.asImageBitmap(),
                    contentDescription = "Prévia",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .aspectRatio(ratio)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFF3A2C22), RoundedCornerShape(4.dp))
                        .background(Color.White)
                )
            } else {
                Text("Sem modelo. Toque em «Buscar do servidor».", color = SlmColors.danger, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    vm.saveLocalLabelLayout(modelsJson)
                    dirty = false
                },
                enabled = dirty && models.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = SlmColors.orange),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Salvar no app")
            }
            OutlinedButton(
                onClick = {
                    vm.pullLabelFromServer { json ->
                        if (json != null) {
                            modelsJson = json
                            dirty = false
                            selectedIdx = 0
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CloudDownload, null, tint = SlmColors.muted, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Do servidor", color = SlmColors.muted)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            if (settings.useLocalLabel) "Impressão usa o layout LOCAL deste aparelho."
            else "Impressão ainda usa o layout do SERVIDOR. Salve para ativar o local.",
            color = SlmColors.muted,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(16.dp))
        Text("Elementos (${elements.size})", color = SlmColors.text, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))

        elements.forEachIndexed { i, el ->
            val type = el.optString("type").ifBlank { el.optString("tipo") }
            val selectedRow = i == selectedIdx
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedRow) SlmColors.orange.copy(alpha = 0.25f) else SlmColors.surface)
                    .clickable { selectedIdx = i }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${i + 1}. $type",
                    color = SlmColors.text,
                    fontWeight = if (selectedRow) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${el.optDouble("x")}×${el.optDouble("y")} mm",
                    color = SlmColors.muted,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        if (selected != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Ajustar: ${selected.optString("type")}",
                color = SlmColors.text,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            EditorStepper("X (mm)", selected.optDouble("x"), 0.0, designW.toDouble(), 0.5) { v ->
                mutateSelected { it.put("x", v) }
            }
            EditorStepper("Y (mm)", selected.optDouble("y"), 0.0, designH.toDouble(), 0.5) { v ->
                mutateSelected { it.put("y", v) }
            }
            EditorStepper("Largura (mm)", selected.optDouble("w"), 1.0, designW.toDouble(), 0.5) { v ->
                mutateSelected { it.put("w", v) }
            }
            EditorStepper("Altura (mm)", selected.optDouble("h"), 1.0, designH.toDouble(), 0.5) { v ->
                mutateSelected { it.put("h", v) }
            }
            EditorStepper("Fonte (pt)", selected.optDouble("fontSize").takeIf { it > 0 } ?: 8.0, 4.0, 40.0, 0.5) { v ->
                mutateSelected { it.put("fontSize", v) }
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = {
                vm.updateSettings(settings.copy(useLocalLabel = false))
                dirty = false
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Usar sempre o modelo do servidor", color = SlmColors.muted)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun EditorStepper(
    label: String,
    value: Double,
    min: Double,
    max: Double,
    step: Double,
    onChange: (Double) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = SlmColors.muted, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SlmColors.surface2)
                .clickable { onChange((value - step).coerceIn(min, max).let { (it * 10).roundToInt() / 10.0 }) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Remove, null, tint = SlmColors.text, modifier = Modifier.size(18.dp))
        }
        Text(
            String.format("%.1f", value),
            color = SlmColors.text,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .width(56.dp)
                .padding(horizontal = 4.dp),
            fontSize = 15.sp
        )
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SlmColors.surface2)
                .clickable { onChange((value + step).coerceIn(min, max).let { (it * 10).roundToInt() / 10.0 }) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, null, tint = SlmColors.text, modifier = Modifier.size(18.dp))
        }
    }
}

private fun parseModels(json: String): List<JSONObject> {
    return try {
        val arr = JSONArray(json.ifBlank { "[]" })
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    } catch (_: Exception) {
        emptyList()
    }
}
