# Elaeste Structures 2.0

A Spigot 1.21.11 plugin source project with ten new procedural fantasy structures:

1. `castle`
2. `mushroom_loft`
3. `fantasy_tower`
4. `magical_keep`
5. `dragon_fortress`
6. `sky_kingdom`
7. `elven_city`
8. `cursed_manor`
9. `dwarven_mine`
10. `desert_temple`

Old structure types are not included in this generator. Structure chests are populated with real Minecraft ItemStacks and tiered loot. **Elytra is intentionally absent from every loot pool.** Other requested rare loot remains represented.

## Commands

- `/elaeste status`
- `/elaeste reload`
- `/elaeste generate [type]`
- `/elaeste locate [type]`

`locate` searches the plugin's saved generated-structure registry. It does not search vanilla Minecraft structures.

## Build

Requires JDK 21 and Maven:

```bash
mvn --batch-mode clean package
```

Output: `target/ElaesteStructures-2.0.0.jar`

Install the JAR in the server's `plugins` folder while the server is stopped. Back up the world first.

## Important implementation note

These are procedural block-built structures, not imported schematic builds. The source has not been run against a live Minecraft server in this environment. Test on a copy of the world before production use. Large structure placement is synchronous and may cause a short server tick pause.
