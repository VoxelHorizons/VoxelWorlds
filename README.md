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
  check-interval-ticks: 20

  messages:
    prefix: "&6[Resource Reset] &r"
    warning: "&e{world} will regenerate in &f{time}&e. Please leave the resource world."
    starting: "&6Regeneration of &f{world} &6is starting now. The world is temporarily closed."
    evacuating: "&ePlayers in &f{world} &eare being sent to spawn."
    forced-evacuation: "&eYou are being moved to spawn while &f{world} &eregenerates."
    locked: "&cThat world is temporarily closed while it regenerates."
    complete: "&aRegeneration of &f{world} &ais complete. The world is open again."
    failed: "&cRegeneration of &f{world} &cfailed. The world has been reopened."

  worlds:
    resource:
      enabled: true
      interval: 7d
      warning-times: [1h, 30m, 10m, 5m, 1m, 30s, 10s]
      evacuation-command: "spawn {player}"
      evacuation-world: voxel_hub
      evacuation-delay-ticks: 40
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

### VoxelCore text placeholders

When VoxelCore is installed, the regeneration prefix and every regeneration message are passed through
VoxelCore's `TextPlaceholderService`. This means the same VoxelCore font aliases and spacing placeholders used in
chat/UI text are supported here too, including values such as `:smile:`, `:staff:`, GUI aliases, and
`:offset_-16:`.

VoxelWorlds first resolves its own `{world}` and `{time}` tokens, then asks VoxelCore to resolve its
`:placeholder:` aliases, and finally applies legacy `&` colour codes.

For example:

```yaml
regeneration:
  messages:
    prefix: ":server_logo: &6[Resource Reset] &r"
    warning: ":warning: &e{world} will regenerate in &f{time}&e."
```

VoxelCore is a soft dependency. If it is absent or disabled, VoxelWorlds continues normally and leaves unknown
VoxelCore aliases untouched.

Before an automatic regeneration, VoxelWorlds broadcasts configurable countdown warnings using a dedicated
`[Resource Reset]` prefix. The default warnings are sent at 1 hour, 30 minutes, 10 minutes, 5 minutes,
1 minute, 30 seconds, and 10 seconds.

When regeneration begins, VoxelWorlds:

1. Confirms Multiverse-Core, the target world, and evacuation world are available.
2. Locks the target world against incoming teleports.
3. Cancels Multiverse-Portals, `/mvtp`, command, and plugin teleports into the locked world.
4. Sends players in the target world through the configured evacuation command. By default this is
   `spawn {player}`, which uses EssentialsX's configured spawn when EssentialsSpawn is installed.
5. Falls back to the Bukkit spawn of `evacuation-world` for anyone who remains in the target world.
6. Verifies that the target world contains zero players. If it cannot be emptied, regeneration is cancelled.
7. Calls Multiverse-Core's world regeneration lifecycle.
8. Preserves the configured Multiverse world settings, gamerules and border.
9. Unlocks the world and broadcasts completion (or failure).
10. Records a successful regeneration and schedules the next interval.

The teleport lock is intentionally world-level rather than tied to a specific Multiverse-Portal. This means a
player standing in a portal cannot re-enter the world during regeneration, and other teleport mechanisms cannot
bypass the lock either.

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

`/vw regen` starts the same lock, evacuation, empty-world verification, and Multiverse regeneration flow
immediately. A successful manual regeneration also resets that world's interval. Scheduled resets receive the
configured countdown warnings; manual resets intentionally begin immediately after the administrator command.

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
