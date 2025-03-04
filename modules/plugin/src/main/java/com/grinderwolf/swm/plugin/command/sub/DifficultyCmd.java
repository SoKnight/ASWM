package com.grinderwolf.swm.plugin.command.sub;

import org.bukkit.command.CommandSender;

public class DifficultyCmd implements Subcommand {

    private final String usage = "difficulty <difficulty> (<world>)";
    private final String description = "Changes world difficulty";
    private final String permission = "swm.difficulty";

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        return false;
    }

    @Override
    public String getUsage() {
        return null;
    }

    @Override
    public String getDescription() {
        return null;
    }

}
