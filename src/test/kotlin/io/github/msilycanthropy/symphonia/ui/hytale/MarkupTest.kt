package io.github.msilycanthropy.symphonia.ui.hytale

import io.github.msilycanthropy.symphonia.ui.core.IdGenerator
import io.github.msilycanthropy.symphonia.ui.core.Node
import io.github.msilycanthropy.symphonia.ui.core.PropValue
import io.github.msilycanthropy.symphonia.ui.core.PropValue.Bool
import io.github.msilycanthropy.symphonia.ui.core.PropValue.Color
import io.github.msilycanthropy.symphonia.ui.core.PropValue.Num
import io.github.msilycanthropy.symphonia.ui.core.PropValue.Tuple
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MarkupTest {
    @Test
    fun `renders nested elements with typed values`() {
        val root = Node("Group", "Root").apply {
            this["LayoutMode"] = PropValue.Enum("Top")
            this["Background"] = Color("1b2430", 0.9f)
            add(Node("Label", "Count").apply {
                this["Text"] = "0"
                this["Style"] = Tuple(mapOf("FontSize" to Num(48), "RenderBold" to Bool(true)))
            })
        }
        val expected = """
            Group #Root {
              LayoutMode: Top;
              Background: #1b2430(0.9);
              Label #Count {
                Text: "0";
                Style: (FontSize: 48, RenderBold: true);
              }
            }
        """.trimIndent() + "\n"
        assertEquals(expected, Markup.render(root))
    }

    @Test
    fun `escapes quotes and flattens newlines`() {
        val n = Node("Label", "T").apply { this["Text"] = "say \"hi\"\nnow" }
        assertEquals("Label #T {\n  Text: \"say \\\"hi\\\" now\";\n}\n", Markup.render(n))
    }

    @Test
    fun `rejects ids that would crash the client`() {
        assertFailsWith<IllegalArgumentException> { Node("Label", "lab_r1") }
        assertFailsWith<IllegalArgumentException> { Node("Label", "Row1") }
        assertFailsWith<IllegalArgumentException> { Node("Label", "Ok").also { it["text-color"] = "x" } }
    }

    @Test
    fun `id generator is letters only and unique`() {
        val gen = IdGenerator()
        val ids = List(30) { gen.next() }
        assertEquals("NA", ids[0]); assertEquals("NZ", ids[25]); assertEquals("NAA", ids[26])
        assertEquals(ids.size, ids.toSet().size)
    }
}
