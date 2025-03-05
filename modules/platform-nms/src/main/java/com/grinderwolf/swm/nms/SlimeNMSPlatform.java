package com.grinderwolf.swm.nms;

import com.flowpowered.nbt.CompoundTag;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.api.world.properties.SlimeProperties;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.minecraft.server.v1_16_R3.*;
import net.minecraft.server.v1_16_R3.GameRules.GameRuleKey;
import net.minecraft.server.v1_16_R3.GameRules.GameRuleValue;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.craftbukkit.libs.org.apache.commons.io.FileUtils;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R3.util.CraftMagicNumbers;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("deprecation")
@Getter
@Log4j2
public class SlimeNMSPlatform implements SlimeNMS {

    private static final File UNIVERSE_DIR;
    public static Convertable CONVERTABLE;
    private static boolean isPaperMC;

    static {
        Path path;
        try {
            path = Files.createTempDirectory("swm-" + UUID.randomUUID().toString().substring(0, 5) + "-");
        } catch (IOException ex) {
            log.fatal("Failed to create temp directory", ex);
            path = null;
            System.exit(1);
        }

        UNIVERSE_DIR = path.toFile();
        CONVERTABLE = Convertable.a(path);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                FileUtils.deleteDirectory(UNIVERSE_DIR);
            } catch (IOException ex) {
                log.fatal("Failed to delete temp directory", ex);
            }
        }));
    }

    private final byte worldVersion = 0x06;
    private final Plugin plugin;

    private boolean loadingDefaultWorlds = true; // If true, the addWorld method will not be skipped

    private CustomWorldServer defaultWorld;
    private CustomWorldServer defaultNetherWorld;
    private CustomWorldServer defaultEndWorld;

    public SlimeNMSPlatform(Plugin plugin) {
        this.plugin = plugin;

        try {
            CraftCLSMBridge.initialize(this);
        } catch (NoClassDefFoundError ex) {
            log.error("Failed to find ClassModifier classes. Are you sure you installed it correctly?");
            System.exit(1); // No ClassModifier, no party
        }
    }

    @Override
    public void setDefaultWorlds(SlimeWorld normalWorld, SlimeWorld netherWorld, SlimeWorld endWorld) {
        if (normalWorld != null)
            defaultWorld = createDefaultWorld(normalWorld, WorldDimension.OVERWORLD, net.minecraft.server.v1_16_R3.World.OVERWORLD);

        if (netherWorld != null)
            defaultNetherWorld = createDefaultWorld(netherWorld, WorldDimension.THE_NETHER, net.minecraft.server.v1_16_R3.World.THE_NETHER);

        if (endWorld != null)
            defaultEndWorld = createDefaultWorld(endWorld, WorldDimension.THE_END, net.minecraft.server.v1_16_R3.World.THE_END);

        loadingDefaultWorlds = false;
    }

    private CustomWorldServer createDefaultWorld(
            SlimeWorld world,
            ResourceKey<WorldDimension> dimensionKey,
            ResourceKey<net.minecraft.server.v1_16_R3.World> worldKey
    ) {
        WorldDataServer worldDataServer = createWorldData(world);

        RegistryMaterials<WorldDimension> registryMaterials = worldDataServer.getGeneratorSettings().d();
        WorldDimension worldDimension = registryMaterials.a(dimensionKey);
        DimensionManager dimensionManager = worldDimension.b();
        ChunkGenerator chunkGenerator = worldDimension.c();

        Environment environment = getEnvironment(world);

        try {
            return new CustomWorldServer((CraftSlimeWorld) world, worldDataServer, worldKey, dimensionKey, dimensionManager, chunkGenerator, environment);
        } catch (IOException ex) {
            throw new RuntimeException(ex); // TODO do something better with this?
        }
    }

    @Override
    public void generateWorld(SlimeWorld world) {
        String worldName = world.getName();

        if (Bukkit.getWorld(worldName) != null)
            throw new IllegalArgumentException("World %s already exists! Maybe it's an outdated SlimeWorld object?".formatted(worldName));

        WorldDataServer worldDataServer = createWorldData(world);
        Environment environment = getEnvironment(world);

        ResourceKey<WorldDimension> dimension = switch (environment) {
            case NORMAL -> WorldDimension.OVERWORLD;
            case NETHER -> WorldDimension.THE_NETHER;
            case THE_END -> WorldDimension.THE_END;
            default -> throw new IllegalArgumentException("Unknown dimension supplied");
        };

        RegistryMaterials<WorldDimension> materials = worldDataServer.getGeneratorSettings().d();
        WorldDimension worldDimension = materials.a(dimension);
        DimensionManager dimensionManager = worldDimension.b();
        ChunkGenerator chunkGenerator = worldDimension.c();

        MinecraftKey mcKey = new MinecraftKey(worldName.toLowerCase(Locale.ENGLISH));
        ResourceKey<net.minecraft.server.v1_16_R3.World> worldKey = ResourceKey.a(IRegistry.L, mcKey);
        CustomWorldServer server;

        try {
            server = new CustomWorldServer(
                    (CraftSlimeWorld) world, worldDataServer,
                    worldKey, dimension,
                    dimensionManager, chunkGenerator,
                    environment
            );
        } catch (IOException ex) {
            throw new RuntimeException(ex); // TODO do something better with this?
        }

        EnderDragonBattle dragonBattle = server.getDragonBattle();
        boolean runBattle = world.getPropertyMap().getValue(SlimeProperties.DRAGON_BATTLE);

        if (dragonBattle != null && !runBattle) {
            dragonBattle.bossBattle.setVisible(false);

            try {
                Field battleField = WorldServer.class.getDeclaredField("dragonBattle");
                battleField.setAccessible(true);
                battleField.set(server, null);
            } catch(NoSuchFieldException | IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }

        server.setReady(true);

        MinecraftServer mcServer = MinecraftServer.getServer();
        mcServer.initWorld(server, worldDataServer, mcServer.getSaveData(), worldDataServer.getGeneratorSettings());

        mcServer.server.addWorld(server.getWorld());
        mcServer.worldServer.put(worldKey, server);

        server.setSpawnFlags(world.getPropertyMap().getValue(SlimeProperties.ALLOW_MONSTERS), world.getPropertyMap().getValue(SlimeProperties.ALLOW_ANIMALS));

        Bukkit.getPluginManager().callEvent(new WorldInitEvent(server.getWorld()));
        mcServer.loadSpawn(server.getChunkProvider().playerChunkMap.worldLoadListener, server);
        Bukkit.getPluginManager().callEvent(new WorldLoadEvent(server.getWorld()));
    }

    private Environment getEnvironment(SlimeWorld world) {
        return Environment.valueOf(world.getPropertyMap().getValue(SlimeProperties.ENVIRONMENT).toUpperCase());
    }

    private WorldDataServer createWorldData(SlimeWorld world) {
        String worldName = world.getName();
        CompoundTag extraData = world.getExtraData();
        WorldDataServer worldDataServer;
        NBTTagCompound extraTag = (NBTTagCompound) Converter.convertTag(extraData);
        MinecraftServer mcServer = MinecraftServer.getServer();
        DedicatedServerProperties serverProps = ((DedicatedServer) mcServer).getDedicatedServerProperties();

        if (extraTag.hasKeyOfType("LevelData", CraftMagicNumbers.NBT.TAG_COMPOUND)) {
            NBTTagCompound levelData = extraTag.getCompound("LevelData");
            int dataVersion = levelData.hasKeyOfType("DataVersion", 99) ? levelData.getInt("DataVersion") : -1;
            Dynamic<NBTBase> dynamic = mcServer.getDataFixer().update(
                    DataFixTypes.LEVEL.a(),
                    new Dynamic<>(DynamicOpsNBT.a, levelData), dataVersion, SharedConstants.getGameVersion().getWorldVersion()
            );

            LevelVersion levelVersion = LevelVersion.a(dynamic);
            WorldSettings worldSettings = WorldSettings.a(dynamic, mcServer.datapackconfiguration);

            worldDataServer = WorldDataServer.a(
                    dynamic,
                    mcServer.getDataFixer(),
                    dataVersion,
                    null,
                    worldSettings,
                    levelVersion,
                    serverProps.generatorSettings,
                    Lifecycle.stable()
            );
        } else {
            // Game rules
            Optional<CompoundTag> gameRules = extraData.getAsCompoundTag("gamerules");
            GameRules rules = new GameRules();

            gameRules.ifPresent(compoundTag -> {
                NBTTagCompound compound = (NBTTagCompound) Converter.convertTag(compoundTag);
                Map<String, GameRuleKey<?>> gameRuleKeys = CraftWorld.getGameRulesNMS();

                compound.getKeys().forEach(gameRule -> {
                    if(gameRuleKeys.containsKey(gameRule)) {
                        GameRuleValue<?> gameRuleValue = rules.get(gameRuleKeys.get(gameRule));
                        String theValue = compound.getString(gameRule);
                        gameRuleValue.setValue(theValue);
                        gameRuleValue.onChange(mcServer);
                    }
                });
            });

            WorldSettings worldSettings = new WorldSettings(
                    worldName,
                    serverProps.gamemode,
                    false,
                    serverProps.difficulty,
                    false,
                    rules,
                    mcServer.datapackconfiguration
            );

            worldDataServer = new WorldDataServer(worldSettings, serverProps.generatorSettings, Lifecycle.stable());
        }

        worldDataServer.checkName(worldName);
        worldDataServer.a(mcServer.getServerModName(), mcServer.getModded().isPresent());
        worldDataServer.c(true);
        return worldDataServer;
    }

    @Override
    public SlimeWorld getSlimeWorld(World world) {
        CraftWorld craftWorld = (CraftWorld) world;
        if (!(craftWorld.getHandle() instanceof CustomWorldServer))
            return null;

        return ((CustomWorldServer) craftWorld.getHandle()).getSlimeWorld();
    }

    @Override
    public CompoundTag convertChunk(CompoundTag tag) {
        NBTTagCompound nmsTag = (NBTTagCompound) Converter.convertTag(tag);
        int version = nmsTag.getInt("DataVersion");

        NBTTagCompound newNmsTag = GameProfileSerializer.a(DataConverterRegistry.a(), DataFixTypes.CHUNK, nmsTag, version);
        return (CompoundTag) Converter.convertTag("", newNmsTag);
    }

}