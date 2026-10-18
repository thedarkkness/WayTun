package com.waytun.app.core.parser

enum class ConfigErrorKind { MISSING, INVALID, STRUCTURE }

/** [fieldName] is the exact wg-quick/awg-quick attribute name (e.g. "PrivateKey", "JunkPacketCount"); blank for file-level errors. */
data class ConfigFieldError(val fieldName: String, val kind: ConfigErrorKind)
