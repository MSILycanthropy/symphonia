package io.github.msilycanthropy.symphonia.ui.core

// Something the flush can send to the client.
sealed interface Patch {
    data class Set(val nodeId: String, val prop: String, val value: Any?) : Patch
}

// A value the page can change.
class State<T> internal constructor(private val runtime: PageRuntime, initial: T) {
    private var value = initial

    operator fun invoke(): T = value

    fun set(next: T) {
        if (next == value) return
        value = next
        runtime.markDirty()
    }

    fun update(f: (T) -> T) = set(f(value))
}

// One bound property, a node, a prop name, and how to get it's current value
class Binding<T>(val nodeId: String, val prop: String, private val compute: () -> T) {
    private var lastSent: Any? = Unsent

    fun initial(): T = compute().also { lastSent = it }

    fun diff(): Patch? {
        val now = compute()
        if (now == lastSent) return null
        lastSent = now
        return Patch.Set(nodeId, prop, now)
    }

    private object Unsent
}

enum class UiEventType { Activating, ValueChanged }

data class EventBinding(
    val nodeId: String,
    val type: UiEventType,
    val handlerId: String,
    val locksInterface: Boolean,
)

// Owns everything a page creates. One per open page.
class PageRuntime {
    private var dirty = false
    private val bindings = mutableListOf<Binding<*>>()
    private val handlers = mutableMapOf<String, (String?) -> Unit>()

    var building = false
        private set
    private var evaluating = 0

    val events: List<EventBinding>
        field = mutableListOf<EventBinding>()
    var onDirty: () -> Unit = {}

    fun <T> state(initial: T): State<T> = State(this, initial)

    fun <T> bind(nodeId: String, prop: String, compute: () -> T): Binding<T> =
        Binding(nodeId, prop, compute).also { bindings += it }

    fun <T> build(block: () -> T): T {
        building = true
        try {
            return block()
        } finally {
            building = false
        }
    }

    internal fun <T> evaluating(block: () -> T): T {
        evaluating++
        try {
            return block()
        } finally {
            evaluating--
        }
    }

    internal fun checkRead() {
        check(!building || evaluating > 0) {
            "state read during page build outside a binding: it would never update. " +
                    "Read it inside a lambda, or use show/each for structure."
        }
    }

    fun handler(id: String, run: (String?) -> Unit) {
        handlers[id] = run
    }

    fun dispatch(handlerId: String, value: String? = null): Boolean {
        val run = handlers[handlerId] ?: return false
        run(value)
        return true
    }

    fun on(nodeId: String, type: UiEventType, locks: Boolean = false, run: (String?) -> Unit): EventBinding {
        val id = nodeId + type.name
        handlers[id] = run
        return EventBinding(nodeId, type, id, locks).also { events += it }
    }

    fun markDirty() {
        if (dirty) return
        dirty = true
        onDirty()
    }

    val isDirty: Boolean get() = dirty

    fun flush(): List<Patch> {
        dirty = false
        return bindings.mapNotNull { it.diff() }
    }
}
