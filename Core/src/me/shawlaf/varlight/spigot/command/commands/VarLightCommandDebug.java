package me.shawlaf.varlight.spigot.command.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import me.shawlaf.command.exception.CommandException;
import me.shawlaf.varlight.persistence.RegionPersistor;
import me.shawlaf.varlight.spigot.VarLightPlugin;
import me.shawlaf.varlight.spigot.command.VarLightSubCommand;
import me.shawlaf.varlight.spigot.persistence.PersistentLightSource;
import me.shawlaf.varlight.spigot.persistence.WorldLightSourceManager;
import me.shawlaf.varlight.util.ChunkCoords;
import me.shawlaf.varlight.util.RegionCoords;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static me.shawlaf.command.result.CommandResult.failure;
import static me.shawlaf.command.result.CommandResult.success;
import static me.shawlaf.varlight.spigot.command.VarLightCommand.FAILURE;
import static me.shawlaf.varlight.spigot.command.VarLightCommand.SUCCESS;

@SuppressWarnings("DuplicatedCode")
public class VarLightCommandDebug extends VarLightSubCommand {

    private static final RequiredArgumentBuilder<CommandSender, Integer> ARG_REGION_X = argument("regionX", integer());
    private static final RequiredArgumentBuilder<CommandSender, Integer> ARG_REGION_Z = argument("regionX", integer());

    private static final RequiredArgumentBuilder<CommandSender, Integer> ARG_CHUNK_X = argument("regionX", integer());
    private static final RequiredArgumentBuilder<CommandSender, Integer> ARG_CHUNK_Z = argument("regionX", integer());

    public VarLightCommandDebug(VarLightPlugin plugin) {
        super(plugin, "debug");
    }

    @NotNull
    @Override
    public String getRequiredPermission() {
        return "varlight.admin.debug";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Lists all custom Light sources in a region or chunk";
    }

    @NotNull
    @Override
    public String getSyntax() {
        return " -r|-c [regionX|chunkX] [regionZ|chunkZ]";
    }

    @NotNull
    @Override
    public LiteralArgumentBuilder<CommandSender> build(LiteralArgumentBuilder<CommandSender> literalArgumentBuilder) {
        literalArgumentBuilder.then(
                LiteralArgumentBuilder.<CommandSender>literal("list")
                        .then(
                                LiteralArgumentBuilder.<CommandSender>literal("-r")
                                        .executes(this::regionImplicit)
                                        .then(
                                                ARG_REGION_X.then(ARG_REGION_Z.executes(this::regionExplicit))
                                        )
                        ).then(
                        LiteralArgumentBuilder.<CommandSender>literal("-c")
                                .executes(this::chunkImplicit)
                                .then(
                                        ARG_CHUNK_X.then(ARG_CHUNK_Z.executes(this::chunkExplicit))
                                )
                )
        );

        literalArgumentBuilder.then(
                LiteralArgumentBuilder.<CommandSender>literal("stick").executes(context -> {
                    if (!(context.getSource() instanceof Player)) {
                        failure(this, context.getSource(), "You must be a player to use this command!");
                        return FAILURE;
                    }

                    ((Player) context.getSource()).getInventory().addItem(plugin.getNmsAdapter().getVarLightDebugStick());

                    return SUCCESS;
                })
        );

        return literalArgumentBuilder;
    }

    private int regionImplicit(CommandContext<CommandSender> context) {
        if (!(context.getSource() instanceof Player)) {
            failure(this, context.getSource(), "You must be a player to use this command!");
            return FAILURE;
        }

        Player player = (Player) context.getSource();

        int regionX = player.getLocation().getBlockX() >> 4 >> 5;
        int regionZ = player.getLocation().getBlockZ() >> 4 >> 5;

        listLightSourcesInRegion(player, regionX, regionZ);

        return SUCCESS;
    }

    private int regionExplicit(CommandContext<CommandSender> context) {
        if (!(context.getSource() instanceof Player)) {
            failure(this, context.getSource(), "You must be a player to use this command!");
            return FAILURE;
        }

        Player player = (Player) context.getSource();

        int regionX = context.getArgument(ARG_REGION_X.getName(), int.class);
        int regionZ = context.getArgument(ARG_REGION_Z.getName(), int.class);

        listLightSourcesInRegion(player, regionX, regionZ);

        return SUCCESS;
    }

    private int chunkImplicit(CommandContext<CommandSender> context) {
        if (!(context.getSource() instanceof Player)) {
            failure(this, context.getSource(), "You must be a player to use this command!");
            return FAILURE;
        }

        Player player = (Player) context.getSource();

        int chunkX = player.getLocation().getBlockX() >> 4;
        int chunkZ = player.getLocation().getBlockZ() >> 4;

        listLightSourcesInChunk(player, chunkX, chunkZ);

        return SUCCESS;
    }

    private int chunkExplicit(CommandContext<CommandSender> context) {
        if (!(context.getSource() instanceof Player)) {
            failure(this, context.getSource(), "You must be a player to use this command!");
            return FAILURE;
        }

        Player player = (Player) context.getSource();

        int chunkX = context.getArgument(ARG_CHUNK_X.getName(), int.class);
        int chunkZ = context.getArgument(ARG_CHUNK_Z.getName(), int.class);

        listLightSourcesInChunk(player, chunkX, chunkZ);

        return SUCCESS;
    }

    private void listLightSourcesInRegion(Player player, int regionX, int regionZ) {
        WorldLightSourceManager manager = plugin.getManager(player.getWorld());

        if (manager == null) {
            success(this, player, "Varlight is not active in your current world!");
            return;
        }

        RegionPersistor<PersistentLightSource> persistor = manager.getRegionPersistor(new RegionCoords(regionX, regionZ));
        List<PersistentLightSource> all;

        try {
            all = persistor.loadAll();
        } catch (IOException e) {
            throw CommandException.severeException("Failed to load light sources!", e);
        }

        player.sendMessage(String.format("Light sources in region (%d | %d): [%d]", regionX, regionZ, all.size()));
        listInternal(player, all);
    }

    private void listLightSourcesInChunk(Player player, int chunkX, int chunkZ) {
        WorldLightSourceManager manager = plugin.getManager(player.getWorld());

        if (manager == null) {
            success(this, player, "Varlight is not active in your current world!");
            return;
        }

        ChunkCoords chunkCoords = new ChunkCoords(chunkX, chunkZ);

        RegionPersistor<PersistentLightSource> persistor = manager.getRegionPersistor(chunkCoords.toRegionCoords());
        List<PersistentLightSource> all;

        if (!persistor.isChunkLoaded(chunkCoords)) {
            try {
                persistor.loadChunk(chunkCoords);
            } catch (IOException e) {
                throw CommandException.severeException("Failed to load light sources!", e);
            }
        }

        all = persistor.getCache(chunkCoords);

        player.sendMessage(String.format("Light sources in chunk (%d | %d): [%d]", chunkX, chunkZ, all.size()));
        listInternal(player, all);
    }

    private void listInternal(Player player, List<PersistentLightSource> list) {
        for (PersistentLightSource lightSource : list) {

            TextComponent textComponent = new TextComponent(lightSource.toCompactString(true));

            textComponent.setClickEvent(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    String.format("/tp %d %d %d", lightSource.getPosition().x, lightSource.getPosition().y, lightSource.getPosition().z)
            ));

            textComponent.setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new BaseComponent[]{
                            new TextComponent("Click to teleport")
                    }
            ));

            player.spigot().sendMessage(textComponent);
        }
    }
}
