package com.iven.musicplayergo.preferences

import android.content.Context
import androidx.annotation.StringRes

/** Domain code carries message identity/arguments; the visible UI resolves its own locale. */
sealed class UiText {
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText()
    data class Literal(val value: String) : UiText()
    data class Joined(val parts: List<UiText>, val separator: String = ", ") : UiText()

    fun resolve(context: Context): String {
        val localized = AppLanguage.context(context)
        return render { id, args -> localized.getString(id, *args.toTypedArray()) }
    }

    fun render(string: (Int, List<Any>) -> String): String = when (this) {
        is Resource -> string(id, args.map { if (it is UiText) it.render(string) else it })
        is Literal -> value
        is Joined -> parts.joinToString(separator) { it.render(string) }
    }

    companion object {
        fun of(@StringRes id: Int, vararg args: Any) = Resource(id, args.toList())
    }
}

class UiTextException(val text: UiText) : IllegalStateException("Localized application error")
