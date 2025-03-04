package com.grinderwolf.swm.plugin.command;

import com.grinderwolf.swm.plugin.command.sub.*;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;

public class CommandManager implements TabExecutor {

    public static final String COMMAND_PREFIX = ChatColor.BLUE + ChatColor.BOLD.toString() + "SWM " + ChatColor.GRAY + ">> ";

    private static CommandManager INSTANCE;
    private final Map<String, Subcommand> commands;

    /* A list containing all the worlds that are being performed operations on, so two commands cannot be run at the same time */
    @Getter
    private final Set<String> worldsInUse;

    public CommandManager() {
        INSTANCE = this;
        this.commands = new HashMap<>();
        this.worldsInUse = new HashSet<>();

        this.commands.put("help", new HelpCmd());
        this.commands.put("version", new VersionCmd());
        this.commands.put("goto", new GotoCmd());
        this.commands.put("load", new LoadWorldCmd());
        this.commands.put("load-template", new LoadTemplateWorldCmd());
        this.commands.put("clone-world", new CloneWorldCmd());
        this.commands.put("unload", new UnloadWorldCmd());
        this.commands.put("list", new WorldListCmd());
        this.commands.put("dslist", new DSListCmd());
        this.commands.put("migrate", new MigrateWorldCmd());
        this.commands.put("delete", new DeleteWorldCmd());
        this.commands.put("import", new ImportWorldCmd());
        this.commands.put("reload", new ReloadConfigCmd());
        this.commands.put("create", new CreateWorldCmd());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(COMMAND_PREFIX + ChatColor.AQUA + "Slime World Manager"
                    + ChatColor.GRAY + " is a plugin that implements the Slime Region Format, "
                    + "designed by the Hypixel Dev Team to load and save worlds more efficiently. To check out the help page, type "
                    + ChatColor.YELLOW + "/swm help" + ChatColor.GRAY + "."
            );
            return true;
        }

        Subcommand command = commands.get(args[0]);
        if (command == null) {
            sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Unknown command. To check out the help page, type " + ChatColor.GRAY + "/swm help" + ChatColor.RED + ".");
            return true;
        }

        if (command.inGameOnly() && !(sender instanceof Player)) {
            sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "This command can only be run in-game.");
            return true;
        }

        if (!command.getPermission().isEmpty() && !sender.hasPermission(command.getPermission()) && !sender.hasPermission("swm.*")) {
            sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "You do not have permission to perform this command.");
            return true;
        }

        String[] subCmdArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subCmdArgs, 0, subCmdArgs.length);

        if (!command.onCommand(sender, subCmdArgs))
            sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Command usage: /swm " + ChatColor.GRAY + command.getUsage() + ChatColor.RED + ".");

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> toReturn = null;
        String typed = args[0].toLowerCase();

        if (args.length == 1) {
            for (Map.Entry<String, Subcommand> entry : commands.entrySet()) {
                String name = entry.getKey();
                Subcommand subcommand = entry.getValue();
                if (name.startsWith(typed) && !subcommand.getPermission().isEmpty() && (sender.hasPermission(subcommand.getPermission()) || sender.hasPermission("swm.*"))) {
                    if (name.equalsIgnoreCase("goto") && (sender instanceof ConsoleCommandSender))
                        continue;

                    if (toReturn == null)
                        toReturn = new ArrayList<>();

                    toReturn.add(name);
                }
            }
        }

        if (args.length > 1) {
            String subName = args[0];
            Subcommand subcommand = commands.get(subName);
            if (subcommand != null) {
                toReturn = subcommand.onTabComplete(sender, args);
            }
        }

        return toReturn;
    }

    public Collection<Subcommand> getCommands() {
        return commands.values();
    }

    public static CommandManager getInstance() {
        return INSTANCE;
    }

}
