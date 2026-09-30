package io.github.msilycanthropy.symphonia.ui.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeTest {
    @Test
    fun `flush sends only what changed`() {
        val rt = PageRuntime()
        val count = rt.state(0)
        val label = rt.bind("Count", "Text") { "Count: ${count()}" }
        val static = rt.bind("Title", "Text") { "Counter" }
        label.initial(); static.initial()

        count.set(1)
        assertEquals(listOf(Patch.Set("Count", "Text", "Count: 1")), rt.flush())
        assertEquals(emptyList(), rt.flush(), "nothing changed since last flush")
    }

    @Test
    fun `setting the same value does not dirty the page`() {
        val rt = PageRuntime()
        val count = rt.state(5)
        count.set(5)
        assertFalse(rt.isDirty)
        count.set(6)
        assertTrue(rt.isDirty)
    }

    @Test
    fun `onDirty fires once per cycle`() {
        val rt = PageRuntime()
        var fired = 0
        rt.onDirty = { fired++ }
        val a = rt.state(0)
        val b = rt.state(0)
        a.set(1); b.set(1); a.set(2)
        assertEquals(1, fired)
        rt.flush()
        a.set(3)
        assertEquals(2, fired)
    }

    @Test
    fun `handlers dispatch by derived id`() {
        val rt = PageRuntime()
        var clicks = 0
        val binding = rt.on("Buy", UiEventType.Activating) { clicks++ }
        assertEquals(EventBinding("Buy", UiEventType.Activating, "BuyActivating", locksInterface = false), binding)
        assertTrue(rt.dispatch("BuyActivating"))
        assertFalse(rt.dispatch("Nope"))
        assertEquals(1, clicks)
    }

    @Test
    fun `value events deliver the client value`() {
        val rt = PageRuntime()
        var seen: String? = null
        rt.on("Step", UiEventType.ValueChanged) { seen = it }
        rt.dispatch("StepValueChanged", "42")
        assertEquals("42", seen)
        assertEquals(1, rt.events.size)
    }
}
