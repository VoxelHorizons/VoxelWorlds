# VoxelWorlds

World lifecycle utilities for VoxelHorizons.

## First world entry

VoxelWorlds can run console commands once when a player enters a configured
world for the first time. Entry is detected using Bukkit's world-change event,
so it works regardless of whether the player arrived through Multiverse-Portals,
a command, another plugin, or another teleport mechanism.

The default configuration is set up for the VoxelHorizons survival world:

```yaml
first-entry:
  realms:
    enabled: true
    delay-ticks: 1
    commands:
      - "rtp player_sudo {player} {world}"
```

With BetterRTP installed, the first time a player moves from `voxel_hub` (or
any other world) into `realms`, VoxelWorlds asks BetterRTP to randomly teleport
that player inside `realms`.

The action is not tied specifically to Multiverse-Portals. This prevents players
from bypassing the first-entry behavior by using another teleport method.

### Placeholders

- `{player}` - player name
- `{uuid}` - player UUID
- `{world}` - destination world

### Persistence

Completed first entries are stored by UUID in `plugins/VoxelWorlds/players.yml`.
The completion state is persisted before commands execute, preventing recursive
teleports from executing an action twice.

### Administration

```
/vw reload
/vw reset <player> <world>
```

The reset command is useful while testing or when an administrator intentionally
wants a player to receive a world's first-entry actions again.

Permission: `voxelworlds.admin`

## Compatibility

VoxelWorlds deliberately compiles against the Spigot 1.12.2 API and Java 8 because
the features it uses are stable Bukkit APIs. The same JAR is intended to run from
Minecraft 1.12.2 through current Paper releases, including 26.2. There is no
NMS/version-specific code.

BetterRTP and Multiverse are optional runtime integrations; VoxelWorlds does not
link against their APIs and executes configured commands through Bukkit.

## Building

```bash
mvn clean package
```

The resulting universal plugin JAR is written to `target/`.

## Automated builds and releases

Every push and pull request is compiled by GitHub Actions. Pushes to `main`
also publish a rolling GitHub prerelease named `latest`, containing the current
universal VoxelWorlds JAR. Version tags matching `v*` create normal versioned
GitHub releases.
