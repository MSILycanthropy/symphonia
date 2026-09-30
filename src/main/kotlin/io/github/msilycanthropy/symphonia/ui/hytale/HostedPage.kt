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
    private val root: Node,
    private val runtime: PageRuntime
) : InteractiveCustomUIPage<UiEvent>(playerRef, CustomPageLifetime.CanDismiss, UiEvent.CODEC) {
    private var inHandler = false

    init {
        // Writes from outside an event handler (timers, etc) schedule a flush on the world thread
        // Writes inside a given handler are flushed by the handler's guaranteed reply
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
        val document = Markup.render(root)
        commands.appendInline(null, document)

        for ((nodeId, type, handlerId, locksInterface) in runtime.events) {
            events.addEventBinding(
                type.toHytale(),
                "#$nodeId",
                eventData(type, nodeId, handlerId),
                locksInterface
            )
        }

        LOGGER.at(Level.INFO).log("[ui] open: %d chars of markup, %d bindings", document.length, runtime.events.size)
    }

    override fun handleDataEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, data: UiEvent) {
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
            is Patch.Set -> {
                val selector = "#${patch.nodeId}.${patch.prop}"
                when (val v = patch.value) {
                    is String -> commands.set(selector, v)
                    is Boolean -> commands.set(selector, v)
                    is Int -> commands.set(selector, v)
                    is Float -> commands.set(selector, v)
                    is Double -> commands.set(selector, v)
                    is PropValue.Str -> commands.set(selector, v.value)
                    is PropValue.Bool -> commands.set(selector, v.value)
                    is PropValue.Num -> commands.set(selector, v.value.toDouble())
                    is PropValue.Enum -> commands.set(selector, v.name)
                    is PropValue.Color -> commands.set(selector, Markup.value(v))
                    else -> error("cannot send ${v?.let { it::class.simpleName }} at runtime for $selector")
                }
            }
        }
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
    }
}
