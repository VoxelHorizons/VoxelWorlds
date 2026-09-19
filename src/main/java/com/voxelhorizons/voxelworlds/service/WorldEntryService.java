package com.voxelhorizons.voxelworlds.service;

import com.voxelhorizons.voxelworlds.VoxelWorlds;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class WorldEntryService {

    private final VoxelWorlds plugin;
    private final File dataFile;
    private final YamlConfiguration data;

    public WorldEntryService(VoxelWorlds plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "players.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void handleWorldChange(Player player, World fromWorld) {
        String destinationWorld = player.getWorld().getName();

        // PlayerChangedWorldEvent fires after the move, but still exposes the
        // world the player came from. At this point the player's current
        // location is already in the destination, so the source position must
        // be captured separately before the teleport (see handleTeleport).
        handleEntry(player, destinationWorld);
    }

    public void handleTeleportFrom(Player player, Location from) {
        if (from == null || from.getWorld() == null) {
            return;
        }

        saveLastLocation(player.getUniqueId(), from);
    }

    public void handleEntry(Player player, String world) {
        String configPath = "first-entry." + world;

        if (!plugin.getConfig().getBoolean(configPath + ".enabled", false)) {
            return;
        }

        if (!hasVisited(player.getUniqueId(), world)) {
            // Persist before executing actions. If an action teleports the player,
            // the resulting world-change event cannot trigger this entry again.
            markVisited(player.getUniqueId(), world);

            long delay = Math.max(0L, plugin.getConfig().getLong(configPath + ".delay-ticks", 1L));
            List<String> commands = plugin.getConfig().getStringList(configPath + ".commands");

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || !player.getWorld().getName().equals(world)) {
                    return;
                }

                for (String command : commands) {
                    String resolved = command
                            .replace("{player}", player.getName())
                            .replace("{uuid}", player.getUniqueId().toString())
                            .replace("{world}", world);

                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                }
            }, delay);
            return;
        }

        if (!plugin.getConfig().getBoolean(configPath + ".return-to-last-location", true)) {
            return;
        }

        Location lastLocation = getLastLocation(player.getUniqueId(), world);
        if (lastLocation == null) {
            return;
        }

        // Multiverse has already placed the player at its portal destination
        // (normally the world's spawn). Restore their saved position one tick
        // later so our destination wins without interfering with the portal event.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.getWorld().getName().equals(world)) {
                player.teleport(lastLocation);
            }
        }, 1L);
    }

    public boolean hasVisited(UUID uuid, String world) {
        return data.getBoolean(visitedPath(uuid, world), false);
    }

    public void markVisited(UUID uuid, String world) {
        data.set(visitedPath(uuid, world), true);
        save();
    }

    public void saveLastLocation(UUID uuid, Location location) {
        if (location.getWorld() == null) {
            return;
        }

        String path = locationPath(uuid, location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        data.set(path + ".yaw", location.getYaw());
        data.set(path + ".pitch", location.getPitch());
        save();
    }

    public Location getLastLocation(UUID uuid, String worldName) {
        String path = locationPath(uuid, worldName);
        if (!data.contains(path + ".x")) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        return new Location(
                world,
                data.getDouble(path + ".x"),
                data.getDouble(path + ".y"),
                data.getDouble(path + ".z"),
                (float) data.getDouble(path + ".yaw"),
                (float) data.getDouble(path + ".pitch")
        );
    }

    /**
     * Clears all first-entry state for one player/world pair.
     *
     * This intentionally removes both the visited flag and the saved
     * last-location. A reset must never send a player back to coordinates from
     * an older incarnation of a regenerated world.
     */
    public boolean reset(UUID uuid, String world) {
        String path = playerWorldPath(uuid, world);
        if (!data.contains(path)) {
            return false;
        }

        data.set(path, null);
        save();
        return true;
    }

    /**
     * Clears first-entry state for every recorded player in a world.
     *
     * Used after a successful resource-world regeneration so the next entry
     * into the fresh world reruns that world's configured first-entry commands
     * and cannot restore a stale location from the previous world generation.
     *
     * @return number of player world-state records removed
     */
    public int resetWorld(String world) {
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            return 0;
        }

        String normalizedWorld = world.toLowerCase(Locale.ROOT);
        int reset = 0;
        for (String playerId : players.getKeys(false)) {
            String path = "players." + playerId + ".worlds." + normalizedWorld;
            if (!data.contains(path)) {
                continue;
            }

            data.set(path, null);
            reset++;
        }

        if (reset > 0) {
            save();
        }
        return reset;
    }

    private String visitedPath(UUID uuid, String world) {
        return playerWorldPath(uuid, world) + ".visited";
    }

    private String locationPath(UUID uuid, String world) {
        return playerWorldPath(uuid, world) + ".last-location";
    }

    private String playerWorldPath(UUID uuid, String world) {
        return "players." + uuid + ".worlds." + world.toLowerCase(Locale.ROOT);
    }

    private void save() {
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save players.yml: " + exception.getMessage());
        }
    }
}
