package com.grinderwolf.swm.plugin.config;

import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;
import lombok.Data;
import org.bukkit.Difficulty;
import org.bukkit.World.Environment;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import static com.grinderwolf.swm.api.world.properties.SlimeProperties.*;

@Data
@ConfigSerializable
public class WorldData {

    @Setting("source")
    private String dataSource = "file";

    @Setting("spawn")
    private String spawn = "0.5, 255, 0.5";

    @Setting("difficulty")
    private String difficulty = "peaceful";

    @Setting("allowMonsters")
    private boolean allowMonsters = true;
    @Setting("allowAnimals")
    private boolean allowAnimals = true;

    @Setting("dragonBattle")
    private boolean dragonBattle = false;

    @Setting("pvp")
    private boolean pvp = true;

    @Setting("environment")
    private String environment = "NORMAL";
    @Setting("worldType")
    private String worldType = "DEFAULT";
    @Setting("defaultBiome")
    private String defaultBiome = "minecraft:plains";

    @Setting("loadOnStartup")
    private boolean loadOnStartup = true;
    @Setting("readOnly")
    private boolean readOnly = false;

    @SuppressWarnings("deprecation")
    public SlimePropertyMap toPropertyMap() {
        try {
            Enum.valueOf(Difficulty.class, this.difficulty.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown difficulty '%s'".formatted(this.difficulty));
        }

        String[] spawnLocationSplit = spawn.split(", ");
        double spawnX, spawnY, spawnZ;

        try {
            spawnX = Double.parseDouble(spawnLocationSplit[0]);
            spawnY = Double.parseDouble(spawnLocationSplit[1]);
            spawnZ = Double.parseDouble(spawnLocationSplit[2]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
            throw new IllegalArgumentException("invalid spawn location '%s'".formatted(this.spawn));
        }

        String environment = this.environment;
        try {
            Enum.valueOf(Environment.class, environment.toUpperCase());
        } catch (IllegalArgumentException ex) {
            try {
                int envId = Integer.parseInt(environment);
                if (envId < -1 || envId > 1)
                    throw new NumberFormatException(environment);

                environment = Environment.getEnvironment(envId).name();
            } catch (NumberFormatException ex2) {
                throw new IllegalArgumentException("unknown environment '%s'".formatted(this.environment));
            }
        }

        SlimePropertyMap propertyMap = new SlimePropertyMap();
        propertyMap.setValue(SPAWN_X, (int) spawnX);
        propertyMap.setValue(SPAWN_Y, (int) spawnY);
        propertyMap.setValue(SPAWN_Z, (int) spawnZ);

        propertyMap.setValue(DIFFICULTY, difficulty);
        propertyMap.setValue(ALLOW_MONSTERS, allowMonsters);
        propertyMap.setValue(ALLOW_ANIMALS, allowAnimals);
        propertyMap.setValue(DRAGON_BATTLE, dragonBattle);
        propertyMap.setValue(PVP, pvp);
        propertyMap.setValue(ENVIRONMENT, environment);
        propertyMap.setValue(WORLD_TYPE, worldType);
        propertyMap.setValue(DEFAULT_BIOME, defaultBiome);
        return propertyMap;
    }

}
