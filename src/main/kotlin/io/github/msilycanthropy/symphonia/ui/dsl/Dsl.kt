package io.github.msilycanthropy.symphonia.ui.dsl

import com.hypixel.hytale.server.core.universe.PlayerRef
import io.github.msilycanthropy.symphonia.ui.core.IdGenerator
import io.github.msilycanthropy.symphonia.ui.core.ListState
import io.github.msilycanthropy.symphonia.ui.core.Node
import io.github.msilycanthropy.symphonia.ui.core.PageRuntime
import io.github.msilycanthropy.symphonia.ui.core.PropValue
import io.github.msilycanthropy.symphonia.ui.core.Row
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
        node[name] = runtime.bind(node, name, compute).initial()
    }

    // Both branches are built once, the condition just toggles Visible
    fun show(condition: () -> Boolean, content: UiScope.() -> Unit): ShowScope {
        branch(condition, content)
        return ShowScope(this, condition)
    }

    internal fun branch(visible: () -> Boolean, content: UiScope.() -> Unit) {
        element("Group") {
            prop("LayoutMode", PropValue.Enum("Top"))
            bind("Visible", visible)
            content()
        }
    }

    fun <T> list(initial: List<T>, key: (T) -> Any): ListState<T> = runtime.list(initial, key)

    fun <T> each(items: ListState<T>, layout: String = "Top", id: String? = null, content: UiScope.(Row<T>) -> Unit) {
        element("Group", id) {
            prop("LayoutMode", PropValue.Enum(layout))
            runtime.mount(items, node) { row ->
                val wrapper = Node("Group", ids.next()).apply { this["LayoutMode"] = PropValue.Enum("Top") }
                UiScope(runtime, ids, wrapper).content(row)
                wrapper
            }
        }
    }

    fun element(type: String, id: String? = null, content: UiScope.() -> Unit = {}): UiScope {
        val child = node.add(Node(type, id ?: ids.next()))
        return UiScope(runtime, ids, child).apply(content)
    }

    // elements

    fun group(layout: String = "Top", id: String? = null, content: UiScope.() -> Unit) =
        element("Group", id) { prop("LayoutMode", PropValue.Enum(layout)); content() }

    // Every text defaults to Body and every button to PrimaryButton, so pages look native unless told otherwise.

    fun text(style: () -> PropValue.StyleRef, id: String? = null, value: () -> String) =
        element("Label", id) { bind("Text", value); bind("Style", style) }

    fun text(
        value: String,
        style: PropValue.StyleRef = Style.Body,
        id: String? = null,
        content: UiScope.() -> Unit = {}
    ) =
        element("Label", id) { prop("Text", value); prop("Style", style); content() }

    fun text(
        style: PropValue.StyleRef = Style.Body,
        id: String? = null,
        content: UiScope.() -> Unit = {},
        value: () -> String
    ) =
        element("Label", id) { bind("Text", value); prop("Style", style); content() }


    fun button(
        label: String,
        style: PropValue.StyleRef = Style.PrimaryButton,
        id: String? = null,
        content: UiScope.() -> Unit = {}
    ) =
        element("TextButton", id) { prop("Text", label); prop("Style", style); content() }

    // events

    fun onClick(locks: Boolean = false, run: () -> Unit) {
        runtime.on(node.id, UiEventType.Activating, locks) { run() }
    }
}


// Handle returned by show, so `otherwise` can attach the other branch
class ShowScope internal constructor(private val parent: UiScope, private val cond: () -> Boolean) {
    infix fun otherwise(content: UiScope.() -> Unit) {
        parent.branch({ !cond() }, content)
    }
}

object Style {
    private const val DOC = "Symphonia/Styles.ui"
    val Body = PropValue.StyleRef(DOC, "Body")
    val Title = PropValue.StyleRef(DOC, "Title")
    val Caption = PropValue.StyleRef(DOC, "Caption")
    val Muted = PropValue.StyleRef(DOC, "Muted")
    val Highlight = PropValue.StyleRef(DOC, "Highlight")
    val Display = PropValue.StyleRef(DOC, "Display")
    val PrimaryButton = PropValue.StyleRef(DOC, "PrimaryButton")
    val SecondaryButton = PropValue.StyleRef(DOC, "SecondaryButton")
    val TertiaryButton = PropValue.StyleRef(DOC, "TertiaryButton")
    val DestructiveButton = PropValue.StyleRef(DOC, "DestructiveButton")
    val Input = PropValue.StyleRef(DOC, "Input")
    val Slider = PropValue.StyleRef(DOC, "Slider")
    val Scrollbar = PropValue.StyleRef(DOC, "Scrollbar")
}


// Build a page for a player. content lambda runs exactly once
fun page(player: PlayerRef, title: String, content: UiScope.() -> Unit): HostedPage {
    val runtime = PageRuntime()
    val ids = IdGenerator()
    val root = Node("Group", "Root").apply { this["LayoutMode"] = PropValue.Enum("Top") }
    UiScope(runtime, ids, root).content()
    return HostedPage(player, title, root, runtime)
}
