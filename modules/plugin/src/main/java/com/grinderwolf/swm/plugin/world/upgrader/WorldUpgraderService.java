package com.grinderwolf.swm.plugin.world.upgrader;

import com.grinderwolf.swm.nms.CraftSlimeWorld;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.world.upgrader.spec.NetherUpdateWorldUpgrader;
import com.grinderwolf.swm.plugin.world.upgrader.spec.VillageAndPillageWorldUpgrader;
import com.grinderwolf.swm.plugin.logging.Logging;

import java.util.Map;

public class WorldUpgraderService {

    private static final Map<Byte, WorldUpgrader> UPGRADERS = Map.of(
            (byte) 0x05, VillageAndPillageWorldUpgrader.INSTANCE,
            (byte) 0x06, VillageAndPillageWorldUpgrader.INSTANCE,
            (byte) 0x07, NetherUpdateWorldUpgrader.INSTANCE
    );

    public static void upgradeWorld(CraftSlimeWorld world) {
        byte serverVersion = SWMPlugin.getInstance().getPlatform().getWorldVersion();
        for (byte version = (byte) (world.getVersion() + 1); version <= serverVersion; version++) {
            WorldUpgrader upgrader = UPGRADERS.get(version);
            if (upgrader == null) {
                Logging.warn("Missing world upgrader for version '%s': world will not be upgraded.", version);
                continue;
            }

            upgrader.upgrade(world);
        }

        world.setVersion(serverVersion);
    }

}
