package com.grinderwolf.swm.plugin.command.sub;


import com.grinderwolf.swm.api.exception.*;
import com.grinderwolf.swm.api.loader.SlimeLoader;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.command.CommandManager;
import com.grinderwolf.swm.plugin.config.ConfigManager;
import com.grinderwolf.swm.plugin.config.WorldData;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
import com.grinderwolf.swm.plugin.loader.LoaderUtils;
import com.grinderwolf.swm.plugin.logging.Logging;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.grinderwolf.swm.plugin.command.CommandManager.COMMAND_PREFIX;

@Getter
public class CloneWorldCmd implements Subcommand {

    private final String usage = "clone-world <template-world> <world-name> [new-data-source]";
    private final String description = "Clones a world";
    private final String permission = "swm.cloneworld";

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length > 1) {
            String worldName = args[1];
            World world = Bukkit.getWorld(worldName);

            if (world != null) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is already loaded!");
                return true;
            }

            String templateWorldName = args[0];
            WorldsConfig config = ConfigManager.getWorldConfig();
            WorldData worldData = config.getWorlds().get(templateWorldName);

            if (worldData == null) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to find world " + templateWorldName + " inside the worlds config file.");
                return true;
            }

            if (templateWorldName.equals(worldName)) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "The template world name cannot be the same as the cloned world one!");
                return true;
            }

            if (CommandManager.getInstance().getWorldsInUse().contains(worldName)) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is already being used on another command! Wait some time and try again.");
                return true;
            }

            String dataSource = args.length > 2 ? args[2] : worldData.getDataSource();
            SlimeLoader initLoader = SWMPlugin.getInstance().getLoader(worldData.getDataSource());
            SlimeLoader loader = SWMPlugin.getInstance().getLoader(dataSource);

            if (loader == null) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Unknown data source " + dataSource + "!");
                return true;
            }

            CommandManager.getInstance().getWorldsInUse().add(worldName);
            sender.sendMessage(COMMAND_PREFIX + ChatColor.GRAY + "Creating world " + ChatColor.YELLOW + worldName + ChatColor.GRAY + " using " + ChatColor.YELLOW + templateWorldName + ChatColor.GRAY + " as a template...");

            // It's best to load the world async, and then just go back to the server thread and add it to the world list
            Bukkit.getScheduler().runTaskAsynchronously(SWMPlugin.getInstance(), () -> {
                try {
                    long start = System.currentTimeMillis();
                    SlimeWorld slimeWorld = SWMPlugin.getInstance().loadWorld(initLoader, templateWorldName, true, worldData.toPropertyMap()).clone(worldName, loader);
                    Bukkit.getScheduler().runTask(SWMPlugin.getInstance(), () -> {
                        try {
                            SWMPlugin.getInstance().generateWorld(slimeWorld);
                            config.getWorlds().put(worldName, worldData);
                            config.save();
                        } catch (IllegalArgumentException ex) {
                            sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to generate world " + worldName + ": " + ex.getMessage() + ".");
                            return;
                        }

                        sender.sendMessage(COMMAND_PREFIX + ChatColor.GREEN + "World " + ChatColor.YELLOW + worldName + ChatColor.GREEN + " loaded and generated in " + (System.currentTimeMillis() - start) + "ms!");
                    });
                } catch (WorldAlreadyExistsException ex) {
                    sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "There is already a world called " + worldName + " stored in " + dataSource + ".");
                } catch (CorruptedWorldException ex) {
                    if (!(sender instanceof ConsoleCommandSender))
                        sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to load world " + templateWorldName + ": world seems to be corrupted.");

                    Logging.error("Failed to load world '%s': world seems to be corrupted.".formatted(templateWorldName), ex);
                } catch (NewerFormatException ex) {
                    sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to load world " + templateWorldName + ": this world was serialized with a newer version of the Slime Format (" + ex.getMessage() + ") that SWM cannot understand.");
                } catch (UnknownWorldException ex) {
                    sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to load world " + templateWorldName + ": world could not be found (using data source '" + worldData.getDataSource() + "').");
                } catch (IllegalArgumentException ex) {
                    sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to load world " + templateWorldName + ": " + ex.getMessage());
                } catch (IOException ex) {
                    if (!(sender instanceof ConsoleCommandSender))
                        sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to load world " + templateWorldName + ". Take a look at the server console for more information.");

                    Logging.error("Failed to load world '%s'!".formatted(templateWorldName), ex);
                } catch (WorldInUseException ignored) { } finally {
                    CommandManager.getInstance().getWorldsInUse().remove(worldName);
                }
            });

            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return args.length == 4 ? new ArrayList<>(LoaderUtils.getAvailableLoadersNames()) : null;
    }

}

