package com.waytun.app.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Text a ViewModel hands to the UI: a string resource (resolved in the current language) or ready text. Args may be UiText too. */
sealed interface UiText {
    data class Res(val resource: StringResource, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText

    companion object {
        fun of(resource: StringResource, vararg args: Any): UiText = Res(resource, args.toList())
    }
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(resource, *args.map { if (it is UiText) it.asString() else it }.toTypedArray())
    is UiText.Raw -> text
}

suspend fun UiText.resolve(): String = when (this) {
    is UiText.Res -> getString(resource, *args.map { if (it is UiText) it.resolve() else it }.toTypedArray())
    is UiText.Raw -> text
}
