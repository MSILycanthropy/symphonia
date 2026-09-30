# Symphonia

A small reactive UI framework for Hytale server plugins, written in Kotlin. Pages are functions
that run once, state lives in plain holders, anything that can change is a lambda. The framework
diffs what changed and sends one update packet per tick.

Currently targets the legacy `.ui` protocol.

This is a big ol' experiment.

## The API

This is the target. The parts that exist today are marked in [Status](#status).

```kotlin
data class Item(val id: String, val name: String, val price: Int)

// Custom styles: a sheet object per .ui document. Each `val X by ref()` is the style named X in
// that file, so the Kotlin name and the asset name cannot drift. `Style` is the framework's own
// sheet, backed by Symphonia/Styles.ui. One-offs can use `MyStyles["Name"]` or a Raw tuple.
object MyStyles : StyleSheet("MyMod/Styles.ui") {
    val Price by ref()
    val SoldOut by ref()
}

fun shopPage(player: PlayerRef, startGold: Int, items: List<Item>) = page(player, title = "Shop") {
    val gold = state(startGold)                       // a holder: read with gold(), write with set/update
    val stock = listState(items, key = Item::id)      // a keyed list holder: diffs rows by key
    val search = state("")

    fun buy(item: Item) {
        if (gold() < item.price) return
        gold.update { it - item.price }
        stock.remove(item)
        player.give(item)
    }

    val visible = { stock().filter { it.name.contains(search(), ignoreCase = true) } }

    row {
        text(style = Style.Highlight) { "Gold: ${gold()}" }   // lambda = binding, re-evaluated at flush
        textField(search, placeholder = "Search", debounce = 200.ms)
    }

    show({ visible().isEmpty() }) {                   // structure goes through show/each, never plain if
        text("Nothing here.", style = MyStyles.SoldOut)
    } otherwise {
        each(visible, key = Item::id) { item ->       // item: Row<Item>; rows get local state and handlers
            shopRow(item, canBuy = { gold() >= item().price }) { buy(item()) }
        }
    }

    dynamic {                                         // escape hatch: re-runs and is diffed when its state changes,
        if (gold() > 1000) text("Big spender.")       // so plain if/for work inside it, at the cost of a subtree diff
        for (perk in perksFor(gold())) text(perk, style = Style.Caption)
    }
}

// Components are extension functions on UiScope. They run once, like the page.
fun UiScope.shopRow(item: Row<Item>, canBuy: () -> Boolean, onBuy: () -> Unit) = row {
    val selected = state(false)                       // row-local state; dies with the row
    onClick { selected.update { !it } }

    // styleBy declares both candidates, so a switch is one command; a raw lambda is also allowed
    // and is diffed property by property.
    text(style = styleBy(selected, on = Style.Highlight, off = Style.Body)) { item().name }
    text(style = { if (item().price > 500) Style.Highlight else MyStyles.Price }) { "${item().price}g" }
    button("Buy", enabled = canBuy) { onClick { onBuy() } }
}

// HUDs share the tree and state model but have no events and no per-packet acknowledgement.
fun healthHud(player: PlayerRef, health: State<Int>) = hud(player, key = "health") {
    row {
        text { "HP ${health()}" }
        progressBar { health() / 100f }
    }
}

// Opening a page.
shopPage(playerRef, 100, items).open(ref, store)
```

### The model in one paragraph

A page function builds a tree of nodes once. A `state(x)` is a value with `get` and `set`;
setting it marks the page dirty and nothing else. Every lambda passed where a value is expected
becomes a binding. At flush, once per dirty tick, every binding is evaluated, compared with what
the client last received, and only the differences are sent as `set` commands. `show` toggles
`Visible`; `each` diffs a keyed list into inserts and removes by element id, and each row owns its
handlers and local state. Event handlers run on the world thread and always get a reply, so the
client never locks waiting.

### Styling

Inline markup cannot import vanilla's `Common.ui`, so native styling comes from two assets:

- `Common/UI/Custom/Symphonia/Styles.ui` defines named styles (`Body`, `Title`, `Display`,
  `PrimaryButton`, `DestructiveButton`, ...) that spread the vanilla ones. The host applies them
  per element with `Value.ref` right after the inline append. `Style.X` in Kotlin refers to them.
- `Common/UI/Custom/Symphonia/Frame.ui` is the page chrome: vanilla overlay, decorated
  container, title and close button. The page's tree mounts into its `#Content`.

Runtime style changes come in two forms. `styleBy(state, on, off)` declares both candidates, so
a switch is a single `Value.ref` command. A raw lambda, `style = { ... }`, is always allowed and
is diffed property by property (`#Id.Style.TextColor`); it just costs more commands.

### Conditionals and structure

The page function runs once, so a plain `if` or `for` over state freezes at whatever it saw at
build time. Structure that depends on state goes through one of these:

- `show(cond) { } otherwise { }`: both branches are built once; the condition toggles `Visible`,
  which collapses layout. One `set` per switch.
- `each(list, key) { row -> }`: keyed diff into inserts and removes by element id. Rows get local
  `state` and handlers that are dropped with the row.
- `dynamic { }`: the escape hatch. The block is re-run at every dirty flush and its subtree is
  diffed against what the client has, so `if` and `for` inside it work. Costs a subtree diff per
  change; use it where `show` and `each` are awkward.

Dev mode flags a `state` read at build time outside a binding, since that is always a frozen `if`.

## Status

| Piece                                                                | State   |
| -------------------------------------------------------------------- | ------- |
| `state`, bindings, per-tick flush, guaranteed event reply            | done    |
| Node tree, validated ids, markup emitter                             | done    |
| Page host over `InteractiveCustomUIPage`, event codec, handler table | done    |
| `page`, `group`, `text`, `button`, `onClick`                         | done    |
| Named styles via `Styles.ui`, page chrome via `Frame.ui`             | done    |
| `StyleSheet` objects for custom style documents                      | next    |
| `show` / `otherwise`                                                 | next    |
| `each` with keyed diff, `Row<T>`, `listState`                        | next    |
| `styleBy` and raw style lambdas with property diffing                | planned |
| Two-way inputs: `textField`, `numberField`, `slider`, debounce       | planned |
| Typed style values, state blocks, generated `Styles.ui`              | planned |
| HUD host over `CustomUIHud`                                          | planned |
| `dynamic { }` re-run blocks                                          | planned |
| Hot reload with state snapshots, dev-mode checks                     | planned |
| Java-friendly surface (`fun interface`s, `@JvmOverloads`)            | planned |

## Layout

```
src/main/kotlin/io/github/msilycanthropy/symphonia/
  ui/core     State, Binding, PageRuntime, Node, PropValue   (no Hytale imports)
  ui/hytale   Markup emitter, HostedPage                     (the only Hytale-facing code)
  ui/dsl      page/group/text/button builders, Style refs
  CounterPage.kt   the reference page, opened with /counter
src/main/resources/Common/UI/Custom/Symphonia/
  Styles.ui, Frame.ui
```

## Developing

```
mise exec -- ./gradlew test        # unit tests, no server needed
mise exec -- ./gradlew runServer   # dev server with the plugin staged; then /counter in game
```

Kotlin's stdlib is staged onto the dev server by `vineImplementation`. A distributable jar will
need it shaded in; that step does not exist yet.
