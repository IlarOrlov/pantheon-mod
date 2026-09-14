# Pantheon

A [Fabric](https://fabricmc.net/) mod for Minecraft that gives every player on the server **one shared inventory**. Storage and hotbar are always shared; armor and offhand can be too. Pick something up as one player, and it's instantly available to the rest of the party.

## Features

- **One inventory for everyone** — the 36 main/hotbar slots are backed by a single shared storage, so any item any player picks up is visible and usable by the whole server immediately.
- **Optional sharing for armor and offhand** — each can be toggled independently in the settings (see below). Main storage and hotbar are always shared; these two are opt-in. The crafting grid is never shared — it stays personal to each player.
- **Independent aim, shared gear** — each player keeps their own selected hotbar slot, so two players can hold two different (shared) items at once.
- **Optional hotbar slot ownership** — when enabled, whichever hotbar slot a player currently has selected is exclusively "theirs" until they switch off it. Nobody else can take from, place into, or otherwise touch that slot — not by clicking it directly, shift-clicking something onto it, pressing a number key while hovering another slot, swapping hands, or dropping — and nobody else can select it themselves either: pressing its number key does nothing, and scrolling past it jumps straight over to the next free slot in that direction instead of landing on it or getting stuck. Enabling this caps the server at **9 players** (one per hotbar slot); the 10th+ connection is rejected with a clear reason.
- **Colors match the locator bar** — each player's lock-frame color is computed from their UUID the exact same way vanilla's locator bar dots are, so the same player is always the same color in both places, server-wide, with no extra networking needed.
- **Optional shared health and/or hunger** — two independent toggles ("Share health toggle" / hunger). With shared health on, every online player's health moves together (damage, healing, regen, everything) as one pool; if it hits 0, everyone dies at once, and the shared inventory is dropped and emptied for the whole server (once, not once per player). With shared hunger on, food level and saturation move together the same way. Either can be on without the other.
- **`keepInventory` is forced on** — a shared inventory can't survive one player's death clearing it for everyone, so the mod sets the `keepInventory` game rule to `true` on server start. The one deliberate exception is a shared-health wipe (above), which empties it on purpose, exactly once.
- **Stateless by design** — the shared inventory lives in memory and resets every time the server starts, so it never conflicts with per-player save data.

## Settings

Settings are server-authoritative (they affect every connected player identically) and can be changed two ways:

- **In-game**: press the "Open Pantheon Settings" key (unbound by default — bind it in Controls) to open the settings screen. Anyone can open it to view the current settings, but only the singleplayer host or a server operator can actually save changes.
- **Command**: `/pantheon config` shows current settings; `/pantheon config <setting> <value>` changes one (op-only). Settings: `syncArmor`, `syncOffhand`, `enableHotbarOwnership`, `syncHealth`, `syncHunger` (all booleans).

Settings persist in `config/pantheon.json`. Changing `syncArmor`/`syncOffhand` takes effect for players as they (re)join; the rest take effect immediately.

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

Every `ServerPlayer`'s `Inventory` is wired via a mixin (see `com.pantheon.mixin`) to point at the same backing item list instead of its own, so writes from one player are immediately visible to all. Armor/offhand sharing is gated by `PantheonConfig` and applied the same way, redirecting `PlayerEquipment`'s get/set per slot type.

`Inventory#load` normally starts by clearing the inventory before repopulating it from the joining player's own save file - for a ServerPlayer that would wipe the *shared* list down to just whatever that one player personally had saved, on every join. `InventorySharingMixin` skips that load entirely for ServerPlayers, so a join can never clobber what everyone else already has. On join, a player's restored hotbar selection is also checked against current ownership and moved to a free slot if it happens to already be someone else's (`HotbarOwnership#resolveJoinConflict`), and their inventory menu is force-synced immediately so they see the shared contents right away rather than waiting for the next tick's diff.

Hotbar-slot locking has two layers. In the inventory/container GUI, `HotbarLockMixin` cancels a click outright if it's directly on a locked slot (hovering it and pressing Q, F, a number key, or clicking it) — this has to happen *before* vanilla processes it, since dropping an item or swapping to the offhand has an external side effect (a dropped entity, a written offhand slot) that reverting the slot afterward can't undo without leaving a duplicate behind. As a backstop for indirect landings vanilla picks internally (e.g. shift-clicking a stack in from a chest), the 9 hotbar slots and the cursor are also snapshotted before the click and restored if a slot locked to someone else changed anyway. Outside a GUI, selecting a slot by scrolling skips over a locked one to the next free slot in the same direction, and pressing its number key does nothing (`com.pantheon.client.mixin.HotbarScrollLockMixin`/`HotbarNumberKeyLockMixin`), with a server-side reject (`HotbarSelectionCapMixin`) as a backstop against a client that skips the check.

Health and hunger aren't a shared reference the way inventory slots are — each player's is their own synced value — so `SharedStats` instead polls once a server tick: whichever online player's value no longer matches what was last synced to everyone is treated as the source of a fresh change (damage, healing, eating, or a fresh join adopting the existing pool), and that value is applied to everyone else. A shared-health pool reaching 0 kills every online player through the real death pipeline (`setHealth(0)` + `LivingEntity#die`, not `Entity#kill` - that one only force-removes the entity and never presents a respawn screen). The shared inventory is dropped and cleared exactly once for the whole event, not once per dying player, since `keepInventory` staying forced on would otherwise just let a wipe that's supposed to end the run survive it anyway. A freshly-joined player's health can briefly read as an uninitialized `0.0` for a tick or two before Minecraft properly sets it - since that's indistinguishable from a real death by value alone, a brand new player is never treated as a change source (or even tracked at all) until their health is observed to be a real, positive number at least once.

## License

Released under [CC0 1.0](LICENSE) — public domain. Use it, learn from it, fork it.

## Author

Ilar Orlov ([Feanor](https://github.com/IlarOrlov))
