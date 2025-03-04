package com.grinderwolf.swm.plugin.command.sub;

import com.grinderwolf.swm.api.exception.UnknownWorldException;
import com.grinderwolf.swm.api.exception.WorldAlreadyExistsException;
import com.grinderwolf.swm.api.exception.WorldInUseException;
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
import java.util.List;

import static com.grinderwolf.swm.plugin.command.CommandManager.COMMAND_PREFIX;

@Getter
public class MigrateWorldCmd implements Subcommand {

    private final String usage = "migrate <world> <new-data-source>";
    private final String description = "Migrate a world from one data source to another.";
    private final String permission = "swm.migrate";

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length > 1) {
            String worldName = args[0];
            WorldsConfig config = ConfigManager.getWorldConfig();
            WorldData worldData = config.getWorlds().get(worldName);
            if (worldData == null) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Unknown world " + worldName + "! Are you sure you configured it correctly?");
                return true;
            }

            String newSource = args[1];
            SlimeLoader newLoader = LoaderUtils.getLoader(newSource);
            if (newLoader == null) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Unknown data source " + newSource + "!");
                return true;
            }

            String currentSource = worldData.getDataSource();
            if (newSource.equalsIgnoreCase(currentSource)) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is already stored using data source " + currentSource + "!");
                return true;
            }

            SlimeLoader oldLoader = LoaderUtils.getLoader(currentSource);
            if (oldLoader == null) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Unknown data source " + currentSource + "! Are you sure you configured it correctly?");
                return true;
            }

            if (CommandManager.getInstance().getWorldsInUse().contains(worldName)) {
                sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is already being used on another command! Wait some time and try again.");
                return true;
            }

            CommandManager.getInstance().getWorldsInUse().add(worldName);

            Bukkit.getScheduler().runTaskAsynchronously(SWMPlugin.getInstance(), () -> {
                try {
                    long start = System.currentTimeMillis();
                    SWMPlugin.getInstance().migrateWorld(worldName, oldLoader, newLoader);

                    worldData.setDataSource(newSource);
                    config.save();

                    sender.sendMessage(COMMAND_PREFIX + ChatColor.GREEN + "World " + ChatColor.YELLOW + worldName + ChatColor.GREEN + " migrated in " + (System.currentTimeMillis() - start) + "ms!");
                } catch (IOException ex) {
                    if (!(sender instanceof ConsoleCommandSender))
                        sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Failed to migrate world " + worldName + " (using data sources " + currentSource + " and " + newSource + "). Take a look at the server console for more information.");

                    SWMPlugin.logger().error("Failed to load world '{}', using data source '{}'!", worldName, currentSource, ex);
                } catch (WorldInUseException ex) {
                    sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is being used on another server.");
                } catch (WorldAlreadyExistsException ex) {
                    sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Data source " + newSource + " already contains a world named " + worldName + "!");
                } catch (UnknownWorldException ex) {
                    sender.sendMessage(COMMAND_PREFIX + ChatColor.RED + "Can't find world " + worldName + " in data source " + currentSource + ".");
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
        if (args.length == 2) {
            List<String> toReturn = new ArrayList<>();
            String typed = args[1].toLowerCase();
            for (World world : Bukkit.getWorlds()) {
                String worldName = world.getName();
                if (worldName.toLowerCase().startsWith(typed)) {
                    toReturn.add(worldName);
                }
            }
            return toReturn;
        }

        return args.length == 3 ? new ArrayList<>(LoaderUtils.getAvailableLoadersNames()) : null;
    }

}

