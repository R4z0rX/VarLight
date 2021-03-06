package me.shawlaf.varlight.spigot;

import me.shawlaf.varlight.spigot.nms.MaterialType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class VarLightConfiguration {

    public static final String CONFIG_KEY_VARLIGHT_ITEM = "item";
    public static final String CONFIG_KEY_LOG_PERSIST = "autosave-logpersist";
    public static final String CONFIG_KEY_REQUIRED_PERMISSION = "requiredPermission";
    public static final String REQUIRED_PERMISSION_DEFAULT = "";
    public static final String CONFIG_KEY_AUTOSAVE = "autosave";
    public static final String CONFIG_KEY_VLDB_DEFLATED = "vldb-deflated";
    public static final int AUTOSAVE_DEFAULT = 5;
    private final VarLightPlugin plugin;

    public VarLightConfiguration(VarLightPlugin plugin) {
        this.plugin = plugin;

        plugin.saveDefaultConfig();
    }

    public String getRequiredPermissionNode() {
        return plugin.getConfig().getString(CONFIG_KEY_REQUIRED_PERMISSION, REQUIRED_PERMISSION_DEFAULT);
    }

    public void setRequiredPermissionNode(String permissionNode) {
        plugin.getConfig().set(CONFIG_KEY_REQUIRED_PERMISSION, permissionNode);

        save();
    }

    public Material getLightUpdateItem() {
        String configMaterial = plugin.getConfig().getString(CONFIG_KEY_VARLIGHT_ITEM, "minecraft:glowstone_dust").toLowerCase();

        Material material = plugin.getNmsAdapter().keyToType(configMaterial, MaterialType.ITEM);

        if (material == null) {
            plugin.getLogger().warning(String.format("Could not find a Material with the given name \"%s\", defaulting to \"%s\"", configMaterial, plugin.getNmsAdapter().materialToKey(Material.GLOWSTONE_DUST)));
            return Material.GLOWSTONE_DUST;
        }

        if (plugin.getNmsAdapter().isIllegalLightUpdateItem(material)) {
            plugin.getLogger().warning(String.format("\"%s\" cannot be used as the Light update item. Defaulting to \"%s\"", plugin.getNmsAdapter().materialToKey(material), plugin.getNmsAdapter().materialToKey(Material.GLOWSTONE_DUST)));

            return Material.GLOWSTONE_DUST;
        }

        return material;
    }

    public boolean isLoggingPersist() {
        return plugin.getConfig().getBoolean(CONFIG_KEY_LOG_PERSIST, true);
    }

    public int getAutosaveInterval() {
        return plugin.getConfig().getInt(CONFIG_KEY_AUTOSAVE, AUTOSAVE_DEFAULT);
    }

    public void setAutosaveInterval(int interval) {
        plugin.getConfig().set(CONFIG_KEY_AUTOSAVE, interval);

        save();
    }

    public boolean addWorldToList(World world, WorldListType type) {
        Objects.requireNonNull(world);
        Objects.requireNonNull(type);

        List<World> worlds = getWorlds(type);

        if (worlds.contains(world)) {
            return false;
        }

        worlds.add(world);
        plugin.getConfig().set(type.getConfigPath(), worlds.stream().map(World::getName).collect(Collectors.toList()));

        save();

        return true;
    }

    public boolean removeWorldFromList(World world, WorldListType type) {
        Objects.requireNonNull(world);
        Objects.requireNonNull(type);

        List<World> worlds = getWorlds(type);

        if (!worlds.contains(world)) {
            return false;
        }

        worlds.remove(world);
        plugin.getConfig().set(type.getConfigPath(), worlds.stream().map(World::getName).collect(Collectors.toList()));

        save();

        return true;
    }

    public List<String> getWorldNames(WorldListType type) {
        Objects.requireNonNull(type);
        return plugin.getConfig().getStringList(type.getConfigPath());
    }

    public List<World> getWorlds(WorldListType type) {
        return getWorldNames(type).stream().map(Bukkit::getWorld).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<String> getVarLightEnabledWorldNames() {
        List<String> worlds = getWorldNames(WorldListType.WHITELIST);

        if (worlds.isEmpty()) {
            worlds = Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
        }

        getWorldNames(WorldListType.BLACKLIST).forEach(worlds::remove);
        return worlds;
    }

    public List<World> getVarLightEnabledWorlds() {
        return getVarLightEnabledWorldNames().stream().map(Bukkit::getWorld).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public void save() {
        plugin.saveConfig();
    }

    public enum WorldListType {
        WHITELIST("whitelist", "Whitelist"),
        BLACKLIST("blacklist", "Blacklist");

        private final String configPath;
        private final String name;

        WorldListType(String configPath, String name) {
            this.configPath = configPath;
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String getConfigPath() {
            return configPath;
        }
    }
}
