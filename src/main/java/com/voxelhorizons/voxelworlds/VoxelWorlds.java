package com.voxelhorizons.voxelworlds;

import com.voxelhorizons.voxelworlds.command.VoxelWorldsCommand;
import com.voxelhorizons.voxelworlds.listener.WorldEntryListener;
import com.voxelhorizons.voxelworlds.service.WorldEntryService;
import com.voxelhorizons.voxelworlds.service.WorldRegenerationService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VoxelWorlds extends JavaPlugin {

    private WorldEntryService worldEntryService;
    private WorldRegenerationService worldRegenerationService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        worldEntryService = new WorldEntryService(this);
        worldRegenerationService = new WorldRegenerationService(this, worldEntryService);
        worldRegenerationService.start();

        getServer().getPluginManager().registerEvents(
                new WorldEntryListener(worldEntryService, worldRegenerationService), this);

        PluginCommand command = getCommand("voxelworlds");
        if (command != null) {
            VoxelWorldsCommand executor = new VoxelWorldsCommand(this, worldEntryService, worldRegenerationService);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("VoxelWorlds enabled.");
    }

    @Override
    public void onDisable() {
        if (worldRegenerationService != null) {
            worldRegenerationService.stop();
        }
    }

    public WorldEntryService getWorldEntryService() {
        return worldEntryService;
    }

    public WorldRegenerationService getWorldRegenerationService() {
        return worldRegenerationService;
    }
}
