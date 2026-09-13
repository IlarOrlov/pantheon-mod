# Pantheon

A [Fabric](https://fabricmc.net/) mod for Minecraft that gives every player on the server **one shared inventory**. Storage and hotbar are always shared; the crafting grid, armor and offhand can be too. Pick something up as one player, and it's instantly available to the rest of the party.

## Features

- **One inventory for everyone** — the 36 main/hotbar slots are backed by a single shared storage, so any item any player picks up or crafts is visible and usable by the whole server immediately.
- **Optional sharing for crafting grid, armor and offhand** — each can be toggled independently in the settings (see below). Main storage and hotbar are always shared; these three are opt-in.
- **Independent aim, shared gear** — each player keeps their own selected hotbar slot, so two players can hold two different (shared) items at once.
- **Optional hotbar slot ownership** — when enabled, each hotbar slot can be exclusively "owned" by one online player at a time (shown as a colored frame around that slot, in the HUD and in every inventory-style screen), and nobody else can take from, place into, or otherwise touch that slot — not by clicking it directly, shift-clicking something onto it, pressing a number key while hovering another slot, swapping hands, or dropping. Two ownership modes:
  - **Currently selected slot** (default) — a player's owned slot follows whichever hotbar slot they have selected right now.
  - **Fixed per-player slot** — each player is assigned one slot for as long as they stay connected.
  - Enabling this caps the server at **9 players** (one per hotbar slot); the 10th+ connection is rejected with a clear reason.
- **`keepInventory` is forced on** — a shared inventory can't survive one player's death clearing it for everyone, so the mod sets the `keepInventory` game rule to `true` on server start.
- **Stateless by design** — the shared inventory lives in memory and resets every time the server starts, so it never conflicts with per-player save data.

## Settings

Settings are server-authoritative (they affect every connected player identically) and can be changed two ways:

- **In-game**: press the "Open Pantheon Settings" key (unbound by default — bind it in Controls) to open the settings screen. Anyone can open it to view the current settings, but only the singleplayer host or a server operator can actually save changes.
- **Command**: `/pantheon config` shows current settings; `/pantheon config <setting> <value>` changes one (op-only). Settings: `syncCraftingGrid`, `syncArmor`, `syncOffhand`, `enableHotbarOwnership` (booleans), `hotbarOwnershipMode` (`selected` or `fixed`).

Settings persist in `config/pantheon.json`. Changing `syncCraftingGrid`/`syncArmor`/`syncOffhand` takes effect for players as they (re)join; `enableHotbarOwnership` and its mode take effect immediately.

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

Every `ServerPlayer`'s `Inventory` is wired via a mixin (see `com.pantheon.mixin`) to point at the same backing item list instead of its own, so writes from one player are immediately visible to all. Armor/offhand sharing and the crafting-grid sharing are gated by `PantheonConfig` and applied the same way. Hotbar-slot locking snapshots the shared hotbar (and the cursor) before every container click and reverts the whole click if it touched a slot locked to a different online player — this covers every input path (click, shift-click, number-key swap, drop, swap-hands) without needing to special-case each one — see `com.pantheon.mixin.HotbarLockMixin` for the details.

## License

Released under [CC0 1.0](LICENSE) — public domain. Use it, learn from it, fork it.

## Author

Ilar Orlov ([Feanor](https://github.com/IlarOrlov))
