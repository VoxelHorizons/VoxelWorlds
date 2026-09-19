package com.voxelhorizons.voxelworlds.service;

import com.voxelhorizons.voxelworlds.VoxelWorlds;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class WorldRegenerationService {

    private final VoxelWorlds plugin;
    private final File stateFile;
    private final YamlConfiguration state;
    private BukkitTask task;
    private boolean regenerationRunning;

    public WorldRegenerationService(VoxelWorlds plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "regeneration.yml");
        this.state = YamlConfiguration.loadConfiguration(stateFile);
    }

    public void start() {
        stop();
        initialiseMissingSchedules();
        long checkTicks = Math.max(20L, plugin.getConfig().getLong("regeneration.check-interval-ticks", 1200L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                checkDueWorlds();
            }
        }, checkTicks, checkTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void reload() {
        initialiseMissingSchedules();
    }

    public boolean regenerateNow(String worldName) {
        ConfigurationSection section = worldSection(worldName);
        if (section == null) {
            return false;
        }
        return beginRegeneration(worldName, section, true);
    }

    public long nextRegeneration(String worldName) {
        return state.getLong(statePath(worldName) + ".next", 0L);
    }

    public List<String> configuredWorlds() {
        ConfigurationSection worlds = plugin.getConfig().getConfigurationSection("regeneration.worlds");
        if (worlds == null) {
            return Collections.emptyList();
        }
        return new ArrayList<String>(worlds.getKeys(false));
    }

    public static long parseIntervalMillis(String value) {
        if (value == null) {
            throw new IllegalArgumentException("interval cannot be null");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 2) {
            throw new IllegalArgumentException("invalid interval '" + value + "'");
        }

        long multiplier;
        String number;
        if (normalized.endsWith("ms")) {
            multiplier = 1L;
            number = normalized.substring(0, normalized.length() - 2);
        } else {
            char unit = normalized.charAt(normalized.length() - 1);
            number = normalized.substring(0, normalized.length() - 1);
            switch (unit) {
                case 's':
                    multiplier = 1000L;
                    break;
                case 'm':
                    multiplier = TimeUnit.MINUTES.toMillis(1L);
                    break;
                case 'h':
                    multiplier = TimeUnit.HOURS.toMillis(1L);
                    break;
                case 'd':
                    multiplier = TimeUnit.DAYS.toMillis(1L);
                    break;
                default:
                    throw new IllegalArgumentException("invalid interval unit in '" + value + "'");
            }
        }

        long amount;
        try {
            amount = Long.parseLong(number);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid interval '" + value + "'");
        }

        if (amount <= 0L) {
            throw new IllegalArgumentException("interval must be greater than zero");
        }

        try {
            return Math.multiplyExact(amount, multiplier);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("interval is too large");
        }
    }

    private void initialiseMissingSchedules() {
        long now = System.currentTimeMillis();
        boolean changed = false;

        for (String worldName : configuredWorlds()) {
            ConfigurationSection section = worldSection(worldName);
            if (section == null || !section.getBoolean("enabled", false)) {
                continue;
            }

            String path = statePath(worldName) + ".next";
            if (!state.contains(path)) {
                try {
                    state.set(path, now + intervalMillis(section));
                    changed = true;
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().severe("Invalid regeneration interval for world '" + worldName
                            + "': " + exception.getMessage());
                }
            }
        }

        if (changed) {
            saveState();
        }
    }

    private void checkDueWorlds() {
        if (regenerationRunning) {
            return;
        }

        long now = System.currentTimeMillis();
        for (String worldName : configuredWorlds()) {
            ConfigurationSection section = worldSection(worldName);
            if (section == null || !section.getBoolean("enabled", false)) {
                continue;
            }

            long next = state.getLong(statePath(worldName) + ".next", 0L);
            if (next <= 0L) {
                try {
                    state.set(statePath(worldName) + ".next", now + intervalMillis(section));
                    saveState();
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().severe("Invalid regeneration interval for world '" + worldName
                            + "': " + exception.getMessage());
                }
                continue;
            }

            if (now >= next && beginRegeneration(worldName, section, false)) {
                return;
            }
        }
    }

    private boolean beginRegeneration(final String worldName, final ConfigurationSection section, boolean manual) {
        if (regenerationRunning) {
            return false;
        }

        final Plugin multiverse = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (multiverse == null || !multiverse.isEnabled()) {
            plugin.getLogger().warning("Cannot regenerate '" + worldName + "': Multiverse-Core is not enabled.");
            return false;
        }

        final World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Cannot regenerate '" + worldName + "': the world is not loaded.");
            return false;
        }

        String evacuationName = section.getString("evacuation-world", "voxel_hub");
        final World evacuationWorld = Bukkit.getWorld(evacuationName);
        if (evacuationWorld == null || evacuationWorld.equals(world)) {
            plugin.getLogger().warning("Cannot regenerate '" + worldName + "': evacuation world '"
                    + evacuationName + "' is unavailable or is the same world.");
            return false;
        }

        regenerationRunning = true;

        if (section.getBoolean("broadcast", true)) {
            Bukkit.broadcastMessage("§e[VoxelWorlds] Regenerating §f" + worldName
                    + "§e. Players are being moved to §f" + evacuationName + "§e.");
        }

        Location evacuation = evacuationWorld.getSpawnLocation();
        for (Player player : new ArrayList<Player>(world.getPlayers())) {
            player.teleport(evacuation);
        }

        long delay = Math.max(1L, section.getLong("evacuation-delay-ticks", 20L));
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    regenerateWithMultiverse(worldName, section);
                } finally {
                    regenerationRunning = false;
                }
            }
        }, delay);

        if (manual) {
            plugin.getLogger().info("Manual regeneration started for '" + worldName + "'.");
        }
        return true;
    }

    private void regenerateWithMultiverse(String worldName, ConfigurationSection section) {
        try {
            Class<?> apiClass = Class.forName("org.mvplugins.multiverse.core.MultiverseCoreApi");
            Object api = apiClass.getMethod("get").invoke(null);
            Object worldManager = apiClass.getMethod("getWorldManager").invoke(api);

            Method getLoadedWorld = worldManager.getClass().getMethod("getLoadedWorld", String.class);
            Object option = getLoadedWorld.invoke(worldManager, worldName);
            Object loadedWorld = option.getClass().getMethod("getOrNull").invoke(option);
            if (loadedWorld == null) {
                throw new IllegalStateException("Multiverse does not have a loaded world named '" + worldName + "'");
            }

            Class<?> loadedWorldClass = Class.forName("org.mvplugins.multiverse.core.world.LoadedMultiverseWorld");
            Class<?> optionsClass = Class.forName("org.mvplugins.multiverse.core.world.options.RegenWorldOptions");
            Object options = optionsClass.getMethod("world", loadedWorldClass).invoke(null, loadedWorld);

            optionsClass.getMethod("keepWorldConfig", boolean.class).invoke(options,
                    section.getBoolean("keep-world-config", true));
            optionsClass.getMethod("keepGameRule", boolean.class).invoke(options,
                    section.getBoolean("keep-gamerules", true));
            optionsClass.getMethod("keepWorldBorder", boolean.class).invoke(options,
                    section.getBoolean("keep-world-border", true));

            if (section.getBoolean("random-seed", true)) {
                optionsClass.getMethod("randomSeed", boolean.class).invoke(options, true);
            } else if (section.contains("seed")) {
                optionsClass.getMethod("seed", String.class).invoke(options, section.getString("seed"));
            }

            List<String> keepFiles = section.getStringList("keep-files");
            if (!keepFiles.isEmpty()) {
                optionsClass.getMethod("keepFiles", List.class).invoke(options, keepFiles);
            }

            Method regenWorld = worldManager.getClass().getMethod("regenWorld", optionsClass);
            Object attempt = regenWorld.invoke(worldManager, options);
            boolean success = (Boolean) attempt.getClass().getMethod("isSuccess").invoke(attempt);

            if (!success) {
                Object message = attempt.getClass().getMethod("getFailureMessage").invoke(attempt);
                throw new IllegalStateException(String.valueOf(message));
            }

            long now = System.currentTimeMillis();
            long next = now + intervalMillis(section);
            String path = statePath(worldName);
            state.set(path + ".last", now);
            state.set(path + ".next", next);
            saveState();

            plugin.getLogger().info("Regenerated world '" + worldName + "' through Multiverse-Core. Next regeneration: "
                    + new java.util.Date(next));
            if (section.getBoolean("broadcast", true)) {
                Bukkit.broadcastMessage("§a[VoxelWorlds] §f" + worldName + " §ahas been regenerated.");
            }
        } catch (Exception exception) {
            plugin.getLogger().severe("Failed to regenerate world '" + worldName + "': "
                    + rootMessage(exception));
        }
    }

    private ConfigurationSection worldSection(String worldName) {
        return plugin.getConfig().getConfigurationSection("regeneration.worlds." + worldName);
    }

    private long intervalMillis(ConfigurationSection section) {
        return parseIntervalMillis(section.getString("interval", "7d"));
    }

    private String statePath(String worldName) {
        return "worlds." + worldName.toLowerCase(Locale.ROOT);
    }

    private void saveState() {
        try {
            state.save(stateFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save regeneration.yml: " + exception.getMessage());
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
