package io.github.msilycanthropy.symphonia.ui.core

// A type .ui property value
sealed interface PropValue {
    data class Str(val value: String) : PropValue
    data class Num(val value: Number) : PropValue
    data class Bool(val value: Boolean) : PropValue

    // The bare ident (`Top`, `Center`, `Rgb`)
    data class Enum(val name: String) : PropValue

    // #rrbbgg
    data class Color(val hex: String, val alpha: Float? = null) : PropValue

    // (Key: value, ...) with an optional type
    data class Tuple(val entries: Map<String, PropValue>, val typeName: String? = null) : PropValue

    data class Raw(val text: String) : PropValue

    companion object {
        fun of(value: Any?): PropValue = when (value) {
            is PropValue -> value
            is String -> Str(value)
            is Boolean -> Bool(value)
            is Number -> Num(value)
            null -> error("null is not a valid .ui property value")
            else -> error("no PropValue mapping for ${value::class.simpleName}")
        }
    }
}

// One element in the page tree. Ids are letters-only PascalCase. Looks like anything else
// crashes the client on the current build
class Node(val type: String, val id: String) {
    val props: MutableMap<String, PropValue> = linkedMapOf()
    val children: MutableList<Node> = mutableListOf()

    init {
        require(IDENT.matches(type)) { "bad element type '$type'" }
        require(IDENT.matches(id)) { "bad node id '$id': ids must be letters only, PascalCase" }
    }

    operator fun set(prop: String, value: Any?) {
        require(IDENT.matches(prop)) { "bad property name '$prop'" }
        props[prop] = PropValue.of(value)
    }

    fun add(child: Node): Node = child.also { children += it }

    companion object {
        val IDENT = Regex("[A-Z][A-Za-z]*")
    }
}

// Generates unique letters-only IDs (NA, NB, ..., NA, NAA, NAB)
class IdGenerator(private val prefix: String = "N") {
    private var next = 0

    fun next(): String {
        var n = next++
        val stringBuilder = StringBuilder()
        do {
            stringBuilder.append('A' + n % 26)
            n = n / 26 - 1
        } while (n >= 0)
        return prefix + stringBuilder.reverse()
    }
}
