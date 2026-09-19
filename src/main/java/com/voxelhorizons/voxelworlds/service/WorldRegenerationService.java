package com.voxelhorizons.voxelworlds.service;

import com.voxelhorizons.voxelworlds.VoxelWorlds;
import com.voxelhorizons.voxelworlds.integration.VoxelCorePlaceholderBridge;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class WorldRegenerationService {

    private final VoxelWorlds plugin;
    private final File stateFile;
    private final YamlConfiguration state;
    private final VoxelCorePlaceholderBridge voxelCorePlaceholders;
    private final Set<String> lockedWorlds = new HashSet<String>();
    private final Map<UUID, Long> blockedMessageTimes = new HashMap<UUID, Long>();
    private BukkitTask task;
    private boolean regenerationRunning;

    public WorldRegenerationService(VoxelWorlds plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "regeneration.yml");
        this.state = YamlConfiguration.loadConfiguration(stateFile);
        this.voxelCorePlaceholders = new VoxelCorePlaceholderBridge(plugin);
    }

    public void start() {
        stop();
        initialiseMissingSchedules();
        long checkTicks = Math.max(20L, plugin.getConfig().getLong("regeneration.check-interval-ticks", 20L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                checkSchedules();
            }
        }, checkTicks, checkTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        lockedWorlds.clear();
        blockedMessageTimes.clear();
    }

    public void reload() {
        long now = System.currentTimeMillis();
        for (String worldName : configuredWorlds()) {
            ConfigurationSection section = worldSection(worldName);
            if (section == null || !section.getBoolean("enabled", false)) {
                continue;
            }

            try {
                long last = state.getLong(statePath(worldName) + ".last", 0L);
                long base = last > 0L ? last : now;
                setNext(worldName, base + intervalMillis(section));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().severe("Invalid regeneration interval for world '" + worldName
                        + "': " + exception.getMessage());
            }
        }
        saveState();
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

    public boolean isWorldLocked(String worldName) {
        return worldName != null && lockedWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public void redirectBlockedPlayer(final Player player, final String worldName) {
        if (player == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Long previous = blockedMessageTimes.get(player.getUniqueId());
        if (previous == null || now - previous.longValue() >= 2000L) {
            player.sendMessage(message("locked", worldName, null,
                    "&cThat world is temporarily closed while it regenerates."));
            blockedMessageTimes.put(player.getUniqueId(), now);
        }

        final ConfigurationSection section = worldSection(worldName);
        if (section == null) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                evacuatePlayer(player, section, worldName);

                // Essentials /spawn is normally asynchronous. Give it a moment,
                // then fall back to the configured evacuation world's Bukkit
                // spawn if the player is still inside the locked world.
                Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (!player.isOnline() || !player.getWorld().getName().equalsIgnoreCase(worldName)) {
                            return;
                        }

                        String evacuationName = section.getString("evacuation-world", "voxel_hub");
                        World evacuationWorld = Bukkit.getWorld(evacuationName);
                        if (evacuationWorld != null) {
                            player.teleport(evacuationWorld.getSpawnLocation());
                        }
                    }
                }, 10L);
            }
        });
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
                    setNext(worldName, now + intervalMillis(section));
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

    private void checkSchedules() {
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
                    setNext(worldName, now + intervalMillis(section));
                    saveState();
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().severe("Invalid regeneration interval for world '" + worldName
                            + "': " + exception.getMessage());
                }
                continue;
            }

            broadcastDueWarning(worldName, section, next, now);

            if (now >= next && beginRegeneration(worldName, section, false)) {
                return;
            }
        }
    }

    private void broadcastDueWarning(String worldName, ConfigurationSection section, long next, long now) {
        if (!section.getBoolean("broadcast", true)) {
            return;
        }

        long remaining = next - now;
        if (remaining <= 0L) {
            return;
        }

        List<Long> thresholds = warningThresholds(section);
        Long selected = null;
        for (Long threshold : thresholds) {
            if (remaining <= threshold.longValue() && !warningSent(worldName, threshold.longValue())) {
                if (selected == null || threshold.longValue() < selected.longValue()) {
                    selected = threshold;
                }
            }
        }

        if (selected == null) {
            return;
        }

        for (Long threshold : thresholds) {
            if (threshold.longValue() >= selected.longValue()) {
                markWarningSent(worldName, threshold.longValue());
            }
        }
        saveState();

        Bukkit.broadcastMessage(message("warning", worldName, formatDuration(remaining),
                "&e{world} will regenerate in &f{time}&e. Please leave the resource world."));
    }

    private List<Long> warningThresholds(ConfigurationSection section) {
        List<Long> values = new ArrayList<Long>();
        for (String value : section.getStringList("warning-times")) {
            try {
                values.add(parseIntervalMillis(value));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring invalid warning time '" + value + "': "
                        + exception.getMessage());
            }
        }
        Collections.sort(values, new Comparator<Long>() {
            @Override
            public int compare(Long left, Long right) {
                return Long.compare(right.longValue(), left.longValue());
            }
        });
        return values;
    }

    private boolean beginRegeneration(final String worldName, final ConfigurationSection section, boolean manual) {
        if (regenerationRunning || isWorldLocked(worldName)) {
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
        lockWorld(worldName);

        if (section.getBoolean("broadcast", true)) {
            Bukkit.broadcastMessage(message("starting", worldName, null,
                    "&6Regeneration of &f{world} &6is starting now. The world is temporarily closed."));
        }

        evacuateWorld(world, section, worldName);

        long delay = Math.max(1L, section.getLong("evacuation-delay-ticks", 40L));
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                forceEvacuateRemaining(world, evacuationWorld, worldName);

                Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (!world.getPlayers().isEmpty()) {
                            plugin.getLogger().severe("Aborting regeneration of '" + worldName
                                    + "' because " + world.getPlayers().size() + " player(s) could not be evacuated.");
                            if (section.getBoolean("broadcast", true)) {
                                Bukkit.broadcastMessage(message("failed", worldName, null,
                                        "&cRegeneration of &f{world} &cwas cancelled because the world could not be emptied safely."));
                            }
                            unlockWorld(worldName);
                            regenerationRunning = false;
                            return;
                        }

                        try {
                            regenerateWithMultiverse(worldName, section);
                        } finally {
                            unlockWorld(worldName);
                            regenerationRunning = false;
                        }
                    }
                }, 5L);
            }
        }, delay);

        if (manual) {
            plugin.getLogger().info("Manual regeneration started for '" + worldName + "'.");
        }
        return true;
    }

    private void evacuateWorld(World world, ConfigurationSection section, String worldName) {
        if (section.getBoolean("broadcast", true)) {
            Bukkit.broadcastMessage(message("evacuating", worldName, null,
                    "&ePlayers in &f{world} &eare being sent to spawn."));
        }

        for (Player player : new ArrayList<Player>(world.getPlayers())) {
            evacuatePlayer(player, section, worldName);
        }
    }

    private void evacuatePlayer(Player player, ConfigurationSection section, String worldName) {
        String command = section.getString("evacuation-command", "spawn {player}");
        if (command != null && !command.trim().isEmpty()) {
            String resolved = command
                    .replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString())
                    .replace("{world}", worldName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }
    }

    private void forceEvacuateRemaining(World world, World evacuationWorld, String worldName) {
        Location fallback = evacuationWorld.getSpawnLocation();
        for (Player player : new ArrayList<Player>(world.getPlayers())) {
            player.sendMessage(message("forced-evacuation", worldName, null,
                    "&eYou are being moved to spawn while &f{world} &eregenerates."));
            player.teleport(fallback);
        }
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
            setNext(worldName, next);
            saveState();

            plugin.getLogger().info("Regenerated world '" + worldName + "' through Multiverse-Core. Next regeneration: "
                    + new java.util.Date(next));
            if (section.getBoolean("broadcast", true)) {
                Bukkit.broadcastMessage(message("complete", worldName, formatDuration(intervalMillis(section)),
                        "&aRegeneration of &f{world} &ais complete. The world is open again."));
            }
        } catch (Exception exception) {
            plugin.getLogger().severe("Failed to regenerate world '" + worldName + "': "
                    + rootMessage(exception));
            if (section.getBoolean("broadcast", true)) {
                Bukkit.broadcastMessage(message("failed", worldName, null,
                        "&cRegeneration of &f{world} &cfailed. The world has been reopened."));
            }
        }
    }

    private void lockWorld(String worldName) {
        lockedWorlds.add(worldName.toLowerCase(Locale.ROOT));
    }

    private void unlockWorld(String worldName) {
        lockedWorlds.remove(worldName.toLowerCase(Locale.ROOT));
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

    private void setNext(String worldName, long next) {
        String path = statePath(worldName);
        state.set(path + ".next", next);
        state.set(path + ".warnings", null);
    }

    private boolean warningSent(String worldName, long threshold) {
        return state.getBoolean(statePath(worldName) + ".warnings." + threshold, false);
    }

    private void markWarningSent(String worldName, long threshold) {
        state.set(statePath(worldName) + ".warnings." + threshold, true);
    }

    private String message(String key, String worldName, String time, String fallback) {
        String prefix = plugin.getConfig().getString("regeneration.messages.prefix",
                "&6[Resource Reset] &r");
        String body = plugin.getConfig().getString("regeneration.messages." + key, fallback);
        if (body == null) {
            body = fallback;
        }

        String value = prefix + body;
        value = value.replace("{world}", worldName == null ? "" : worldName);
        value = value.replace("{time}", time == null ? "" : time);

        // Resolve VoxelCore's :alias: and :offset_*: placeholders before
        // applying legacy colour codes, matching VoxelCore's normal chat path.
        value = voxelCorePlaceholders.resolve(value);
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    static String formatDuration(long millis) {
        long totalSeconds = Math.max(1L, (millis + 999L) / 1000L);
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (days > 0L) {
            return days + "d" + (hours > 0L ? " " + hours + "h" : "");
        }
        if (hours > 0L) {
            return hours + "h" + (minutes > 0L ? " " + minutes + "m" : "");
        }
        if (minutes > 0L) {
            return minutes + "m" + (seconds > 0L ? " " + seconds + "s" : "");
        }
        return seconds + "s";
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
