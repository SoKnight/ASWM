package com.grinderwolf.swm.plugin.loader.file;

import com.grinderwolf.swm.api.exception.UnknownWorldException;
import com.grinderwolf.swm.api.loader.SlimeLoader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
public class FileLoader implements SlimeLoader {

    private static final FilenameFilter WORLD_FILE_FILTER = (dir, name) -> name.endsWith(".slime");

    private final Map<String, RandomAccessFile> worldFiles = Collections.synchronizedMap(new HashMap<>());
    private final Path worldDir;

    public FileLoader(Path worldDir) throws IOException {
        this.worldDir = worldDir;

        if (Files.deleteIfExists(worldDir))
            log.warn("A file named '{}' has been deleted, as this is the name used for the worlds directory.", worldDir);

        if (!Files.isDirectory(worldDir)) {
            Files.createDirectories(worldDir);
        }
    }

    @Override
    public byte[] loadWorld(String worldName, boolean readOnly) throws UnknownWorldException, IOException {
        if (!worldExists(worldName))
            throw new UnknownWorldException(worldName);

        RandomAccessFile file = worldFiles.computeIfAbsent(worldName, (world) -> {
            try {
                return new RandomAccessFile(worldDir.resolve(worldName + ".slime").toFile(), "rw");
            } catch (FileNotFoundException ex) {
                return null; // This is never going to happen as we've just checked if the world exists
            }
        });

        if (!readOnly && file != null && file.getChannel().isOpen())
            log.debug("World is unlocked");

        if (file != null && file.length() > Integer.MAX_VALUE)
            throw new IndexOutOfBoundsException("World is too big!");

        byte[] serializedWorld = new byte[0];
        if (file != null) {
            serializedWorld = new byte[(int) file.length()];
            file.seek(0); // Make sure we're at the start of the file
            file.readFully(serializedWorld);
        }

        return serializedWorld;
    }

    @Override
    public boolean worldExists(String worldName) {
        return Files.exists(worldDir.resolve(worldName + ".slime"));
    }

    @Override
    public List<String> listWorlds() throws IOException {
        if (!Files.isDirectory(worldDir))
            throw new NotDirectoryException(worldDir.toAbsolutePath().toString());

        try (Stream<Path> stream = Files.list(worldDir)) {
            return stream.map(Path::getFileName).map(Path::toString)
                    .filter(name -> name.toLowerCase().endsWith(".slime"))
                    .map(name -> name.substring(0, name.length() - 6))
                    .toList();
        }
    }

    @Override
    public void saveWorld(String worldName, byte[] serializedWorld, boolean lock) throws IOException {
        RandomAccessFile worldFile = worldFiles.get(worldName);
        boolean tempFile = worldFile == null;

        if (tempFile)
            worldFile = new RandomAccessFile(worldDir.resolve(worldName + ".slime").toFile(), "rw");

        worldFile.seek(0); // Make sure we're at the start of the file
        worldFile.setLength(0); // Delete old data
        worldFile.write(serializedWorld);

        if (lock) {
            FileChannel channel = worldFile.getChannel();
            try {
                //noinspection ResultOfMethodCallIgnored
                channel.tryLock();
            } catch (OverlappingFileLockException ignored) {
            }
        }

        if (tempFile) {
            worldFile.close();
        }
    }

    @Override
    public void unlockWorld(String worldName) throws UnknownWorldException, IOException {
        if (!worldExists(worldName))
            throw new UnknownWorldException(worldName);

        RandomAccessFile file = worldFiles.remove(worldName);
        if (file != null) {
            FileChannel channel = file.getChannel();
            if (channel.isOpen()) {
                file.close();
            }
        }
    }

    @Override
    public boolean isWorldLocked(String worldName) throws IOException {
        RandomAccessFile file = worldFiles.get(worldName);
        if (file == null)
            file = new RandomAccessFile(worldDir.resolve(worldName + ".slime").toFile(), "rw");

        if (file.getChannel().isOpen()) {
            file.close();
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void deleteWorld(String worldName) throws UnknownWorldException {
        if (!worldExists(worldName)) {
            throw new UnknownWorldException(worldName);
        } else {
            try {
                RandomAccessFile worldFile = worldFiles.get(worldName);
                log.debug("Deleting world '{}'...", worldName);
                RandomAccessFile randomAccessFile = worldFiles.get(worldName);
                unlockWorld(worldName);
                FileUtils.forceDelete(worldDir.resolve(worldName + ".slime").toFile());

                if (randomAccessFile != null) {
                    log.debug("Attempting to delete worldData '{}'...", worldName);
                    worldFile.seek(0); // Make sure we're at the start of the file
                    worldFile.setLength(0); // Delete old data
                    worldFile.write(null);
                    randomAccessFile.close();
                    worldFiles.remove(worldName);
                }

                log.info("World '{}' deleted.", worldName);
            } catch (IOException ex) {
                log.error("Failed to delete world '{}'!", worldName, ex);
            }
        }
    }

}
