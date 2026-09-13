# Pantheon

A [Fabric](https://fabricmc.net/) mod for Minecraft that gives every player on the server **one shared inventory**. Storage, hotbar, armor and offhand are the same 41 slots for everyone — pick something up as one player, and it's instantly available to the rest of the party.

## Features

- **One inventory for everyone** — the 36 main/hotbar slots, 4 armor slots and offhand are backed by a single shared storage, so any item any player picks up, crafts, or equips is visible and usable by the whole server immediately.
- **Independent aim, shared gear** — each player keeps their own selected hotbar slot, so two players can hold two different (shared) items at once.
- **Hotbar owner tags** — a small colored tag with the owning player's initial is drawn above each hotbar slot, showing at a glance who is "currently" attached to that slot. This is purely cosmetic; every slot still works for every player regardless of the tag.
- **Stateless by design** — the shared inventory lives in memory and resets every time the server starts, so it never conflicts with per-player save data.

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

The mod only needs to be installed on the server for multiplayer — clients only need it to see the hotbar owner tags.

## How it works

Every `ServerPlayer`'s `Inventory` and `PlayerEquipment` are wired via mixins (see `com.pantheon.mixin`) to point at the same backing item lists instead of their own, so writes from one player are immediately visible to all. The server also periodically broadcasts which online player is "assigned" to each hotbar slot (ordered by UUID, so it's stable and identical on every client) purely so the client-side HUD overlay can render the owner tags — it has no effect on gameplay.

## License

Released under [CC0 1.0](LICENSE) — public domain. Use it, learn from it, fork it.

## Author

Ilar Orlov ([Feanor](https://github.com/IlarOrlov))
