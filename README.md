# Pantheon

A [Fabric](https://fabricmc.net/) mod for Minecraft that gives every player on the server **one shared inventory**. Storage and hotbar are always shared; the crafting grid, armor and offhand can be too. Pick something up as one player, and it's instantly available to the rest of the party.

## Features

- **One inventory for everyone** — the 36 main/hotbar slots are backed by a single shared storage, so any item any player picks up or crafts is visible and usable by the whole server immediately.
- **Optional sharing for crafting grid, armor and offhand** — each can be toggled independently in the settings (see below). Main storage and hotbar are always shared; these three are opt-in.
- **Independent aim, shared gear** — each player keeps their own selected hotbar slot, so two players can hold two different (shared) items at once.
- **Optional hotbar slot ownership** — when enabled, whichever hotbar slot a player currently has selected is exclusively "theirs" until they switch off it. Nobody else can take from, place into, or otherwise touch that slot — not by clicking it directly, shift-clicking something onto it, pressing a number key while hovering another slot, swapping hands, or dropping — and nobody else can select it themselves either: pressing its number key does nothing, and scrolling past it jumps straight over to the next free slot in that direction instead of landing on it or getting stuck. Enabling this caps the server at **9 players** (one per hotbar slot); the 10th+ connection is rejected with a clear reason.
- **Colors match the locator bar** — each player's lock-frame color is computed from their UUID the exact same way vanilla's locator bar dots are, so the same player is always the same color in both places, server-wide, with no extra networking needed.
- **`keepInventory` is forced on** — a shared inventory can't survive one player's death clearing it for everyone, so the mod sets the `keepInventory` game rule to `true` on server start.
- **Stateless by design** — the shared inventory lives in memory and resets every time the server starts, so it never conflicts with per-player save data.

## Settings

Settings are server-authoritative (they affect every connected player identically) and can be changed two ways:

- **In-game**: press the "Open Pantheon Settings" key (unbound by default — bind it in Controls) to open the settings screen. Anyone can open it to view the current settings, but only the singleplayer host or a server operator can actually save changes.
- **Command**: `/pantheon config` shows current settings; `/pantheon config <setting> <value>` changes one (op-only). Settings: `syncCraftingGrid`, `syncArmor`, `syncOffhand`, `enableHotbarOwnership` (all booleans).

Settings persist in `config/pantheon.json`. Changing `syncCraftingGrid`/`syncArmor`/`syncOffhand` takes effect for players as they (re)join; `enableHotbarOwnership` takes effect immediately.

## Requirements

| | |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | ≥ 0.19.5 |
| Fabric API | required |
| Java | ≥ 25 |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for the same version.
3. Grab the latest `pantheon-*.jar` from [Releases](https://github.com/IlarOrlov/pantheon-mod/releases) and drop it into your server's (or client's, for singleplayer/LAN) `mods` folder.

The mod needs to be installed on the server for multiplayer; clients need it too, to see the shared hotbar's colored lock frames and to use the settings screen.

## How it works

Every `ServerPlayer`'s `Inventory` is wired via a mixin (see `com.pantheon.mixin`) to point at the same backing item list instead of its own, so writes from one player are immediately visible to all. Armor/offhand sharing and the crafting-grid sharing are gated by `PantheonConfig` and applied the same way.

Hotbar-slot locking has two layers. In the inventory/container GUI, `HotbarLockMixin` cancels a click outright if it's directly on a locked slot (hovering it and pressing Q, F, a number key, or clicking it) — this has to happen *before* vanilla processes it, since dropping an item or swapping to the offhand has an external side effect (a dropped entity, a written offhand slot) that reverting the slot afterward can't undo without leaving a duplicate behind. As a backstop for indirect landings vanilla picks internally (e.g. shift-clicking a stack in from a chest), the 9 hotbar slots and the cursor are also snapshotted before the click and restored if a slot locked to someone else changed anyway. Outside a GUI, selecting a slot by scrolling skips over a locked one to the next free slot in the same direction, and pressing its number key does nothing (`com.pantheon.client.mixin.HotbarScrollLockMixin`/`HotbarNumberKeyLockMixin`), with a server-side reject (`HotbarSelectionCapMixin`) as a backstop against a client that skips the check.

## License

Released under [CC0 1.0](LICENSE) — public domain. Use it, learn from it, fork it.

## Author

Ilar Orlov ([Feanor](https://github.com/IlarOrlov))
