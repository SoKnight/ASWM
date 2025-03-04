package com.grinderwolf.swm.plugin.world.importer;

import java.util.Map;

public record LevelData(int version, Map<String, String> gameRules, int spawnX, int spawnY, int spawnZ) {

}
