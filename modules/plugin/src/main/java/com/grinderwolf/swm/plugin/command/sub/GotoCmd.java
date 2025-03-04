package com.grinderwolf.swm.plugin.command.sub;

import com.grinderwolf.swm.plugin.config.ConfigManager;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static com.grinderwolf.swm.plugin.command.CommandManager.COMMAND_PREFIX;

@Getter
public class GotoCmd implements Subcommand {

    private final String usage = "goto <world> [player]";
    private final String description = "Teleport yourself (or someone else) to a world.";
    private final String permission = "swm.goto";

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length > 0) {
            World world = Bukkit.getWorld(args[0]);
            if (world == null) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "World " + args[0] + " does not exist!");
                return true;
            }

            Player target;
            if (args.length > 1) {
                target = Bukkit.getPlayerExact(args[1]);
            } else {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "The console cannot be teleported to a world! Please specify a player.");
                    return true;
                }

                target = (Player) sender;
            }

            if (target == null) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + args[1] + " is offline.");
                return true;
            }

            sender.sendMessage(COMMAND_PREFIX + "Teleporting " + (target.getName().equals(sender.getName()) ? "yourself" : ChatColor.YELLOW + target.getName() + ChatColor.GRAY) + " to " + ChatColor.AQUA + world.getName() + ChatColor.GRAY + "...");

            Location spawnLocation;
            if (ConfigManager.getWorldConfig().getWorlds().containsKey(world.getName())) {
                String spawn = ConfigManager.getWorldConfig().getWorlds().get(world.getName()).getSpawn();
                String[] coords = spawn.split(", ");
                double x = Double.parseDouble(coords[0]);
                double y = Double.parseDouble(coords[1]);
                double z = Double.parseDouble(coords[2]);
                spawnLocation = new Location(world, x, y, z);
            } else {
                spawnLocation = world.getSpawnLocation();
            }

            // Safe Spawn Location
            spawnLocation.setY(0);
            while (spawnLocation.getBlock().getType() != Material.AIR || spawnLocation.getBlock().getRelative(BlockFace.UP).getType() != Material.AIR) {
                if (spawnLocation.getY() >= 256) {
                    spawnLocation.getWorld().getBlockAt(0, 64 ,0).setType(Material.BEDROCK);
                } else {
                    spawnLocation.add(0, 1, 0);
                }
            }

            target.teleportAsync(spawnLocation);
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (sender instanceof ConsoleCommandSender)
            return null;

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
            return toReturn;
        }

        if (args.length == 3) {
            String typed = args[2].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                String playerName = player.getName();
                if (playerName.toLowerCase().startsWith(typed)) {
                    if (toReturn == null)
                        toReturn = new ArrayList<>();

                    toReturn.add(playerName);
                }
            }
        }

        return toReturn;
    }

}
