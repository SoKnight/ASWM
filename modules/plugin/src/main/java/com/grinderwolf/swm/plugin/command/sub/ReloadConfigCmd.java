package com.grinderwolf.swm.plugin.command.sub;


import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.config.ConfigManager;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.io.IOException;

import static com.grinderwolf.swm.plugin.command.CommandManager.COMMAND_PREFIX;

@Getter
public class ReloadConfigCmd implements Subcommand {

    private final String usage = "reload";
    private final String description = "Reloads the config files.";
    private final String permission = "swm.reload";

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        try {
            ConfigManager.initialize();
        } catch (IOException ex) {
            if (!(sender instanceof ConsoleCommandSender))
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to reload the config file. Take a look at the server console for more information.");

            SWMPlugin.logger().error("Failed to load config files!", ex);
            return true;
        }

        sender.sendMessage(COMMAND_PREFIX + ChatColor.GREEN + "Config reloaded.");
        return true;
    }

}

