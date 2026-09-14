package com.goroyattemiyo.wms.ui.components

private val URL_PATTERN = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)

fun isDirectUrl(value: String): Boolean = URL_PATTERN.matches(value.trim())
