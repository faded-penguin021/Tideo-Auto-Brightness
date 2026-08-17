package com.tideo.autobrightness.app.ui.components

import java.util.Locale

// Coordinate fields: use as a PAIR — locale-aware write vs dot-only read is DB-051, again DB-061.
internal fun formatCoord(value: Double): String = String.format(Locale.US, "%.5f", value)

internal fun parseCoord(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

internal fun coordText(value: Double?): String = value?.let { formatCoord(it) } ?: ""
