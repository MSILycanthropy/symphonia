package io.github.msilycanthropy.symphonia.ui.hytale

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
import com.hypixel.hytale.server.core.ui.Value
import com.hypixel.hytale.server.core.ui.builder.EventData
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import io.github.msilycanthropy.symphonia.ui.core.Node
import io.github.msilycanthropy.symphonia.ui.core.PageRuntime
import io.github.msilycanthropy.symphonia.ui.core.Patch
import io.github.msilycanthropy.symphonia.ui.core.PropValue
import io.github.msilycanthropy.symphonia.ui.core.UiEventType
import java.util.logging.Level

// What the client sends back. Which handler and optionally a field value
class UiEvent {
    var handler: String? = null
    var value: String? = null

    companion object {
        @JvmField
        val CODEC: BuilderCodec<UiEvent> = BuilderCodec.builder(UiEvent::class.java, ::UiEvent)
            .append(KeyedCodec("H", Codec.STRING), { e, v -> e.handler = v }, { it.handler }).add()
            .append(KeyedCodec("@V", Codec.STRING), { e, v -> e.value = v }, { it.value }).add()
            .build()
    }
}

// The one Hytale page class to rule them all
// Sends the tree ones, routes events to the runtime and flushes patches
class HostedPage(
    playerRef: PlayerRef,
    private val title: String,
    private val root: Node,
    private val runtime: PageRuntime
) : InteractiveCustomUIPage<UiEvent>(playerRef, CustomPageLifetime.CanDismiss, UiEvent.CODEC) {
    private var inHandler = false

    init {
        // Writes from outside an event handler (timers, etc) schedule a flush on the world thread
        // Writes inside a given haendler are flushed by the handler's guaranteed reply
        runtime.onDirty = { if (!inHandler) scheduleFlush() }
    }

    fun open(ref: Ref<EntityStore>, store: Store<EntityStore>) {
        val player = store.getComponent(ref, Player.getComponentType()) ?: return
        player.pageManager.openCustomPage(ref, store, this)
    }

    override fun build(
        ref: Ref<EntityStore>,
        commands: UICommandBuilder,
        events: UIEventBuilder,
        store: Store<EntityStore>
    ) {
        val start = System.nanoTime()
        val document = Markup.render(root)
        commands.append(FRAME)
        commands.set("#TitleLabel.Text", title)
        commands.appendInline("#Content", document)
        applyStyleRefs(commands, root)
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("H", CLOSE), true)

        for ((nodeId, type, handlerId, locksInterface) in runtime.events) {
            events.addEventBinding(
                type.toHytale(),
                "#$nodeId",
                eventData(type, nodeId, handlerId),
                locksInterface
            )
        }

        // Size and timing of the initial packet, for comparing styling strategies and vanilla pages.
        val sent = commands.commands
        val bytes = sent.sumOf { (it.selector?.length ?: 0) + (it.data?.length ?: 0) + (it.text?.length ?: 0) }
        LOGGER.at(Level.INFO).log(
            "[ui] open '%s': %d commands, ~%d bytes (%d markup), %d bindings, built in %d us",
            title, sent.size, bytes, document.length, runtime.events.size + 1, (System.nanoTime() - start) / 1_000
        )
    }

    /** Open for this page's player. Must be called on the world thread, e.g. from a handler. */
    fun open() {
        val ref = playerRef.reference ?: return
        open(ref, ref.store)
    }

    override fun handleDataEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, data: UiEvent) {
        if (data.handler == CLOSE) {
            close()
            return
        }

        val id = data.handler
        inHandler = true

        try {
            if (id == null || !runtime.dispatch(id, data.value)) {
                LOGGER.at(Level.WARNING).log("[ui] event with unknown handler '%s'", id)
            }
        } catch (t: Throwable) {
            LOGGER.at(Level.SEVERE).withCause(t).log("[ui] handler '%s' threw", id)
        } finally {
            inHandler = false
        }

        flush(reply = true)
    }


    private fun scheduleFlush() {
        val ref = playerRef.reference ?: return

        ref.store.externalData.world.execute { if (ref.isValid) flush(reply = false) }
    }

    private fun flush(reply: Boolean) {
        val patches = runtime.flush()
        if (patches.isEmpty() && !reply) return
        val commands = UICommandBuilder()
        for (patch in patches) apply(commands, patch)
        LOGGER.at(Level.INFO).log("[ui] flush: %d patch(es)%s", patches.size, if (reply) " (reply)" else "")
        sendUpdate(commands, null, false)
    }

    private fun apply(commands: UICommandBuilder, patch: Patch) {
        when (patch) {
            is Patch.Set -> setValue(commands, "#${patch.nodeId}.${patch.prop}", patch.value)
        }
    }

    private fun setValue(commands: UICommandBuilder, selector: String, value: Any?) {
        when (value) {
            is String -> commands.set(selector, value)
            is Boolean -> commands.set(selector, value)
            is Int -> commands.set(selector, value)
            is Float -> commands.set(selector, value)
            is Double -> commands.set(selector, value)
            is PropValue.Str -> commands.set(selector, value.value)
            is PropValue.Bool -> commands.set(selector, value.value)
            is PropValue.Num -> commands.set(selector, value.value.toDouble())
            is PropValue.Enum -> commands.set(selector, value.name)
            is PropValue.Color -> commands.set(selector, Markup.value(value))
            is PropValue.StyleRef -> applyStyle(commands, selector, value)
            else -> error("cannot send ${value?.let { it::class.simpleName }} at runtime for $selector")
        }
    }

    private fun applyStyleRefs(commands: UICommandBuilder, node: Node) {
        for ((prop, value) in node.props) {
            if (value is PropValue.StyleRef) applyStyle(commands, "#${node.id}.$prop", value)
        }
        node.children.forEach { applyStyleRefs(commands, it) }
    }

    private fun applyStyle(commands: UICommandBuilder, selector: String, style: PropValue.StyleRef) {
        commands.set(selector, Value.ref<Any>(style.document, style.name))
        for ((prop, value) in style.overrides) setValue(commands, "$selector.$prop", value)
    }

    private fun eventData(type: UiEventType, nodeId: String, handlerId: String): EventData {
        val data = EventData.of("H", handlerId)

        if (type == UiEventType.ValueChanged) data.append("@V", "#$nodeId.Value")

        return data
    }

    private fun UiEventType.toHytale() = when (this) {
        UiEventType.Activating -> CustomUIEventBindingType.Activating
        UiEventType.ValueChanged -> CustomUIEventBindingType.ValueChanged
    }

    companion object {
        private val LOGGER = HytaleLogger.forEnclosingClass()
        private const val FRAME = "Symphonia/Frame.ui"
        private const val CLOSE = "Close"
    }
}
