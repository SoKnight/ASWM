package com.grinderwolf.swm.plugin;

import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.CompoundTag;
import com.google.common.collect.ImmutableList;
import com.grinderwolf.swm.api.SlimePlugin;
import com.grinderwolf.swm.api.event.*;
import com.grinderwolf.swm.api.exception.*;
import com.grinderwolf.swm.api.loader.SlimeLoader;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;
import com.grinderwolf.swm.nms.CraftSlimeWorld;
import com.grinderwolf.swm.nms.SlimeNMS;
import com.grinderwolf.swm.nms.SlimeNMSPlatform;
import com.grinderwolf.swm.plugin.command.CommandManager;
import com.grinderwolf.swm.plugin.config.ConfigManager;
import com.grinderwolf.swm.plugin.config.WorldData;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
import com.grinderwolf.swm.plugin.world.upgrader.WorldUpgraderService;
import com.grinderwolf.swm.plugin.loader.LoaderUtils;
import com.grinderwolf.swm.plugin.logging.Logging;
import com.grinderwolf.swm.plugin.world.WorldUnlocker;
import com.grinderwolf.swm.plugin.world.importer.WorldImporter;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SWMPlugin extends JavaPlugin implements SlimePlugin, Listener {

    private static SWMPlugin INSTANCE;

    @Getter
    private final SlimeNMS platform;
    private final Map<String, SlimeWorld> loadedWorlds;

    public SWMPlugin() {
        INSTANCE = this;
        this.platform = new SlimeNMSPlatform(this);
        this.loadedWorlds = new ConcurrentHashMap<>();
    }

    @Override
    public void onLoad() {
        try {
            ConfigManager.initialize();
        } catch (NullPointerException | IOException ex) {
            Logging.error("Failed to load config files!", ex);
            return;
        }

        LoaderUtils.registerLoaders();

        // Default world override
        try {
            Properties props = new Properties();
            props.load(new FileInputStream("server.properties"));

            List<String> erroredWorlds = loadWorlds();
            String defaultWorldName = props.getProperty("level-name");

            if (erroredWorlds.contains(defaultWorldName)) {
                Logging.error("Shutting down server, as the default world could not be loaded.");
                System.exit(1);
            } else if (getServer().getAllowNether() && erroredWorlds.contains(defaultWorldName + "_nether")) {
                Logging.error("Shutting down server, as the default nether world could not be loaded.");
                System.exit(1);
            } else if (getServer().getAllowEnd() && erroredWorlds.contains(defaultWorldName + "_the_end")) {
                Logging.error("Shutting down server, as the default end world could not be loaded.");
                System.exit(1);
            }

            SlimeWorld defaultWorld = loadedWorlds.get(defaultWorldName);
            SlimeWorld netherWorld = getServer().getAllowNether() ? loadedWorlds.get(defaultWorldName + "_nether") : null;
            SlimeWorld endWorld = getServer().getAllowEnd() ? loadedWorlds.get(defaultWorldName + "_the_end") : null;

            platform.setDefaultWorlds(defaultWorld, netherWorld, endWorld);
        } catch (IOException ex) {
            Logging.error("Failed to retrieve default world name!", ex);
        }
    }

    @Override
    public void onEnable() {
        CommandManager commandManager = new CommandManager();
        PluginCommand swmCommand = getCommand("swm");
        swmCommand.setExecutor(commandManager);

        try {
            swmCommand.setTabCompleter(commandManager);
        } catch (Throwable throwable) {
            // For some versions that does not have TabComplete?
        }

        getServer().getPluginManager().registerEvents(new WorldUnlocker(), this);

        loadedWorlds.values().stream()
                .filter(slimeWorld -> Objects.isNull(Bukkit.getWorld(slimeWorld.getName())))
                .forEach(this::generateWorld);

        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        Bukkit.getWorlds().stream()
                .map(platform::getSlimeWorld)
                .filter(Objects::nonNull)
                .filter(world -> !world.isReadOnly())
                .map(world -> (CraftSlimeWorld) world)
                .forEach(world -> {
                    SlimeLoader loader = world.getLoader();
                    String worldName = world.getName();

                    try {
                        loader.saveWorld(worldName, world.serialize(), world.isLocked());

                        if (loader.isWorldLocked(worldName)) {
                            loader.unlockWorld(worldName);
                        }
                    } catch (IOException | UnknownWorldException ex) {
                        Logging.error("Failed to save world '%s'!".formatted(worldName), ex);
                    }
                });
    }

    @EventHandler
    public void onBukkitWorldUnload(WorldUnloadEvent event) {
        this.loadedWorlds.remove(event.getWorld().getName());
    }

    private void registerWorld(SlimeWorld world) {
        this.loadedWorlds.put(world.getName(), world);
    }

    private List<String> loadWorlds() {
        List<String> erroredWorlds = new ArrayList<>();
        WorldsConfig config = ConfigManager.getWorldConfig();

        for (Map.Entry<String, WorldData> entry : config.getWorlds().entrySet()) {
            String worldName = entry.getKey();
            WorldData worldData = entry.getValue();

            if (worldData.isLoadOnStartup()) {
                try {
                    SlimeLoader loader = getLoader(worldData.getDataSource());
                    if (loader == null)
                        throw new IllegalArgumentException("invalid data source %s".formatted(worldData.getDataSource()));

                    SlimePropertyMap propertyMap = worldData.toPropertyMap();
                    SlimeWorld world = loadWorld(loader, worldName, worldData.isReadOnly(), propertyMap);
                    loadedWorlds.put(worldName, world);
                } catch (UnknownWorldException ex) {
                    Logging.error("Failed to load world '%s': world does not exist, are you sure you've set the correct data source?", worldName);
                    erroredWorlds.add(worldName);
                } catch (NewerFormatException ex) {
                    Logging.error("Failed to load world '%s': world is serialized in a newer Slime Format version (%s) that SWM does not understand.", worldName, ex.getMessage());
                    erroredWorlds.add(worldName);
                } catch (WorldInUseException ex) {
                    Logging.error("Failed to load world '%s': world is in use! If you think this is a mistake, please wait some time and try again.", worldName);
                    erroredWorlds.add(worldName);
                } catch (CorruptedWorldException ex) {
                    Logging.error("Failed to load world '%s': world seems to be corrupted.", worldName);
                    erroredWorlds.add(worldName);
                } catch (Exception ex) {
                    Logging.error("Failed to load world '%s'!".formatted(worldName), ex);
                    erroredWorlds.add(worldName);
                }
            }
        }

        config.save();
        return erroredWorlds;
    }

    @Override
    public SlimeWorld getWorld(String worldName) {
        return worldName != null ? loadedWorlds.get(worldName) : null;
    }

    @Override
    public List<SlimeWorld> getLoadedWorlds() {
        return ImmutableList.copyOf(loadedWorlds.values());
    }

    @Override
    public SlimeWorld loadWorld(
            SlimeLoader loader,
            String worldName,
            boolean readOnly,
            SlimePropertyMap propertyMap
    ) throws UnknownWorldException, IOException, CorruptedWorldException, NewerFormatException, WorldInUseException {
        Objects.requireNonNull(loader, "Loader cannot be null");
        Objects.requireNonNull(worldName, "World name cannot be null");
        Objects.requireNonNull(propertyMap, "Properties cannot be null");

        Logging.info("Loading world '%s'...", worldName);
        long start = System.currentTimeMillis();
        byte[] serializedWorld = loader.loadWorld(worldName, readOnly);
        CraftSlimeWorld world;

        try {
            world = LoaderUtils.deserializeWorld(loader, worldName, serializedWorld, propertyMap, readOnly);
            if (world.getVersion() > platform.getWorldVersion()) {
                throw new NewerFormatException(world.getVersion());
            } else if (world.getVersion() < platform.getWorldVersion()) {
                WorldUpgraderService.upgradeWorld(world);
            }
        } catch (Exception ex) {
            if (!readOnly) // Unlock the world as we're not using it
                loader.unlockWorld(worldName);

            throw ex;
        }

        long timeTaken = System.currentTimeMillis() - start;
        Logging.info("World '%s' loaded in %d ms.", worldName, timeTaken);
        registerWorld(world);
        return world;
    }

    @Override
    public CompletableFuture<SlimeWorld> loadWorldAsync(SlimeLoader loader, String worldName, boolean readOnly, SlimePropertyMap slimePropertyMap) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var preEvent = new AsyncPreLoadWorldEvent(loader, worldName, readOnly, slimePropertyMap);
                Bukkit.getPluginManager().callEvent(preEvent);
                if (preEvent.isCancelled())
                    return null;

                var world = loadWorld(preEvent.getSlimeLoader(), preEvent.getWorldName(), preEvent.isReadOnly(), preEvent.getSlimePropertyMap());
                var postEvent = new AsyncPostLoadWorldEvent(world);
                Bukkit.getPluginManager().callEvent(postEvent);
                return postEvent.getWorld();
            } catch (UnknownWorldException | IOException | CorruptedWorldException | NewerFormatException | WorldInUseException ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    @Override
    public SlimeWorld createEmptyWorld(
            SlimeLoader loader,
            String worldName,
            boolean readOnly,
            SlimePropertyMap propertyMap
    ) throws WorldAlreadyExistsException, IOException {
        Objects.requireNonNull(loader, "Loader cannot be null");
        Objects.requireNonNull(worldName, "World name cannot be null");
        Objects.requireNonNull(propertyMap, "Properties cannot be null");

        if (loader.worldExists(worldName))
            throw new WorldAlreadyExistsException(worldName);

        Logging.info("Creating empty world '%s'...", worldName);
        long start = System.currentTimeMillis();

        CraftSlimeWorld world = new CraftSlimeWorld(
                loader,
                worldName,
                new HashMap<>(),
                new CompoundTag("", new CompoundMap()),
                new ArrayList<>(),
                platform.getWorldVersion(),
                propertyMap,
                readOnly,
                !readOnly
        );

        loader.saveWorld(worldName, world.serialize(), !readOnly);

        long timeTaken = System.currentTimeMillis() - start;
        Logging.info("World '%s' created in %d ms.", worldName, timeTaken);
        registerWorld(world);
        return world;
    }

    @Override
    public CompletableFuture<SlimeWorld> createEmptyWorldAsync(SlimeLoader slimeLoader, String worldName, boolean readOnly, SlimePropertyMap slimePropertyMap) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var preEvent = new AsyncPreCreateEmptyWorldEvent(slimeLoader, worldName, readOnly, slimePropertyMap);
                Bukkit.getPluginManager().callEvent(preEvent);
                if (preEvent.isCancelled())
                    return null;

                var world = createEmptyWorld(preEvent.getSlimeLoader(), preEvent.getWorldName(), preEvent.isReadOnly(), preEvent.getSlimePropertyMap());
                var postEvent = new AsyncPostCreateEmptyWorldEvent(world);
                Bukkit.getPluginManager().callEvent(postEvent);
                return postEvent.getWorld();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    @Override
    public void generateWorld(SlimeWorld world) {
        Objects.requireNonNull(world, "SlimeWorld cannot be null");

        if (!world.isReadOnly() && !world.isLocked())
            throw new IllegalArgumentException("This world cannot be loaded, as it has not been locked.");

        var preEvent = new PreGenerateWorldEvent(world);
        Bukkit.getPluginManager().callEvent(preEvent);
        if (preEvent.isCancelled())
            return;

        platform.generateWorld(world);
        var postEvent = new PostGenerateWorldEvent(world);
        Bukkit.getPluginManager().callEvent(postEvent);
    }

    @Override
    public void migrateWorld(
            String worldName,
            SlimeLoader currentLoader,
            SlimeLoader newLoader
    ) throws IOException, WorldInUseException, WorldAlreadyExistsException, UnknownWorldException {
        Objects.requireNonNull(worldName, "World name cannot be null");
        Objects.requireNonNull(currentLoader, "Current loader cannot be null");
        Objects.requireNonNull(newLoader, "New loader cannot be null");

        if (newLoader.worldExists(worldName))
            throw new WorldAlreadyExistsException(worldName);

        World bukkitWorld = Bukkit.getWorld(worldName);
        boolean leaveLock = false;

        if (bukkitWorld != null) {
            // Make sure the loaded world really is a SlimeWorld and not a normal Bukkit world
            CraftSlimeWorld slimeWorld = (CraftSlimeWorld) platform.getSlimeWorld(bukkitWorld);
            if (slimeWorld != null && currentLoader.equals(slimeWorld.getLoader())) {
                slimeWorld.setLoader(newLoader);
                if (!slimeWorld.isReadOnly()) { // We have to manually unlock the world so no WorldInUseException is thrown
                    currentLoader.unlockWorld(worldName);
                    leaveLock = true;
                }
            }
        }

        byte[] serializedWorld = currentLoader.loadWorld(worldName, false);
        newLoader.saveWorld(worldName, serializedWorld, leaveLock);
        currentLoader.deleteWorld(worldName);
    }

    @Override
    public CompletableFuture<Void> migrateWorldAsync(String worldName, SlimeLoader currentLoader, SlimeLoader newLoader) {
        return CompletableFuture.runAsync(() -> {
            try {
                var preEvent = new AsyncPreMigrateWorldEvent(worldName, currentLoader, newLoader);
                Bukkit.getPluginManager().callEvent(preEvent);
                if (preEvent.isCancelled())
                    return;

                migrateWorld(preEvent.getWorldName(), preEvent.getCurrentLoader(), preEvent.getNewLoader());
                var postEvent = new AsyncPostMigrateWorldEvent(preEvent.getWorldName(), preEvent.getCurrentLoader(), preEvent.getNewLoader());
                Bukkit.getPluginManager().callEvent(postEvent);
            } catch (IOException | WorldInUseException | WorldAlreadyExistsException | UnknownWorldException ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    @Override
    public void importWorld(
            Path worldDir,
            String worldName,
            SlimeLoader loader
    ) throws WorldAlreadyExistsException, InvalidWorldException, WorldLoadedException, WorldTooBigException, IOException {
        Objects.requireNonNull(worldDir, "World directory cannot be null");
        Objects.requireNonNull(worldName, "World name cannot be null");
        Objects.requireNonNull(loader, "Loader cannot be null");

        if (loader.worldExists(worldName))
            throw new WorldAlreadyExistsException(worldName);

        String worldDirName = worldDir.getFileName().toString();
        World bukkitWorld = Bukkit.getWorld(worldDirName);
        if (bukkitWorld != null && platform.getSlimeWorld(bukkitWorld) == null)
            throw new WorldLoadedException(worldDirName);

        CraftSlimeWorld world = WorldImporter.readFromDirectory(worldDir);
        byte[] serializedWorld;

        try {
            serializedWorld = world.serialize();
        } catch (IndexOutOfBoundsException ex) {
            throw new WorldTooBigException(worldDirName);
        }

        loader.saveWorld(worldName, serializedWorld, false);
    }

    @Override
    public CompletableFuture<?> importWorldAsync(Path worldDir, String worldName, SlimeLoader slimeLoader) {
        return CompletableFuture.runAsync(() -> {
            try {
                var preEvent = new AsyncPreImportWorldEvent(worldDir, worldName, slimeLoader);
                Bukkit.getPluginManager().callEvent(preEvent);
                if (preEvent.isCancelled())
                    return;

                importWorld(preEvent.getWorldDir(), preEvent.getWorldName(), preEvent.getSlimeLoader());
                var postEvent = new AsyncPostImportWorldEvent(preEvent.getWorldDir(), preEvent.getWorldName(), preEvent.getSlimeLoader());
                Bukkit.getPluginManager().callEvent(postEvent);
            } catch (WorldAlreadyExistsException | InvalidWorldException | WorldLoadedException | WorldTooBigException | IOException ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    @Override
    public SlimeLoader getLoader(String dataSource) {
        Objects.requireNonNull(dataSource, "Data source cannot be null");
        return LoaderUtils.getLoader(dataSource);
    }

    @Override
    public void registerLoader(String dataSource, SlimeLoader loader) {
        Objects.requireNonNull(dataSource, "Data source cannot be null");
        Objects.requireNonNull(loader, "Loader cannot be null");
        LoaderUtils.registerLoader(dataSource, loader);
    }

    public static Logger logger() {
        return getInstance().getLogger();
    }

    public static SWMPlugin getInstance() {
        return INSTANCE;
    }

}