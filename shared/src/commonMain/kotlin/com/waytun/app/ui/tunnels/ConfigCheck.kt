package com.waytun.app.ui.tunnels

import com.waytun.app.core.parser.ConfigErrorKind
import com.waytun.app.core.parser.SingBoxLink
import com.waytun.app.core.parser.SingBoxLinkParser
import com.waytun.app.res.Res
import com.waytun.app.res.import_error_conf_unsupported
import com.waytun.app.res.import_error_field_invalid
import com.waytun.app.res.import_error_field_missing
import com.waytun.app.res.import_error_generic_field
import com.waytun.app.res.import_error_structure
import com.waytun.app.ui.ConfCheck
import com.waytun.app.ui.PlatformServices
import com.waytun.app.ui.UiText

/** What pasted/imported config text turned out to be: a share link (or Xray JSON), or a `.conf`. */
sealed interface ConfigCheck {
    data class Link(val link: SingBoxLink) : ConfigCheck
    data class Conf(val check: ConfCheck) : ConfigCheck
}

/** Same order as Android's TunnelConfigParser.parse: share links first, then wg-quick / awg-quick text. */
fun checkConfig(rawText: String, platform: PlatformServices): ConfigCheck =
    if (SingBoxLinkParser.canParse(rawText)) {
        SingBoxLinkParser.parse(rawText)?.let { ConfigCheck.Link(it) }
            ?: ConfigCheck.Conf(ConfCheck.SyntaxError("Malformed share link"))
    } else {
        ConfigCheck.Conf(platform.checkConf(rawText))
    }

/** The user-facing message for a `.conf` that didn't pass. */
fun describeConfError(check: ConfCheck): UiText? = when (check) {
    is ConfCheck.Valid -> null
    is ConfCheck.Invalid -> {
        val field: Any = check.field.ifBlank { null } ?: UiText.of(Res.string.import_error_generic_field)
        when (check.kind) {
            ConfigErrorKind.MISSING -> UiText.of(Res.string.import_error_field_missing, field)
            ConfigErrorKind.INVALID -> UiText.of(Res.string.import_error_field_invalid, field)
            ConfigErrorKind.STRUCTURE -> UiText.of(Res.string.import_error_structure, field)
        }
    }
    is ConfCheck.SyntaxError -> UiText.of(Res.string.import_error_structure, check.detail)
    ConfCheck.Unsupported -> UiText.of(Res.string.import_error_conf_unsupported)
}
