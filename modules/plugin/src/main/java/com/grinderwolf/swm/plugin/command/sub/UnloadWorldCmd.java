package com.grinderwolf.swm.plugin.command.sub;

import com.grinderwolf.swm.api.exception.UnknownWorldException;
import com.grinderwolf.swm.api.loader.SlimeLoader;
import com.grinderwolf.swm.plugin.config.ConfigManager;
import com.grinderwolf.swm.plugin.config.WorldData;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
import com.grinderwolf.swm.plugin.loader.LoaderUtils;
import com.grinderwolf.swm.plugin.logging.Logging;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.grinderwolf.swm.plugin.command.CommandManager.COMMAND_PREFIX;

@Getter
public class UnloadWorldCmd implements Subcommand {

    private final String usage = "unload <world> [data-source]";
    private final String description = "Unload a world.";
    private final String permission = "swm.unloadworld";

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length == 0)
            return false;

        String worldName = args[0];

        World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is not loaded!");
            return true;
        }

        String source = null;
        if (args.length > 1) {
            source = args[1];
        } else {
            WorldsConfig config = ConfigManager.getWorldConfig();
            WorldData worldData = config.getWorlds().get(worldName);
            if (worldData != null && !worldData.isReadOnly()) {
                source = worldData.getDataSource();
            }
        }

        SlimeLoader loader = source == null ? null : LoaderUtils.getLoader(source);

        // Teleport all players outside the world before unloading it
        List<Player> players = world.getPlayers();
        if (!players.isEmpty()) {
            Location spawnLocation = findValidDefaultSpawn();
            players.forEach(player -> player.teleportAsync(spawnLocation));
        }

        if (!Bukkit.unloadWorld(world, true)) {
            sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to unload world " + worldName + ".");
            return true;
        }

        Logging.info("Attempting to unload world '%s'...", worldName);
        try {
            if (loader != null && loader.isWorldLocked(worldName)) {
                Logging.info("World '%s' is locked.", worldName);
                loader.unlockWorld(worldName);
                Logging.info("Attempted to unlock world '%s'.", worldName);
            } else {
                Logging.info("World '%s' was not unlocked. This could be because the world is either unlocked or not in the config. This is not an error.", worldName);
            }
        } catch (UnknownWorldException | IOException ex) {
            Logging.error("Failed to unload world '%s'.".formatted(worldName), ex);
        }

        sender.sendMessage(COMMAND_PREFIX + ChatColor.GREEN + "World " + ChatColor.YELLOW + worldName + ChatColor.GREEN + " unloaded correctly.");
        return true;
    }

    private Location findValidDefaultSpawn() {
        World defaultWorld = Bukkit.getWorlds().get(0);
        Location spawnLocation = defaultWorld.getSpawnLocation();
        spawnLocation.setY(64);

        while (spawnLocation.getBlock().getType() != Material.AIR || spawnLocation.getBlock().getRelative(BlockFace.UP).getType() != Material.AIR) {
            if (spawnLocation.getY() >= 256) {
                spawnLocation.getWorld().getBlockAt(0, 64 ,0).setType(Material.BEDROCK);
            } else {
                spawnLocation.add(0, 1, 0);
            }
        }

        return spawnLocation;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> toReturn = null;

        if (args.length == 2) {
            String typed = args[1].toLowerCase();
            for (World world : Bukkit.getWorlds()) {
                String worldName = world.getName();
                if (worldName.toLowerCase().startsWith(typed)) {
                    if (toReturn == null)
                        toReturn = new ArrayList<>();

                    toReturn.add(worldName);
                }
            }
        }

        return toReturn;
    }

}

