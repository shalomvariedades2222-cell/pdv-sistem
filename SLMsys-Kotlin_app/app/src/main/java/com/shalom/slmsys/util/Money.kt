package com.shalom.slmsys.util

import java.text.NumberFormat
import java.util.Locale

fun formatBRL(value: Double): String {
    return NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)
}

fun parseMoney(text: String): Double {
    val cleaned = text
        .replace("R$", "", ignoreCase = true)
        .replace(".", "")
        .replace(",", ".")
        .trim()
    return cleaned.toDoubleOrNull() ?: 0.0
}
