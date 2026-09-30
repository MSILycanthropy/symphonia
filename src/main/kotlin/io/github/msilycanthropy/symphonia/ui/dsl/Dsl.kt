package io.github.msilycanthropy.symphonia.ui.dsl

import com.hypixel.hytale.server.core.universe.PlayerRef
import io.github.msilycanthropy.symphonia.ui.core.IdGenerator
import io.github.msilycanthropy.symphonia.ui.core.Node
import io.github.msilycanthropy.symphonia.ui.core.PageRuntime
import io.github.msilycanthropy.symphonia.ui.core.PropValue
import io.github.msilycanthropy.symphonia.ui.core.State
import io.github.msilycanthropy.symphonia.ui.core.UiEventType
import io.github.msilycanthropy.symphonia.ui.hytale.HostedPage

@DslMarker
annotation class UiDsl

@UiDsl
class UiScope internal constructor(val runtime: PageRuntime, private val ids: IdGenerator, val node: Node) {
    fun <T> state(initial: T): State<T> = runtime.state(initial)

    //  A static property on this element
    fun prop(name: String, value: Any) {
        node[name] = value
    }

    // A bound property, the lamda is re-evaled every flush and only changes are sent
    fun <T : Any> bind(name: String, compute: () -> T) {
        node[name] = runtime.bind(node.id, name, compute).initial()
    }

    fun element(type: String, id: String? = null, content: UiScope.() -> Unit = {}): UiScope {
        val child = node.add(Node(type, id ?: ids.next()))
        return UiScope(runtime, ids, child).apply(content)
    }

    // elements

    fun group(layout: String = "Top", id: String? = null, content: UiScope.() -> Unit) =
        element("Group", id) { prop("LayoutMode", PropValue.Enum(layout)); content() }

    fun text(value: String, id: String? = null, content: UiScope.() -> Unit = {}) =
        element("Label", id) { prop("Text", value); content() }

    fun text(id: String? = null, content: UiScope.() -> Unit = {}, value: () -> String) =
        element("Label", id) { bind("Text", value); content() }

    fun button(label: String, id: String? = null, content: UiScope.() -> Unit = {}) =
        element("TextButton", id) { prop("Text", label); content() }

    // events

    fun onClick(locks: Boolean = false, run: () -> Unit) {
        runtime.on(node.id, UiEventType.Activating, locks) { run() }
    }
}

// Build a page for a player. content lambda runs exactly once
fun page(player: PlayerRef, content: UiScope.() -> Unit): HostedPage {
    val runtime = PageRuntime()
    val ids = IdGenerator()
    val root = Node("Group", "Root")
    UiScope(runtime, ids, root).content()
    return HostedPage(player, root, runtime)
}
