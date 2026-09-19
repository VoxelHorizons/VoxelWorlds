package com.voxelhorizons.voxelworlds.integration;

import com.voxelhorizons.voxelworlds.VoxelWorlds;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Optional runtime bridge to VoxelCore's TextPlaceholderService.
 *
 * VoxelWorlds deliberately avoids a compile-time VoxelCore dependency so its
 * existing Java 8 / Spigot 1.12 build remains universal.
 */
public final class VoxelCorePlaceholderBridge {

    private final VoxelWorlds plugin;
    private Plugin cachedPlugin;
    private Object placeholderService;
    private Method resolveMethod;
    private boolean warned;

    public VoxelCorePlaceholderBridge(VoxelWorlds plugin) {
        this.plugin = plugin;
    }

    public String resolve(String input) {
        if (input == null || input.indexOf(':') < 0) {
            return input;
        }

        Plugin voxelCore = plugin.getServer().getPluginManager().getPlugin("VoxelCore");
        if (voxelCore == null || !voxelCore.isEnabled()) {
            clearCache();
            return input;
        }

        try {
            if (voxelCore != cachedPlugin || placeholderService == null || resolveMethod == null) {
                Method getter = voxelCore.getClass().getMethod("getTextPlaceholderService");
                Object service = getter.invoke(voxelCore);
                if (service == null) {
                    return input;
                }

                Method resolver = service.getClass().getMethod("resolve", String.class);
                cachedPlugin = voxelCore;
                placeholderService = service;
                resolveMethod = resolver;
            }

            Object resolved = resolveMethod.invoke(placeholderService, input);
            if (resolved instanceof String) {
                warned = false;
                return (String) resolved;
            }
        } catch (ReflectiveOperationException exception) {
            warnOnce(exception);
            clearCache();
        } catch (LinkageError error) {
            warnOnce(error);
            clearCache();
        }

        return input;
    }

    private void warnOnce(Throwable throwable) {
        if (warned) {
            return;
        }
        warned = true;
        plugin.getLogger().warning("Unable to resolve VoxelCore text placeholders: "
                + (throwable.getMessage() == null
                ? throwable.getClass().getSimpleName()
                : throwable.getMessage()));
    }

    private void clearCache() {
        cachedPlugin = null;
        placeholderService = null;
        resolveMethod = null;
    }
}
