package com.grinderwolf.swm.plugin.command.sub;

import com.grinderwolf.swm.api.util.SlimeFormat;
import com.grinderwolf.swm.plugin.SWMPlugin;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import static com.grinderwolf.swm.plugin.command.CommandManager.COMMAND_PREFIX;

@Getter
public class VersionCmd implements Subcommand {

    private final String usage = "version";
    private final String description = "Shows the plugin version.";

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        sender.sendMessage(new String[] {
                COMMAND_PREFIX + ChatColor.GRAY + "This server is running SWM " + ChatColor.YELLOW + "v" + SWMPlugin.getInstance().getDescription().getVersion() + ChatColor.GRAY + ".",
                COMMAND_PREFIX + ChatColor.GRAY + "Support Slime Format version up to " + ChatColor.AQUA + "v" + SlimeFormat.SLIME_VERSION + ChatColor.GRAY + ".",
                COMMAND_PREFIX + ChatColor.GRAY + "* Patched by SoKnight, specially for StarMC."
        });
        return true;
    }

}
