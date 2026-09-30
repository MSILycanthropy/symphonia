package io.github.msilycanthropy.symphonia

import com.hypixel.hytale.server.core.universe.PlayerRef
import io.github.msilycanthropy.symphonia.ui.core.PropValue.Color
import io.github.msilycanthropy.symphonia.ui.core.PropValue.Raw
import io.github.msilycanthropy.symphonia.ui.dsl.page

fun counterPage(player: PlayerRef) = page(player) {
    val count = state(0)

    group(layout = "Middle") {
        prop("Background", Color("000000", 0.45f))
        group(layout = "Top") {
            prop("Anchor", Raw("(Width: 360)"))
            prop("Background", Color("1b2430"))
            prop("Padding", 16)

            text { "Count: ${count()}" }.apply {
                prop("Style", Raw("(FontSize: 32, RenderBold: true, HorizontalAlignment: Center, TextColor: #f0c95a)"))
            }

            group(layout = "Left") {
                button("-") { onClick { count.update { it - 1 } } }
                button("+") { onClick { count.update { it + 1 } } }
                button("Reset") { onClick { count.set(0) } }
            }
        }
    }
}
