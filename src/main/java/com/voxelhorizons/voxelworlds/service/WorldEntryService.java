package com.voxelhorizons.voxelworlds.service;

import com.voxelhorizons.voxelworlds.VoxelWorlds;
import org.bukkit.Bukkit;
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

    public void handleEntry(Player player) {
        String world = player.getWorld().getName();
        String configPath = "first-entry." + world;

        if (!plugin.getConfig().getBoolean(configPath + ".enabled", false)) {
            return;
        }

        if (hasVisited(player.getUniqueId(), world)) {
            return;
        }

        // Persist before executing actions. If an action teleports the player,
        // the resulting world-change event cannot trigger this entry again.
        markVisited(player.getUniqueId(), world);

        long delay = Math.max(0L, plugin.getConfig().getLong(configPath + ".delay-ticks", 1L));
        List<String> commands = plugin.getConfig().getStringList(configPath + ".commands");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
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
    }

    public boolean hasVisited(UUID uuid, String world) {
        return data.getBoolean(path(uuid, world), false);
    }

    public void markVisited(UUID uuid, String world) {
        data.set(path(uuid, world), true);
        save();
    }

    public boolean reset(UUID uuid, String world) {
        String path = path(uuid, world);
        if (!data.contains(path)) {
            return false;
        }

        data.set(path, null);
        save();
        return true;
    }

    private String path(UUID uuid, String world) {
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
