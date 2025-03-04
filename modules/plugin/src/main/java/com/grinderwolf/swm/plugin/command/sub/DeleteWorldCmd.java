package com.grinderwolf.swm.plugin.command.sub;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.grinderwolf.swm.api.exception.UnknownWorldException;
import com.grinderwolf.swm.api.loader.SlimeLoader;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.command.CommandManager;
import com.grinderwolf.swm.plugin.config.ConfigManager;
import com.grinderwolf.swm.plugin.config.WorldData;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
import com.grinderwolf.swm.plugin.loader.LoaderUtils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.grinderwolf.swm.plugin.command.CommandManager.COMMAND_PREFIX;

@Getter
public class DeleteWorldCmd implements Subcommand {

    private final String usage = "delete <world> [data-source]";
    private final String description = "Delete a world.";
    private final String permission = "swm.deleteworld";

    private final Cache<String, String[]> deleteCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length > 0) {
            String worldName = args[0];
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is loaded on this server! Unload it by running the command " + ChatColor.GRAY + "/swm unload " + worldName + ChatColor.RED + ".");
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
            if (loader == null) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Unknown data source " + source + "!  Are you sure you've typed it correctly?");
                return true;
            }

            if (CommandManager.getInstance().getWorldsInUse().contains(worldName)) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is already being used on another command! Wait some time and try again.");
                return true;
            }

            String[] oldArgs = deleteCache.getIfPresent(sender.getName());

            if (oldArgs != null) {
                deleteCache.invalidate(sender.getName());

                if (Arrays.equals(args, oldArgs)) { // Make sure it's exactly the same command
                    sender.sendMessage(COMMAND_PREFIX + ChatColor.GRAY + "Deleting world " + ChatColor.YELLOW + worldName + ChatColor.GRAY + "...");

                    // No need to do this synchronously
                    CommandManager.getInstance().getWorldsInUse().add(worldName);
                    Bukkit.getScheduler().runTaskAsynchronously(SWMPlugin.getInstance(), () -> {
                        try {
                            if (loader.isWorldLocked(worldName)) {
                                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is being used on another server.");
                                return;
                            }

                            long start = System.currentTimeMillis();
                            loader.deleteWorld(worldName);

                            // Now let's delete it from the config file
                            WorldsConfig config = ConfigManager.getWorldConfig();

                            config.getWorlds().remove(worldName);
                            config.save();

                            sender.sendMessage(COMMAND_PREFIX + ChatColor.GREEN + "World " + ChatColor.YELLOW + worldName + ChatColor.GREEN + " deleted in " + (System.currentTimeMillis() - start) + "ms!");
                        } catch (IOException ex) {
                            if (!(sender instanceof ConsoleCommandSender))
                                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to delete world " + worldName + ". Take a look at the server console for more information.");

                            SWMPlugin.logger().error("Failed to delete world '{}'!", worldName, ex);
                        } catch (UnknownWorldException ex) {
                            sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Data source " + source + " does not contain any world called " + worldName + ".");
                        } finally {
                            CommandManager.getInstance().getWorldsInUse().remove(worldName);
                        }

                    });

                    return true;
                }
            }

            sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + ChatColor.BOLD + "WARNING: " + ChatColor.GRAY + "You're about to delete world " + ChatColor.YELLOW + worldName + ChatColor.GRAY + ". This action cannot be undone.");

            sender.sendMessage(" ");
            sender.sendMessage(ChatColor.GRAY + "If you are sure you want to continue, type again this command.");

            deleteCache.put(sender.getName(), args);
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
            return toReturn;
        }

        if (args.length == 3 || args.length == 4)
            toReturn = new ArrayList<>(LoaderUtils.getAvailableLoadersNames());

        return toReturn;
    }

}

