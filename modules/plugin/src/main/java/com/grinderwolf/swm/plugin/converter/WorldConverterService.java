package com.grinderwolf.swm.plugin.converter;

import com.grinderwolf.swm.nms.CraftSlimeWorld;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.converter.spec.NetherUpdateWorldConverter;
import com.grinderwolf.swm.plugin.converter.spec.VillageAndPillageWorldConverter;
import com.grinderwolf.swm.plugin.logging.Logging;

import java.util.Map;

public class WorldConverterService {

    private static final Map<Byte, WorldConverter> CONVERTERS = Map.of(
            (byte) 0x05, VillageAndPillageWorldConverter.INSTANCE,
            (byte) 0x06, VillageAndPillageWorldConverter.INSTANCE,
            (byte) 0x07, NetherUpdateWorldConverter.INSTANCE
    );

    public static void upgradeWorld(CraftSlimeWorld world) {
        byte serverVersion = SWMPlugin.getInstance().getPlatform().getWorldVersion();
        for (byte version = (byte) (world.getVersion() + 1); version <= serverVersion; version++) {
            WorldConverter upgrade = CONVERTERS.get(version);
            if (upgrade == null) {
                Logging.warn("Missing world converter for version '%s': world will not be upgraded.", version);
                continue;
            }

            upgrade.upgrade(world);
        }

        world.setVersion(serverVersion);
    }

    public static void downgradeWorld(CraftSlimeWorld world) {
        byte serverVersion = SWMPlugin.getInstance().getPlatform().getWorldVersion();
        for (byte version = world.getVersion(); version > serverVersion; version--) {
            WorldConverter upgrade = CONVERTERS.get(version);
            if (upgrade == null) {
                Logging.warn("Missing world converter for version '%s': world will not be downgraded.", version);
                continue;
            }

            upgrade.downgrade(world);
        }

        world.setVersion(serverVersion);
    }

}
