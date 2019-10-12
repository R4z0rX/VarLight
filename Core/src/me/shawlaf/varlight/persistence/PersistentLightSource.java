package me.shawlaf.varlight.persistence;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import me.shawlaf.varlight.VarLightPlugin;
import me.shawlaf.varlight.util.IntPosition;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;

public class PersistentLightSource {

    private final IntPosition position;
    private final Material type;
    boolean migrated = false;
    private transient World world;
    private transient VarLightPlugin plugin;
    private int emittingLight;

    PersistentLightSource(VarLightPlugin plugin, World world, IntPosition position, int emittingLight) {
        Objects.requireNonNull(plugin);
        Objects.requireNonNull(world);
        Objects.requireNonNull(position);

        this.plugin = plugin;
        this.world = world;
        this.position = position;
        this.type = position.toLocation(world).getBlock().getType();
        this.emittingLight = (emittingLight & 0xF);
    }

    static PersistentLightSource read(Gson gson, JsonReader jsonReader) {
        return gson.fromJson(jsonReader, PersistentLightSource.class);
    }

    public World getWorld() {
        return world;
    }

    public IntPosition getPosition() {
        return position;
    }

    public Material getType() {
        return type;
    }

    public int getEmittingLight() {

        if (!isValid()) {
            return 0;
        }

        return emittingLight & 0xF;
    }

    public void setEmittingLight(int lightLevel) {
        this.emittingLight = (lightLevel & 0xF);
    }

    public boolean needsMigration() {
        return plugin.getNmsAdapter().getMinecraftVersion().newerOrEquals(VarLightPlugin.MC1_14_2) && !isMigrated();
    }

    public boolean isMigrated() {
        return migrated;
    }

    public void migrate() {
        plugin.getNmsAdapter().updateBlockLight(position.toLocation(world), emittingLight);
        migrated = true;
    }

    public void update() {
        if (needsMigration() && world.isChunkLoaded(position.getChunkX(), position.getChunkZ())) {
            migrate();
        }
    }

    public boolean isValid() {
        if (!world.isChunkLoaded(position.getChunkX(), position.getChunkZ())) {
            return true; // Assume valid
        }

        Block block = position.toBlock(world);

        if (block.getType() != type) {
            return false;
        }

        if (plugin.getNmsAdapter().isIllegalBlock(block)) {
            return false;
        }

        return block.getLightFromBlocks() >= emittingLight;
    }

    void initialize(World world, VarLightPlugin plugin) {
        this.world = world;
        this.plugin = plugin;
    }

    void write(Gson gson, JsonWriter jsonWriter) {
        gson.toJson(this, PersistentLightSource.class, jsonWriter);
    }


}
