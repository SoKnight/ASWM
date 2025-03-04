package com.grinderwolf.swm.plugin.command.sub;

import com.grinderwolf.swm.api.exception.UnknownWorldException;
import com.grinderwolf.swm.api.loader.SlimeLoader;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.config.ConfigManager;
import com.grinderwolf.swm.plugin.config.WorldData;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
import com.grinderwolf.swm.plugin.loader.LoaderUtils;
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
        if (args.length > 0) {
            String worldName = args[0];
            World world = Bukkit.getWorld(args[0]);
            if (world == null) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is not loaded!");
                return true;
            }

            String source;
            if (args.length > 1) {
                source = args[1];
            } else {
                WorldsConfig config = ConfigManager.getWorldConfig();
                WorldData worldData = config.getWorlds().get(worldName);
                if (worldData == null) {
                    sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Unknown world " + worldName + "! Are you sure you've typed it correctly?");
                    return true;
                }

                source = worldData.getDataSource();
            }

            SlimeLoader loader = LoaderUtils.getLoader(source);

            // Teleport all players outside the world before unloading it
            List<Player> players = world.getPlayers();
            if (!players.isEmpty()) {
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

                for (Player player : players) {
                    player.teleportAsync(spawnLocation);
                }
            }

            if (Bukkit.unloadWorld(world, true)) {
                SWMPlugin.logger().debug("Attempting to unload world '{}'...", worldName);

                try {
                    if (loader.isWorldLocked(worldName)) {
                        SWMPlugin.logger().debug("World '{}' is locked.", worldName);
                        loader.unlockWorld(worldName);
                        SWMPlugin.logger().info("Attempted to unlock world '{}'.", worldName);
                    }
                } catch (UnknownWorldException | IOException ex) {
                    SWMPlugin.logger().error("Failed to unload world '{}'!", worldName, ex);
                }

                sender.sendMessage(COMMAND_PREFIX + ChatColor.GREEN + "World " + ChatColor.YELLOW + worldName + ChatColor.GREEN + " unloaded correctly.");
            } else {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to unload world " + worldName + ".");
            }

            return true;
        }

        return false;
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

