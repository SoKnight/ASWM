package com.grinderwolf.swm.plugin.config;

import com.grinderwolf.swm.plugin.SWMPlugin;
import lombok.AccessLevel;
import lombok.Getter;
import org.spongepowered.configurate.loader.HeaderMode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigManager {

    private static final Path PLUGIN_DIR = Paths.get("plugins", "SlimeWorldManager");
    private static final Path MAIN_FILE = PLUGIN_DIR.resolve("main.yml");
    private static final Path WORLDS_FILE = PLUGIN_DIR.resolve("worlds.yml");
    private static final Path SOURCES_FILE = PLUGIN_DIR.resolve("sources.yml");

    @Getter
    private static WorldsConfig worldConfig;
    @Getter(value = AccessLevel.PACKAGE)
    private static YamlConfigurationLoader worldConfigLoader;

    @Getter
    private static DatasourcesConfig datasourcesConfig;

    public static void initialize() throws IOException {
        copyDefaultConfigs();

        worldConfigLoader = createLoader(WORLDS_FILE);
        worldConfig = worldConfigLoader.load().get(WorldsConfig.class);
        worldConfig.save();

        var datasourcesConfigLoader = createLoader(SOURCES_FILE);
        datasourcesConfig = datasourcesConfigLoader.load().get(DatasourcesConfig.class);
        datasourcesConfigLoader.save(datasourcesConfigLoader.createNode().set(DatasourcesConfig.class, datasourcesConfig));
    }

    private static void copyDefaultConfigs() throws IOException {
        if (!Files.isDirectory(PLUGIN_DIR))
            Files.createDirectories(PLUGIN_DIR);

        createDefaultConfig(WORLDS_FILE, "worlds.yml");
        createDefaultConfig(SOURCES_FILE, "sources.yml");
    }

    private static void createDefaultConfig(Path outputFile, String resourceName) throws IOException {
        if (!Files.isRegularFile(outputFile)) {
            Files.copy(SWMPlugin.getInstance().getResource(resourceName), outputFile);
        }
    }

    private static YamlConfigurationLoader createLoader(Path path) {
        return YamlConfigurationLoader.builder()
                .path(path)
                .nodeStyle(NodeStyle.BLOCK)
                .headerMode(HeaderMode.PRESERVE)
                .build();
    }

}
