package me.shawlaf.varlight.spigot.util;

import lombok.experimental.UtilityClass;
import me.shawlaf.varlight.spigot.LightUpdateResult;
import me.shawlaf.varlight.spigot.VarLightPlugin;
import me.shawlaf.varlight.spigot.event.LightUpdateEvent;
import me.shawlaf.varlight.spigot.persistence.PersistentLightSource;
import me.shawlaf.varlight.spigot.persistence.WorldLightSourceManager;
import me.shawlaf.varlight.util.NumericMajorMinorVersion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import static me.shawlaf.varlight.spigot.LightUpdateResult.*;
import static me.shawlaf.varlight.spigot.util.IntPositionExtension.toIntPosition;

@UtilityClass
public class LightSourceUtil {

    public static final NumericMajorMinorVersion MC1_14 = new NumericMajorMinorVersion("1.14");

    private static final BlockFace[] CHECK_ADJACENT = new BlockFace[]{
            BlockFace.UP,
            BlockFace.DOWN,
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST
    };

    public static LightUpdateResult placeNewLightSource(VarLightPlugin plugin, Location location, int lightLevel) {
        if (!canNewLightSourceBePlaced(plugin, location)) {
            return adjacentLightSource(plugin);
        }

        WorldLightSourceManager manager = plugin.getManager(location.getWorld());

        if (manager == null) {
            return varLightNotActive(plugin, location.getWorld());
        }

        if (lightLevel < 0) {
            return zeroReached(plugin);
        }

        if (lightLevel > 15) {
            return fifteenReached(plugin);
        }

        if (plugin.getNmsAdapter().isIllegalBlock(location.getBlock())) {
            return invalidBlock(plugin);
        }

        LightUpdateEvent lightUpdateEvent = new LightUpdateEvent(location.getBlock(), manager.getCustomLuminance(toIntPosition(location), 0), lightLevel);
        Bukkit.getPluginManager().callEvent(lightUpdateEvent);

        if (lightUpdateEvent.isCancelled()) {
            return cancelled(plugin);
        }

        int lightTo = lightUpdateEvent.getToLight();

        plugin.getNmsAdapter().updateBlockLight(location, lightTo);
        manager.setCustomLuminance(location, lightTo);

        return updated(plugin, lightTo);
    }

    public static boolean canNewLightSourceBePlaced(VarLightPlugin plugin, Location location) {
        WorldLightSourceManager manager = plugin.getManager(location.getWorld());

        if (manager == null) {
            return false;
        }

        if (plugin.getNmsAdapter().getMinecraftVersion().newerOrEquals(MC1_14)) {
            for (BlockFace blockFace : CHECK_ADJACENT) {
                Block relative = location.getBlock().getRelative(blockFace);

                PersistentLightSource pls = manager.getPersistentLightSource(relative.getLocation());

                if (pls != null && !pls.isInvalid()) {
                    return false;
                }
            }
        }

        return true;
    }

}
