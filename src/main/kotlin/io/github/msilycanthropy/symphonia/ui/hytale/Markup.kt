package io.github.msilycanthropy.symphonia.ui.hytale

import io.github.msilycanthropy.symphonia.ui.core.Node
import io.github.msilycanthropy.symphonia.ui.core.PropValue
import kotlin.math.floor

// Renders a node tree as an inline .ui document
object Markup {
    fun render(node: Node): String = buildString { render(node, 0) }

    private fun StringBuilder.render(node: Node, depth: Int) {
        val pad = "  ".repeat(depth)
        append(pad).append(node.type).append(" #").append(node.id).append(" {\n")
        for ((key, value) in node.props) {
            append(pad).append("  ").append(key).append(": ").append(value(value)).append(";\n")
        }
        for (child in node.children) render(child, depth + 1)
        append(pad).append("}\n")
    }

    fun value(v: PropValue): String = when (v) {
        is PropValue.Str -> "\"" + escape(v.value) + "\""
        is PropValue.Num -> num(v.value)
        is PropValue.Bool -> v.value.toString()
        is PropValue.Enum -> v.name
        is PropValue.Color -> "#" + v.hex + (v.alpha?.let { "(" + num(it) + ")" } ?: "")
        is PropValue.Tuple -> (v.typeName ?: "") +
                v.entries.entries.joinToString(", ", "(", ")") { (k, e) -> "$k: ${value(e)}" }

        is PropValue.Raw -> v.text
    }

    // Whole floats print as integers ("48" not "48.0"); others use the type's own shortest
    // repr, so a Float 0.9f stays "0.9" instead of widening to 0.8999...
    private fun num(n: Number): String = when (n) {
        is Float -> if (n == floor(n) && !n.isInfinite()) n.toLong().toString() else n.toString()
        is Double -> if (n == floor(n) && !n.isInfinite()) n.toLong().toString() else n.toString()
        else -> n.toString()
    }

    private fun escape(str: String): String = buildString {
        for (char in str) when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append(' ')
            else -> append(char)
        }
    }
}
