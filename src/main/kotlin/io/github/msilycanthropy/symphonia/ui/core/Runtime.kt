package io.github.msilycanthropy.symphonia.ui.core

// Something the flush can send to the client.
sealed interface Patch {
    data class Set(val nodeId: String, val prop: String, val value: Any?) : Patch
    data class Remove(val nodeId: String) : Patch
    data class Insert(val parentId: String, val node: Node, val beforeId: String?, val events: List<EventBinding>) :
        Patch
}

// A value the page can change.
class State<T> internal constructor(private val runtime: PageRuntime, initial: T) {
    private var value = initial

    operator fun invoke(): T {
        runtime.checkRead()
        return value
    }

    fun set(next: T) {
        if (next == value) return
        value = next
        runtime.markDirty()
    }

    fun update(f: (T) -> T) = set(f(value))
}

// One bound property, a node, a prop name, and how to get it's current value
class Binding<T>(private val runtime: PageRuntime, val node: Node, val prop: String, private val compute: () -> T) {
    val nodeId get() = node.id
    private var lastSent: Any? = Unsent

    fun initial(): T = runtime.evaluating { compute() }.also { lastSent = it }

    fun diff(): Patch? {
        val now = runtime.evaluating { compute() }
        if (now == lastSent) return null
        lastSent = now
        node[prop] = now
        return Patch.Set(node.id, prop, now)
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

class ListState<T> internal constructor(
    private val runtime: PageRuntime,
    initial: List<T>,
    internal val key: (T) -> Any
) {
    private var value: List<T> = initial

    operator fun invoke(): List<T> {
        runtime.checkRead()

        return value
    }

    fun set(next: List<T>) {
        if (next == value) return
        value = next
        runtime.markDirty()
    }

    fun update(f: (List<T>) -> List<T>) = set(f(value))

    internal fun peek(): List<T> = value

    internal fun keys(): List<Any> = value.map(key)
}

sealed interface ListOp {
    data class Remove(val key: Any) : ListOp

    /** Insert before `before`, or append when null. */
    data class Insert(val key: Any, val before: Any?) : ListOp
    data class Move(val key: Any, val before: Any?) : ListOp
}

class Row<T> internal constructor(private val runtime: PageRuntime, val key: Any, initial: T) {
    internal var current: T = initial
    internal lateinit var node: Node
    internal val owner = Owner()

    operator fun invoke(): T {
        runtime.checkRead(); return current
    }
}

// Keeps one container's children in step with a ListState, row by key
class ListMount<T> internal constructor(
    private val runtime: PageRuntime,
    private val items: ListState<T>,
    private val container: Node,
    private val build: (Row<T>) -> Node,
) {
    private val order = mutableListOf<Row<T>>()

    internal fun initial() {
        for (item in items.peek()) place(newRow(item), before = null)
    }

    internal fun reconcile(): List<Patch> {
        val next = items.peek()
        val byKey = next.associateBy(items.key)
        val ops = diffKeys(
            order.map { it.key }, next.map(items.key)
        )
        val patches = mutableListOf<Patch>()
        for (op in ops) when (op) {
            is ListOp.Remove -> {
                val row = order.first { it.key == op.key }
                patches += remove(row)
                runtime.release(row.owner)
            }

            is ListOp.Insert -> patches += insert(newRow(byKey.getValue(op.key)), op.before)
            is ListOp.Move -> {
                val row = order.first { it.key == op.key }
                patches += remove(row)
                runtime.owned(row.owner) { /* nothing new registered */ }
                patches += insert(row, op.before)
            }
        }
        for (row in order) row.current = byKey.getValue(row.key)
        return patches
    }

    private fun newRow(item: T): Row<T> {
        val row = Row(runtime, items.key(item), item)
        row.node = runtime.owned(row.owner) { build(row) }
        return row
    }

    private fun place(row: Row<T>, before: Any?) {
        val index = before?.let { b -> order.indexOfFirst { it.key == b } } ?: order.size
        order.add(index, row)
        container.children.add(index, row.node)
    }

    private fun insert(row: Row<T>, before: Any?): Patch {
        place(row, before)
        val beforeId = before?.let { b -> order.first { it.key == b }.node.id }
        return Patch.Insert(container.id, row.node, beforeId, row.owner.events)
    }

    private fun remove(row: Row<T>): Patch {
        order -= row
        container.children -= row.node
        return Patch.Remove(row.node.id)
    }
}

class Owner internal constructor() {
    internal val bindings = mutableListOf<Binding<*>>()
    internal val handlerIds = mutableListOf<String>()
    internal val events = mutableListOf<EventBinding>()
    internal val mounts = mutableListOf<ListMount<*>>()
}

// Owns everything a page creates. One per open page.
class PageRuntime {
    private var dirty = false
    private val bindings = mutableListOf<Binding<*>>()
    private val handlers = mutableMapOf<String, (String?) -> Unit>()
    private val rootOwner = Owner()
    private var owner = rootOwner
    private val mounts = mutableListOf<ListMount<*>>()

    var building = false
        private set
    private var evaluating = 0

    val events: List<EventBinding>
        field = mutableListOf<EventBinding>()
    var onDirty: () -> Unit = {}

    fun <T> state(initial: T): State<T> = State(this, initial)

    fun <T> list(initial: List<T>, key: (T) -> Any): ListState<T> = ListState(this, initial, key)

    fun <T> bind(node: Node, prop: String, compute: () -> T): Binding<T> {
        val binding = Binding(this, node, prop, compute)

        bindings += binding
        owner.bindings += binding

        return binding
    }

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

    internal fun <T> owned(o: Owner, block: () -> T): T {
        owner = o
        try {
            return block()
        } finally {
            owner = o
        }
    }

    internal fun release(o: Owner) {
        bindings -= o.bindings.toSet()
        o.handlerIds.forEach(handlers::remove)
        events -= o.events.toSet()
        mounts -= o.mounts.toSet()
    }

    fun <T> mount(items: ListState<T>, container: Node, build: (Row<T>) -> Node): ListMount<T> {
        val listMount = ListMount(this, items, container, build)

        mounts += listMount
        owner.mounts += listMount
        listMount.initial()

        return listMount
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
        owner.handlerIds += id
        return EventBinding(nodeId, type, id, locks).also {
            events += it
            owner.events += it
        }
    }

    fun markDirty() {
        if (dirty) return
        dirty = true
        onDirty()
    }

    val isDirty: Boolean get() = dirty

    fun flush(): List<Patch> {
        dirty = false
        return mounts.toList().flatMap { it.reconcile() } + bindings.mapNotNull { it.diff() }
    }
}

fun diffKeys(old: List<Any>, new: List<Any>): List<ListOp> {
    val ops = mutableListOf<ListOp>()
    val wanted = new.toHashSet()
    check(wanted.size == new.size) { "duplicate row keys: ${new.groupBy { it }.filterValues { it.size > 1 }.keys}" }

    val mirror = ArrayList<Any>(old.size)
    for (k in old) if (k in wanted) mirror += k else ops += ListOp.Remove(k)

    for (i in new.indices) {
        val want = new[i]
        if (i < mirror.size && mirror[i] == want) continue
        val before = mirror.getOrNull(i)
        val at = mirror.indexOf(want)
        if (at >= 0) {
            mirror.removeAt(at); ops += ListOp.Move(want, before)
        } else ops += ListOp.Insert(want, before)
        mirror.add(i, want)
    }
    return ops
}
