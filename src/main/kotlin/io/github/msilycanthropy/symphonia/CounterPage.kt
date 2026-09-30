package io.github.msilycanthropy.symphonia

import com.hypixel.hytale.server.core.universe.PlayerRef
import io.github.msilycanthropy.symphonia.ui.core.PropValue.Raw
import io.github.msilycanthropy.symphonia.ui.dsl.Style
import io.github.msilycanthropy.symphonia.ui.dsl.page

// The frame asset provides the overlay, the decorated container and the title, so the page
// body is just the content that goes into it.
fun counterPage(player: PlayerRef) = page(player, "Counter") {
    val count = state(0)

    text(style = Style.Display) { "${count()}" }

    group(layout = "Left") {
        prop("Anchor", Raw("(Top: 16)"))
        button("-", style = Style.SecondaryButton) { prop("FlexWeight", 1); onClick { count.update { it - 1 } } }
        button("+") { prop("FlexWeight", 1); onClick { count.update { it + 1 } } }
        button("Reset", style = Style.DestructiveButton) { prop("FlexWeight", 1); onClick { count.set(0) } }
    }
}
