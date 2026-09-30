package io.github.msilycanthropy.symphonia.ui.core

enum class Align { Start, Center, End }

data class LabelStyle(
    val fontSize: Int? = null,
    val color: PropValue.Color? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val uppercase: Boolean? = null,
    val align: Align? = null,
    val wrap: Boolean? = null
) {
    fun overrides(): Map<String, Any> = buildMap {
        fontSize?.let { put("FontSize", it) }
        color?.let { put("TextColor", it) }
        bold?.let { put("RenderBold", it) }
        italic?.let { put("RenderItalics", it) }
        uppercase?.let { put("RenderUppercase", it) }
        align?.let { put("HorizontalAlignment", PropValue.Enum(it.name)) }
        wrap?.let { put("Wrap", it) }
    }
}
