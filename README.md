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

## Scheduled world regeneration

VoxelWorlds can automatically regenerate Multiverse-managed resource worlds on
a persistent interval. The default configuration regenerates `resource` once
per week:

```yaml
regeneration:
  check-interval-ticks: 1200
  worlds:
    resource:
      enabled: true
      interval: 7d
      evacuation-world: voxel_hub
      evacuation-delay-ticks: 20
      random-seed: true
      keep-world-config: true
      keep-gamerules: true
      keep-world-border: true
      keep-files: []
      broadcast: true
```

Intervals support `ms`, `s`, `m`, `h`, and `d`, such as `30m`,
`12h`, or `7d`. The next regeneration timestamp is persisted in
`plugins/VoxelWorlds/regeneration.yml`, so restarting the server does not
restart the interval.

When a regeneration becomes due, VoxelWorlds:

1. Confirms Multiverse-Core, the target world, and evacuation world are available.
2. Moves all players out of the target world.
3. Waits the configured evacuation delay.
4. Calls Multiverse-Core's world regeneration lifecycle.
5. Preserves the configured Multiverse world settings, gamerules and border.
6. Records the successful regeneration and schedules the next interval.

With `random-seed: true`, each regeneration creates a fresh seed. Set it to
`false` to retain the current seed, or additionally configure `seed:` to use
a fixed numeric or text seed.

VoxelWorlds deliberately uses the Multiverse 5 API reflectively at runtime. This
allows the project to retain its Java 8 / Spigot 1.12 compile target while still
using Multiverse's own regeneration implementation on current servers. If the
required Multiverse regeneration API is unavailable, the operation fails safely
without deleting the world.

### Regeneration administration

```
/vw regen <world>
/vw next <world>
/vw reload
```

`/vw regen` starts the same safe evacuation and Multiverse regeneration flow
immediately. A successful manual regeneration also resets that world's interval.

## Compatibility

VoxelWorlds deliberately compiles against the Spigot 1.12.2 API and Java 8 because
the features it uses are stable Bukkit APIs. The same JAR is intended to run from
Minecraft 1.12.2 through current Paper releases, including 26.2. There is no
NMS/version-specific code.

BetterRTP remains an optional command-based runtime integration. Multiverse-Core
is optional for first-entry behavior, but is required for configured automatic
world regeneration. Regeneration uses Multiverse's runtime API without adding a
compile-time dependency.

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
