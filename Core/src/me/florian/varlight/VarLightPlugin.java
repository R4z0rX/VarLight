package me.florian.varlight;

import me.florian.varlight.command.VarLightCommand;
import me.florian.varlight.event.LightUpdateEvent;
import me.florian.varlight.nms.*;
import me.florian.varlight.persistence.LightSourcePersistor;
import me.florian.varlight.util.IntPosition;
import me.florian.varlight.util.NumericMajorMinorVersion;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class VarLightPlugin extends JavaPlugin implements Listener {

    public static final NumericMajorMinorVersion
            MC1_14_2 = new NumericMajorMinorVersion("1.14.2");
    public static boolean DEBUG = false;

    private enum LightUpdateResult {
        INVALID_BLOCK,
        ZERO_REACHED,
        FIFTEEN_REACHED,
        CANCELLED,
        UPDATED;
    }

    public static final long TICK_RATE = 20L;
    private static String PACKAGE_VERSION;

    public static String getPackageVersion() {
        return PACKAGE_VERSION;
    }

    private INmsAdapter nmsAdapter;
    private VarLightConfiguration configuration;
    private BukkitTask autosaveTask;
    private boolean doLoad = true;
    private PersistOnWorldSaveHandler persistOnWorldSaveHandler;

    private boolean isPaperImplementation() {
        try {
            Class.forName("me.florian.varlight.nms.v1_14_R1.paper.WrappedIChunkAccessPaper");
            Class.forName("me.florian.varlight.nms.v1_14_R1.paper.WrappedIBlockAccessPaper");

            return true;

        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean isPaper() {
        return Package.getPackage("com.destroystokyo.paper") != null;
    }

    private void unsupportedShutdown(String message) {
        getLogger().severe("------------------------------------------------------");
        getLogger().severe(message);
        getLogger().severe("");
        getLogger().severe("VarLight will shutdown the server!");
        getLogger().severe("Keeping the server running in this state");
        getLogger().severe("will result in countless bugs and errors in the console!");
        getLogger().severe("------------------------------------------------------");

        Bukkit.shutdown();

        doLoad = false;
    }

    @Override
    public void onLoad() {
        if (isPaper() && !isPaperImplementation()) {
            unsupportedShutdown("You are running Paper but installed the Spigot Version of VarLight");
            return;
        }

        if (!isPaper() && isPaperImplementation()) {
            unsupportedShutdown("You are running Spigot but installed the Paper Version of VarLight");
            return;
        }

        if ((int) ReflectionHelper.get(Bukkit.getServer(), "reloadCount") > 0) {
            unsupportedShutdown("VarLight does not support /reload!");
            return;
        }

        PACKAGE_VERSION = Bukkit.getServer().getClass().getPackage().getName();
        PACKAGE_VERSION = PACKAGE_VERSION.substring(PACKAGE_VERSION.lastIndexOf('.') + 1);

        final String version = NmsAdapter.class.getAnnotation(ForMinecraft.class).version();

        try {
            this.nmsAdapter = new NmsAdapter();
        } catch (Exception e) { // Catch anything that goes wrong while initializing
            e.printStackTrace();
            unsupportedShutdown(String.format("Failed to initialize VarLight for Minecraft Version \"%s\": %s", version, e.getMessage()));
            return;
        }


        getLogger().info(String.format("Loading VarLight for Minecraft version \"%s\"", version));
        nmsAdapter.onLoad(this);
    }

    @Override
    public void onEnable() {
        if (!doLoad) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        configuration = new VarLightConfiguration(this);
        DEBUG = configuration.isDebug();
        configuration.getVarLightEnabledWorlds().forEach(w -> LightSourcePersistor.createPersistor(this, w));

        try {
            nmsAdapter.onEnable(this);
        } catch (VarLightInitializationException e) {
            getLogger().throwing(getClass().getName(), "onEnable", e);
            Bukkit.getPluginManager().disablePlugin(this);
            doLoad = false;
            return;
        }


        initAutosave();

        Bukkit.getPluginManager().registerEvents(this, this);

        getCommand("varlight").setExecutor(new VarLightCommand(this));
    }

    public void initAutosave() {
        if (autosaveTask != null && !autosaveTask.isCancelled()) {
            autosaveTask.cancel();
            autosaveTask = null;
        }

        if (persistOnWorldSaveHandler != null) {
            HandlerList.unregisterAll(persistOnWorldSaveHandler);
            persistOnWorldSaveHandler = null;
        }

        int saveInterval = configuration.getAutosaveInterval();

        if (saveInterval == 0) {
            getLogger().warning("Autosave is disabled! All Light sources will be lost if the server crashes and Light sources were not manually saved!");
            return;
        }

        if (saveInterval < 0) {
            persistOnWorldSaveHandler = new PersistOnWorldSaveHandler(this);

            Bukkit.getPluginManager().registerEvents(persistOnWorldSaveHandler, this);
            return;
        }

        long ticks = TimeUnit.MINUTES.toSeconds(saveInterval) * TICK_RATE;

        autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                () -> LightSourcePersistor.getAllPersistors(this).forEach(p -> p.save(Bukkit.getConsoleSender())),
                ticks, ticks
        );
    }

    @Override
    public void onDisable() {
        if (!doLoad) {
            return;
        }

        configuration.save();
        nmsAdapter.onDisable();

        // If PersistOnSave is enabled, PersistOnWorldSaveHandler.onWorldSave will automatically save the Light Sources
        if (configuration.getAutosaveInterval() >= 0) {
            LightSourcePersistor.getAllPersistors(this).forEach(l -> l.save(Bukkit.getConsoleSender()));
        }
    }

    public VarLightConfiguration getConfiguration() {
        return configuration;
    }

    public INmsAdapter getNmsAdapter() {
        return nmsAdapter;
    }

    private boolean isLightLevelInRange(int lightLevel) {
        return lightLevel >= 0 && lightLevel <= 15;
    }

    private boolean canModifyBlockLight(Block block, int mod) {
        return isLightLevelInRange(block.getLightFromBlocks() + mod);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Optional<LightSourcePersistor> optPersistor = LightSourcePersistor.getPersistor(this, e.getPlayer().getWorld());

        if (e.isCancelled() || e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK || nmsAdapter.hasCooldown(e.getPlayer(), Material.GLOWSTONE_DUST)) {
            return;
        }

        if (!optPersistor.isPresent() && configuration.getVarLightEnabledWorldNames().contains(e.getPlayer().getWorld().getName())) {
            optPersistor = Optional.of(LightSourcePersistor.createPersistor(this, e.getPlayer().getWorld()));
        }

        if (!optPersistor.isPresent()) {
            return;
        }

        String requiredPermission = getConfig().getString("requiredPermission", null);

        if (requiredPermission != null && !e.getPlayer().hasPermission(requiredPermission)) {
            return;
        }

        Block clickedBlock = e.getClickedBlock();
        Player player = e.getPlayer();
        ItemStack heldItem = e.getItem();

        if (heldItem == null || heldItem.getType() != Material.GLOWSTONE_DUST) {
            return;
        }

        int mod = 0;

        switch (e.getAction()) {
            case RIGHT_CLICK_BLOCK:
                mod = 1;
                break;
            case LEFT_CLICK_BLOCK:
                mod = -1;
                break;
        }

        boolean creative = player.getGameMode() == GameMode.CREATIVE;

        if (!nmsAdapter.isValidBlock(clickedBlock)) {
            displayMessage(player, LightUpdateResult.INVALID_BLOCK);
            return;
        }

        if (!canModifyBlockLight(clickedBlock, mod)) {
            displayMessage(player, mod < 0 ? LightUpdateResult.ZERO_REACHED : LightUpdateResult.FIFTEEN_REACHED);
            return;
        }

        LightUpdateEvent lightUpdateEvent = new LightUpdateEvent(this, clickedBlock, mod);
        Bukkit.getPluginManager().callEvent(lightUpdateEvent);

        if (lightUpdateEvent.isCancelled()) {
            displayMessage(player, LightUpdateResult.CANCELLED);
            return;
        }

        int lightTo = lightUpdateEvent.getToLight();

        optPersistor.get().getOrCreatePersistentLightSource(new IntPosition(clickedBlock.getLocation()))
                .setEmittingLight(lightTo);

        nmsAdapter.updateBlockLight(clickedBlock.getLocation(), lightTo);

        e.setCancelled(creative && e.getAction() == Action.LEFT_CLICK_BLOCK);

        if (!creative && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            heldItem.setAmount(heldItem.getAmount() - 1);
        }

        nmsAdapter.setCooldown(player, Material.GLOWSTONE_DUST, 5);
        displayMessage(player, LightUpdateResult.UPDATED);
    }

    private void displayMessage(Player player, LightUpdateResult lightUpdateResult) {
        switch (lightUpdateResult) {

            case CANCELLED: {
                if (DEBUG) {
                    nmsAdapter.sendActionBarMessage(player, "Cancelled");
                }
            }

            case INVALID_BLOCK: {
                if (DEBUG) {
                    nmsAdapter.sendActionBarMessage(player, "Invalid Block.");
                }

                return;
            }

            case FIFTEEN_REACHED: {
                nmsAdapter.sendActionBarMessage(player, "Cannot increase light level beyond 15.");
                return;
            }
            case ZERO_REACHED: {
                nmsAdapter.sendActionBarMessage(player, "Cannot decrease light level below 0.");
                return;
            }

            case UPDATED: {
                nmsAdapter.sendActionBarMessage(player, "Updated Light level");
                return;
            }
        }
    }
}
