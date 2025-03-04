package com.grinderwolf.swm.plugin.command.sub;

import org.bukkit.command.CommandSender;

import java.util.List;

public interface Subcommand {

    boolean onCommand(CommandSender sender, String[] args);

    default List<String> onTabComplete(CommandSender sender, String[] args) {
        return null;
    }

    String getUsage();

    String getDescription();

    default boolean inGameOnly() {
        return false;
    }

    default String getPermission() {
        return "";
    }

}
