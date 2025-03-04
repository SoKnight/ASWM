package com.grinderwolf.swm.plugin.command.sub;


import com.grinderwolf.swm.api.exception.CorruptedWorldException;
import com.grinderwolf.swm.api.exception.NewerFormatException;
import com.grinderwolf.swm.api.exception.UnknownWorldException;
import com.grinderwolf.swm.api.exception.WorldInUseException;
import com.grinderwolf.swm.api.loader.SlimeLoader;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.command.CommandManager;
import com.grinderwolf.swm.plugin.config.ConfigManager;
import com.grinderwolf.swm.plugin.config.WorldData;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
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
public class LoadWorldCmd implements Subcommand {

    private final String usage = "load <world>";
    private final String description = "Load a world.";
    private final String permission = "swm.loadworld";

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length > 0) {
            String worldName = args[0];
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is already loaded!");
                return true;
            }

            WorldsConfig config = ConfigManager.getWorldConfig();
            WorldData worldData = config.getWorlds().get(worldName);
            if (worldData == null) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to find world " + worldName + " inside the worlds config file.");
                return true;
            }

            if (CommandManager.getInstance().getWorldsInUse().contains(worldName)) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is already being used on another command! Wait some time and try again.");
                return true;
            }

            CommandManager.getInstance().getWorldsInUse().add(worldName);
            sender.sendMessage(COMMAND_PREFIX + ChatColor.GRAY + "Loading world " + ChatColor.YELLOW + worldName + ChatColor.GRAY + "...");

            // It's best to load the world async, and then just go back to the server thread and add it to the world list
            Bukkit.getScheduler().runTaskAsynchronously(SWMPlugin.getInstance(), () -> {
                try {
                    // ATTEMPT TO LOAD WORLD
                    long start = System.currentTimeMillis();
                    SlimeLoader loader = SWMPlugin.getInstance().getLoader(worldData.getDataSource());
                    if (loader == null)
                        throw new IllegalArgumentException("invalid data source " + worldData.getDataSource());

                    SlimeWorld slimeWorld = SWMPlugin.getInstance().loadWorld(loader, worldName, worldData.isReadOnly(), worldData.toPropertyMap());
                    Bukkit.getScheduler().runTask(SWMPlugin.getInstance(), () -> {
                        try {
                            SWMPlugin.getInstance().generateWorld(slimeWorld);
                        } catch (IllegalArgumentException ex) {
                            sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to generate world " + worldName + ": " + ex.getMessage() + ".");
                            return;
                        }

                        sender.sendMessage(COMMAND_PREFIX + ChatColor.GREEN + "World " + ChatColor.YELLOW + worldName + ChatColor.GREEN + " loaded and generated in " + (System.currentTimeMillis() - start) + "ms!");
                    });
                } catch (CorruptedWorldException ex) {
                    if (!(sender instanceof ConsoleCommandSender))
                        sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to load world " + worldName + ": world seems to be corrupted.");

                    SWMPlugin.logger().error("Failed to load world '{}': world seems to be corrupted.", worldName, ex);
                } catch (NewerFormatException ex) {
                    sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to load world " + worldName + ": this world" +
                            " was serialized with a newer version of the Slime Format (" + ex.getMessage() + ") that SWM cannot understand.");
                } catch (UnknownWorldException ex) {
                    sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to load world " + worldName +
                            ": world could not be found (using data source '" + worldData.getDataSource() + "').");
                } catch (WorldInUseException ex) {
                    sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to load world " + worldName +
                            ": world is already in use. If you think this is a mistake, please wait some time and try again.");
                } catch (IllegalArgumentException ex) {
                    sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to load world " + worldName +
                            ": " + ex.getMessage());
                } catch (IOException ex) {
                    if (!(sender instanceof ConsoleCommandSender))
                        sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to load world " + worldName + ". Take a look at the server console for more information.");

                    SWMPlugin.logger().error("Failed to load world '{}'!", worldName, ex);
                } finally {
                    CommandManager.getInstance().getWorldsInUse().remove(worldName);
                }
            });

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

