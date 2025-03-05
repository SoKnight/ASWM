package com.grinderwolf.swm.plugin.command.sub;


import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

import static com.grinderwolf.swm.plugin.command.CommandManager.COMMAND_PREFIX;

@Getter
public class SaveWorldCmd implements Subcommand {

    private final String usage = "save <world>";
    private final String description = "Saves a world.";
    private final String permission = "swm.saveworld";

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length > 0) {
            String worldName = args[0];

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is not loaded!");
                return true;
            }

            world.save();
            sender.sendMessage(COMMAND_PREFIX + ChatColor.GREEN + "World " + ChatColor.YELLOW + worldName + ChatColor.GREEN + " saved correctly.");
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
