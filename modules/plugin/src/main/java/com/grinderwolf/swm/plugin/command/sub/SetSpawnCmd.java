package com.grinderwolf.swm.plugin.command.sub;


import com.grinderwolf.swm.plugin.config.ConfigManager;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static com.grinderwolf.swm.plugin.command.CommandManager.COMMAND_PREFIX;

@Getter
public class SetSpawnCmd implements Subcommand {

    private final String usage = "setspawn <world>";
    private final String description = "Set the spawnpoint of a world based on your location.";
    private final String permission = "swm.setspawn";

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "This command is for players");
            return true;
        }

        if (args.length > 0) {
            String worldName = args[0];

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " does not exist!");
                return true;
            }

            Player player = (Player) sender;
            Location playerLoc = player.getLocation();
            world.setSpawnLocation(playerLoc);

            WorldsConfig config = ConfigManager.getWorldConfig();
            if (!(config.getWorlds().containsKey(world.getName()))) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "World " + ChatColor.YELLOW + world.getName() + ChatColor.RED + " is not a slime world.");
                return true;
            }

            String spawnVerbose = playerLoc.getX() + ", " + playerLoc.getY() + ", " + playerLoc.getZ();
            config.getWorlds().get(world.getName()).setSpawn(spawnVerbose);
            config.save();

            sender.sendMessage(COMMAND_PREFIX + ChatColor.GREEN + "Set spawn for " + ChatColor.YELLOW + worldName + ChatColor.GREEN + ".");
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> toReturn = null;

        if (args.length == 2 && sender instanceof Player) {
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
