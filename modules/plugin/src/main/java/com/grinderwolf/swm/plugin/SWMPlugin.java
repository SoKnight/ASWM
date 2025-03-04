package com.grinderwolf.swm.plugin;

import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.CompoundTag;
import com.grinderwolf.swm.api.SlimePlugin;
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
import com.grinderwolf.swm.plugin.converter.WorldConverterService;
import com.grinderwolf.swm.plugin.loader.LoaderUtils;
import com.grinderwolf.swm.plugin.logging.Logging;
import com.grinderwolf.swm.plugin.world.WorldUnlocker;
import com.grinderwolf.swm.plugin.world.importer.WorldImporter;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

public class SWMPlugin extends JavaPlugin implements SlimePlugin {

    private static SWMPlugin INSTANCE;

    @Getter
    private final SlimeNMS platform;
    private final List<SlimeWorld> worlds;

    public SWMPlugin() {
        INSTANCE = this;
        this.platform = new SlimeNMSPlatform();
        this.worlds = new ArrayList<>();
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

            SlimeWorld defaultWorld = worlds.stream().filter(world -> world.getName().equals(defaultWorldName)).findFirst().orElse(null);
            SlimeWorld netherWorld = (getServer().getAllowNether() ? worlds.stream().filter(world -> world.getName().equals(defaultWorldName + "_nether")).findFirst().orElse(null) : null);
            SlimeWorld endWorld = (getServer().getAllowEnd() ? worlds.stream().filter(world -> world.getName().equals(defaultWorldName + "_the_end")).findFirst().orElse(null) : null);

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

        for (SlimeWorld world : worlds)
            if (Bukkit.getWorld(world.getName()) == null)
                generateWorld(world);

        worlds.clear();
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
                    worlds.add(world);
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
                WorldConverterService.downgradeWorld(world);
            } else if (world.getVersion() < platform.getWorldVersion()) {
                WorldConverterService.upgradeWorld(world);
            }
        } catch (Exception ex) {
            if (!readOnly) // Unlock the world as we're not using it
                loader.unlockWorld(worldName);

            throw ex;
        }

        long timeTaken = System.currentTimeMillis() - start;
        Logging.info("World '%s' loaded in %d ms.", worldName, timeTaken);
        return world;
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
        return world;
    }

    @Override
    public void generateWorld(SlimeWorld world) {
        Objects.requireNonNull(world, "SlimeWorld cannot be null");

        if (!world.isReadOnly() && !world.isLocked())
            throw new IllegalArgumentException("This world cannot be loaded, as it has not been locked.");

        platform.generateWorld(world);
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

    public static Logger logger() {
        return getInstance().getLogger();
    }

    public static SWMPlugin getInstance() {
        return INSTANCE;
    }

}