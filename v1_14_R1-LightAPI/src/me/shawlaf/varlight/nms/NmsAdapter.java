package me.shawlaf.varlight.nms;

import me.shawlaf.varlight.VarLightPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.minecraft.server.v1_14_R1.IBlockData;
import net.minecraft.server.v1_14_R1.MinecraftServer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Piston;
import org.bukkit.craftbukkit.v1_14_R1.block.CraftBlock;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.beykerykt.lightapi.LightAPI;
import ru.beykerykt.lightapi.chunks.ChunkInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("deprecation")
@ForMinecraft(version = "Spigot 1.14 + LightAPI")
public class NmsAdapter implements INmsAdapter {

    public NmsAdapter(VarLightPlugin plugin) {
        if (!plugin.isLightApiInstalled()) {
            throw new VarLightInitializationException("LightAPI not installed!");
        }
    }

    @Override
    public boolean isBlockTransparent(@NotNull Block block) {
        throw new RuntimeException("Not used in combination with LightAPI");
    }

    @Override
    public void updateBlockLight(@NotNull Location at, int lightLevel) {
        Objects.requireNonNull(at);
        Objects.requireNonNull(at.getWorld());

        LightAPI.createLight(at, lightLevel, false);

        List<Chunk> chunksToUpdate = collectChunksToUpdate(at);
        List<ChunkInfo> chunkSectionsToUpdate = new ArrayList<>();

        final int sectionY = at.getBlockY() >> 4;

        for (Chunk chunk : chunksToUpdate) {
            chunkSectionsToUpdate.add(new ChunkInfo(at.getWorld(), chunk.getX(), sectionY, chunk.getZ(), at.getWorld().getPlayers()));

            if (sectionY < 16) {
                chunkSectionsToUpdate.add(new ChunkInfo(at.getWorld(), chunk.getX(), sectionY + 1, chunk.getZ(), at.getWorld().getPlayers()));
            }

            if (sectionY > 0) {
                chunkSectionsToUpdate.add(new ChunkInfo(at.getWorld(), chunk.getX(), sectionY - 1, chunk.getZ(), at.getWorld().getPlayers()));
            }
        }

        for (ChunkInfo chunkInfo : chunkSectionsToUpdate) {
            LightAPI.updateChunk(chunkInfo);
        }
    }

    @Override
    public int getEmittingLightLevel(@NotNull Block block) {
        IBlockData blockData = ((CraftBlock) block).getNMS();

        return blockData.getBlock().a(blockData);
    }

    @Override
    public void sendChunkUpdates(@NotNull Chunk chunk, int mask) {
    }

    @Override
    public boolean isIllegalBlock(@NotNull Block block) {
        if (!block.getType().isBlock()) {
            return true;
        }

        if (getEmittingLightLevel(block) > 0) {
            return true;
        }

        BlockData blockData = block.getType().createBlockData();

        if (blockData instanceof Powerable || blockData instanceof AnaloguePowerable || blockData instanceof Openable || blockData instanceof Piston) {
            return true;
        }

        if (block.getType() == Material.SLIME_BLOCK) {
            return true;
        }

        if (block.getType() == Material.BLUE_ICE) {
            return false; // Packed ice is solid and occluding but blue ice isn't?
        }

        return !block.getType().isSolid() || !block.getType().isOccluding();
    }

    @Override
    public void sendActionBarMessage(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    @Override
    public void setCooldown(Player player, Material material, int ticks) {
        player.setCooldown(material, ticks);
    }

    @Override
    public boolean hasCooldown(Player player, Material material) {
        return player.hasCooldown(material);
    }

    @Override
    public Block getTargetBlockExact(Player player, int maxDistance) {
        return player.getTargetBlockExact(maxDistance);
    }

    @NotNull
    @Override
    public String getNumericMinecraftVersion() {
        return MinecraftServer.getServer().getVersion();
    }
}