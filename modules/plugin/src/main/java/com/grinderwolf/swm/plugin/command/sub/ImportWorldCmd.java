package com.grinderwolf.swm.plugin.command.sub;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.grinderwolf.swm.api.exception.InvalidWorldException;
import com.grinderwolf.swm.api.exception.WorldAlreadyExistsException;
import com.grinderwolf.swm.api.exception.WorldLoadedException;
import com.grinderwolf.swm.api.exception.WorldTooBigException;
import com.grinderwolf.swm.api.loader.SlimeLoader;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.loader.LoaderUtils;
import com.grinderwolf.swm.plugin.logging.Logging;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.grinderwolf.swm.plugin.command.CommandManager.COMMAND_PREFIX;

@Getter
public class ImportWorldCmd implements Subcommand {

    private final String usage = "import <path-to-world> <data-source> [new-world-name]";
    private final String description = "Convert a world to the slime format and save it.";
    private final String permission = "swm.importworld";

    private final Cache<String, String[]> importCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length > 1) {
            String dataSource = args[1];
            SlimeLoader loader = LoaderUtils.getLoader(dataSource);

            if (loader == null) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Data source " + dataSource + " does not exist.");
                return true;
            }

            Path worldDir = Paths.get(args[0]);
            if (!Files.isDirectory(worldDir)) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Path " + worldDir + " does not point out to a valid world directory.");
                return true;
            }

            String[] oldArgs = importCache.getIfPresent(sender.getName());
            if (oldArgs != null) {
                importCache.invalidate(sender.getName());

                if (Arrays.equals(args, oldArgs)) { // Make sure it's exactly the same command
                    String worldDirName = worldDir.getFileName().toString();
                    String worldName = (args.length > 2 ? args[2] : worldDirName);
                    sender.sendMessage(COMMAND_PREFIX + "Importing world " + worldDirName + " into data source " + dataSource + "...");

                    Bukkit.getScheduler().runTaskAsynchronously(SWMPlugin.getInstance(), () -> {
                        try {
                            long start = System.currentTimeMillis();
                            SWMPlugin.getInstance().importWorld(worldDir, worldName, loader);
                            sender.sendMessage(COMMAND_PREFIX +  ChatColor.GREEN + "World " + ChatColor.YELLOW + worldName + ChatColor.GREEN + " imported " + "successfully in " + (System.currentTimeMillis() - start) + "ms. Remember to add it to the worlds config file before loading it.");
                        } catch (WorldAlreadyExistsException ex) {
                            sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Data source " + dataSource + " already contains a world called " + worldName + ".");
                        } catch (InvalidWorldException ex) {
                            sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Directory " + worldDirName + " does not contain a valid Minecraft world.");
                        } catch (WorldLoadedException ex) {
                            sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "World " + worldDirName + " is loaded on this server. Please unload it before importing it.");
                        } catch (WorldTooBigException ex) {
                            sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Hey! Didn't you just read the warning? The Slime Format isn't meant for big worlds. The world you provided just breaks everything. Please, trim it by using the MCEdit tool and try again.");
                        } catch (IOException ex) {
                            if (!(sender instanceof ConsoleCommandSender))
                                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to import world " + worldName + ". Take a look at the server console for more information.");

                            Logging.error("Failed to import world '%s'!".formatted(worldName), ex);
                        }

                    });

                    return true;
                }
            }

            sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + ChatColor.BOLD + "WARNING: " + ChatColor.GRAY + "The Slime Format is meant to " +
                    "be used on tiny maps, not big survival worlds. It is recommended to trim your world by using the Prune MCEdit tool to ensure " +
                    "you don't save more chunks than you want to.");

            sender.sendMessage(" ");
            sender.sendMessage(COMMAND_PREFIX + ChatColor.YELLOW + ChatColor.BOLD + "NOTE: " + ChatColor.GRAY + "This command will automatically ignore every chunk that doesn't contain any blocks.");
            sender.sendMessage(" ");
            sender.sendMessage(COMMAND_PREFIX + ChatColor.GRAY + "If you are sure you want to continue, type again this command.");

            importCache.put(sender.getName(), args);
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return args.length == 3 ? new ArrayList<>(LoaderUtils.getAvailableLoadersNames()) : null;
    }

}

