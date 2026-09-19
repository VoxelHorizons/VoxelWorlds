package com.voxelhorizons.voxelworlds.listener;

import com.voxelhorizons.voxelworlds.service.WorldEntryService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

public final class WorldEntryListener implements Listener {

    private final WorldEntryService service;

    public WorldEntryListener(WorldEntryService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChanged(PlayerChangedWorldEvent event) {
        service.handleEntry(event.getPlayer());
    }
}
