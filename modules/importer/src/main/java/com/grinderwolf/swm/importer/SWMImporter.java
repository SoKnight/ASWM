package com.grinderwolf.swm.importer;

import com.flowpowered.nbt.*;
import com.flowpowered.nbt.stream.NBTInputStream;
import com.flowpowered.nbt.stream.NBTOutputStream;
import com.github.luben.zstd.Zstd;
import com.github.tomaslanger.chalk.Chalk;
import com.grinderwolf.swm.api.exception.InvalidWorldException;
import com.grinderwolf.swm.api.util.NibbleArray;
import com.grinderwolf.swm.api.util.SlimeFormat;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import com.grinderwolf.swm.nms.CraftSlimeChunk;
import com.grinderwolf.swm.nms.CraftSlimeChunkSection;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * The SWMImporter class provides the ability to convert
 * a vanilla world folder into a slime file.
 * <p>
 * The importer may be run directly as executable or
 * used as dependency in your own plugins.
 */
public class SWMImporter {

    private static final Pattern MAP_FILE_PATTERN = Pattern.compile("^map_([0-9]*).dat$");
    private static final int SECTOR_SIZE = 4096;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java -jar aswm-importer.jar <path-to-world-folder> [--accept] [--silent] [--print-error]");
            return;
        }

        Path worldDir = Paths.get(args[0]);
        Path outputFile = getDestinationFile(worldDir);

        List<String> argList = Arrays.asList(args);
        boolean hasAccepted = argList.contains("--accept");
        boolean isSilent = argList.contains("--silent");
        boolean printErrors = argList.contains("--print-error");

        if(!hasAccepted) {
            System.out.println("**** WARNING ****");
            System.out.println("The Slime Format is meant to be used on tiny maps, not big survival worlds.");
            System.out.println("It is recommended to trim your world by using the Prune MCEdit tool to ensure you don't save more chunks than you want to.");
            System.out.println();
            System.out.println("NOTE: This utility will automatically ignore every chunk that doesn't contain any blocks.");
            System.out.print("Do you want to continue? [Y/N]: ");

            Scanner scanner = new Scanner(System.in);
            String response = scanner.next();

            if(!response.equalsIgnoreCase("Y")) {
                System.out.println("Your wish is my command.");
                return;
            }
        }

        try {
            importWorld(worldDir, outputFile, !isSilent);
        } catch (IndexOutOfBoundsException ex) {
            System.err.println("Oops, it looks like the world provided is too big to be imported. " +
                "Please trim it by using the MCEdit tool and try again.");
        } catch (IOException ex) {
            System.err.println("Failed to save the world file.");
            //noinspection CallToPrintStackTrace
            ex.printStackTrace();
        } catch (InvalidWorldException ex) {
            if(printErrors) {
                //noinspection CallToPrintStackTrace
                ex.printStackTrace();
            } else {
                System.err.println(ex.getMessage());
            }
        }
    }

    /**
     * Returns a destination file at which the slime file will
     * be placed when run as an executable.
     * <p>
     * This method may be used by your plugin to output slime
     * files identical to the executable.
     *
     * @param worldFolder The world directory to import
     * @return The output file destination
     */
    public static Path getDestinationFile(Path worldFolder) {
        return worldFolder.getParent().resolve(worldFolder.getFileName() + ".slime");
    }

    /**
     * Import the given vanilla world directory into
     * a slime world file. The debug boolean may be
     * set to true in order to provide debug prints.
     *
     * @param worldFolder The world directory to import
     * @param outputFile The output file
     * @param debug Whether debug messages should be printed to sysout
     * @throws IOException when the world could not be saved
     * @throws InvalidWorldException when the world is not valid
     * @throws IndexOutOfBoundsException if the world was too big
     */
    public static void importWorld(Path worldFolder, Path outputFile, boolean debug) throws IOException, InvalidWorldException {
        if (!Files.exists(worldFolder))
            throw new InvalidWorldException(worldFolder, "Are you sure the directory exists?");

        if (!Files.isDirectory(worldFolder))
            throw new InvalidWorldException(worldFolder, "It appears to be a regular file");

        Path regionDir = worldFolder.resolve("region");
        if (!Files.isDirectory(regionDir))
            throw new InvalidWorldException(worldFolder, "The world appears to be corrupted");

        if (debug)
            System.out.println("Loading world...");

        Path levelFile = worldFolder.resolve("level.dat");
        if (!Files.isRegularFile(levelFile))
            throw new InvalidWorldException(worldFolder, "The world appears to be corrupted");

        LevelData data;
        try {
            data = readLevelData(levelFile);
        } catch (IOException ex) {
            throw new IOException("Failed to load world level file", ex);
        }

        // World version
        byte worldVersion;

        if (data.version() == -1) { // DataVersion tag was added in 1.9
            worldVersion = 0x01;
        } else if (data.version() < 818) {
            worldVersion = 0x02; // 1.9 world
        } else if (data.version() < 1501) {
            worldVersion = 0x03; // 1.11 world
        } else if (data.version() < 1517) {
            worldVersion = 0x04; // 1.13 world
        } else if (data.version() < 2566) {
            worldVersion = 0x05; // 1.14 world
        } else {
            worldVersion = 0x07;
        }

        if (debug)
            System.out.printf("World version: %s%n", worldVersion);

        List<SlimeChunk> chunks = new ArrayList<>();
        try (Stream<Path> stream = Files.list(regionDir)) {
            var iterator = stream.filter(path -> path.getFileName().toString().endsWith(".mca")).iterator();
            while (iterator.hasNext()) {
                try {
                    chunks.addAll(loadChunks(iterator.next(), worldVersion, debug));
                } catch (IOException ex) {
                    throw new IOException("Failed to read region file", ex);
                }
            }
        }

        if (debug)
            System.out.printf("World %s contains %d chunks.%n", worldFolder, chunks.size());

        // World maps
        Path dataDir = worldFolder.resolve("data");
        List<CompoundTag> maps = new ArrayList<>();

        if (Files.exists(dataDir)) {
            if (!Files.isDirectory(dataDir))
                throw new InvalidWorldException(worldFolder, "The data directory appears to be invalid");

            try (Stream<Path> stream = Files.list(dataDir)) {
                var iterator = stream.filter(path -> MAP_FILE_PATTERN.matcher(path.getFileName().toString()).matches()).iterator();
                while (iterator.hasNext()) {
                    try {
                        maps.add(loadMap(iterator.next()));
                    } catch (IOException ex) {
                        throw new IOException("Failed to read world map", ex);
                    }
                }
            }
        }

        long start = System.currentTimeMillis();
        byte[] slimeFormattedWorld = generateSlimeWorld(chunks, worldVersion, data, maps);
        long duration = System.currentTimeMillis() - start;

        if (debug)
            System.out.println(Chalk.on("World %s successfully serialized to the Slime Format in %d ms!".formatted(worldFolder.getFileName(), duration)).green());

        if (!Files.isDirectory(outputFile.getParent()))
            Files.createDirectories(outputFile.getParent());

        try (var stream = Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            stream.write(slimeFormattedWorld);
            stream.flush();
        }
    }

    private static LevelData readLevelData(Path file) throws IOException, InvalidWorldException {
        try (NBTInputStream nbtStream = new NBTInputStream(Files.newInputStream(file))) {
            CompoundTag dataTag = nbtStream.readTag().getAsCompoundTag()
                    .flatMap(t -> t.getAsCompoundTag("Data"))
                    .orElseThrow(() -> new InvalidWorldException(file.getParent()));

            // Data version
            int dataVersion = dataTag.getIntValue("DataVersion").orElse(-1);

            // Game rules
            Map<String, String> gameRules = new HashMap<>();
            Optional<CompoundTag> rulesList = dataTag.getAsCompoundTag("GameRules");

            rulesList.ifPresent(compoundTag -> compoundTag.getValue()
                    .forEach((ruleName, ruleTag) -> gameRules.put(
                            ruleName,
                            ruleTag.getAsStringTag().orElseThrow().getValue()
                    )));

            return new LevelData(dataVersion, gameRules);
        }
    }

    private static CompoundTag loadMap(Path mapFile) throws IOException {
        String fileName = mapFile.getFileName().toString();
        int mapId = Integer.parseInt(fileName.substring(4, fileName.length() - 4));

        NBTInputStream nbtStream = new NBTInputStream(Files.newInputStream(mapFile), NBTInputStream.GZIP_COMPRESSION, ByteOrder.BIG_ENDIAN);
        CompoundTag tag = nbtStream.readTag().getAsCompoundTag().flatMap(t -> t.getAsCompoundTag("data")).orElseThrow();
        tag.getValue().put("id", new IntTag("id", mapId));
        return tag;
    }

    private static List<SlimeChunk> loadChunks(Path file, byte worldVersion, boolean debug) throws IOException {
        if (debug)
            System.out.printf("Loading chunks from region file '%s':%n", file);

        byte[] regionByteArray = Files.readAllBytes(file);
        DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(regionByteArray));

        List<ChunkEntry> chunks = new ArrayList<>(1024);
        for (int i = 0; i < 1024; i++) {
            int entry = inputStream.readInt();
            int chunkOffset = entry >>> 8;
            int chunkSize = entry & 15;
            if (entry != 0) {
                ChunkEntry chunkEntry = new ChunkEntry(chunkOffset * SECTOR_SIZE, chunkSize * SECTOR_SIZE);
                chunks.add(chunkEntry);
            }
        }

        List<SlimeChunk> loadedChunks = chunks.stream().map((entry) -> {
            try {
                DataInputStream headerStream = new DataInputStream(new ByteArrayInputStream(regionByteArray, entry.offset(), entry.paddedSize()));

                int chunkSize = headerStream.readInt() - 1;
                int compressionScheme = headerStream.readByte();

                DataInputStream chunkStream = new DataInputStream(new ByteArrayInputStream(regionByteArray, entry.offset() + 5, chunkSize));
                InputStream decompressorStream = compressionScheme == 1 ? new GZIPInputStream(chunkStream) : new InflaterInputStream(chunkStream);
                NBTInputStream nbtStream = new NBTInputStream(decompressorStream, NBTInputStream.NO_COMPRESSION, ByteOrder.BIG_ENDIAN);
                CompoundMap globalMap = ((CompoundTag) nbtStream.readTag()).getValue();

                if (!globalMap.containsKey("Level"))
                    throw new RuntimeException("Missing Level tag?");

                CompoundTag levelCompound = (CompoundTag) globalMap.get("Level");
                return readChunk(levelCompound, worldVersion);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());

        if (debug)
            System.out.printf("%d chunks loaded.%n", loadedChunks.size());

        return loadedChunks;
    }

    @SuppressWarnings("unchecked")
    private static SlimeChunk readChunk(CompoundTag compound, byte worldVersion) {
        int chunkX = compound.getAsIntTag("xPos").orElseThrow().getValue();
        int chunkZ = compound.getAsIntTag("zPos").orElseThrow().getValue();
        Optional<String> status = compound.getStringValue("Status");

        if (status.isPresent() && !status.get().equals("postprocessed") && !status.get().startsWith("full"))
            // It's a protochunk
            return null;

        int[] biomes;
        Tag<?> biomesTag = compound.getValue().get("Biomes");
        if (biomesTag instanceof IntArrayTag cast) {
            biomes = cast.getValue();
        } else if (biomesTag instanceof ByteArrayTag cast) {
            byte[] byteBiomes = cast.getValue();
            biomes = toIntArray(byteBiomes);
        } else {
            biomes = null;
        }

        Optional<CompoundTag> optionalHeightMaps = compound.getAsCompoundTag("Heightmaps");
        CompoundTag heightMapsCompound;

        if (worldVersion >= 0x04) {
            heightMapsCompound = optionalHeightMaps.orElse(new CompoundTag("", new CompoundMap()));
        } else {
            // Pre 1.13 world
            int[] heightMap = compound.getIntArrayValue("HeightMap").orElse(new int[256]);
            heightMapsCompound = new CompoundTag("", new CompoundMap());
            heightMapsCompound.getValue().put("heightMap", new IntArrayTag("heightMap", heightMap));
        }

        List<CompoundTag> tileEntities = ((ListTag<CompoundTag>) compound.getAsListTag("TileEntities").orElse(new ListTag<>("TileEntities", TagType.TAG_COMPOUND, new ArrayList<>()))).getValue();
        List<CompoundTag> entities = ((ListTag<CompoundTag>) compound.getAsListTag("Entities").orElse(new ListTag<>("Entities", TagType.TAG_COMPOUND, new ArrayList<>()))).getValue();
        ListTag<CompoundTag> sectionsTag = (ListTag<CompoundTag>) compound.getAsListTag("Sections").orElseThrow();
        SlimeChunkSection[] sectionArray = new SlimeChunkSection[16];

        for (CompoundTag sectionTag : sectionsTag.getValue()) {
            int index = sectionTag.getByteValue("Y").orElseThrow();
            if (index < 0)
                // For some reason MC 1.14 worlds contain an empty section with Y = -1.
                continue;

            byte[] blocks = sectionTag.getByteArrayValue("Blocks").orElse(null);
            NibbleArray dataArray;
            ListTag<CompoundTag> paletteTag;
            long[] blockStatesArray;

            if (worldVersion < 0x04) {
                dataArray = new NibbleArray(sectionTag.getByteArrayValue("Data").orElseThrow());
                if (isEmpty(blocks)) // Just skip it
                    continue;

                paletteTag = null;
                blockStatesArray = null;
            } else {
                dataArray = null;

                paletteTag = (ListTag<CompoundTag>) sectionTag.getAsListTag("Palette").orElse(null);
                blockStatesArray = sectionTag.getLongArrayValue("BlockStates").orElse(null);

                if (paletteTag == null || blockStatesArray == null || isEmpty(blockStatesArray)) { // Skip it
                    continue;
                }
            }

            NibbleArray blockLightArray = sectionTag.getValue().containsKey("BlockLight") ? new NibbleArray(sectionTag.getByteArrayValue("BlockLight").orElseThrow()) : null;
            NibbleArray skyLightArray = sectionTag.getValue().containsKey("SkyLight") ? new NibbleArray(sectionTag.getByteArrayValue("SkyLight").orElseThrow()) : null;
            sectionArray[index] = new CraftSlimeChunkSection(blocks, dataArray, paletteTag, blockStatesArray, blockLightArray, skyLightArray);
        }

        for (SlimeChunkSection section : sectionArray)
            if (section != null) // Chunk isn't empty
                return new CraftSlimeChunk(null, chunkX, chunkZ, sectionArray, heightMapsCompound, biomes, tileEntities, entities);

        // Chunk is empty
        return null;
    }

    private static int[] toIntArray(byte[] buf) {
        ByteBuffer buffer = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN);
        int[] ret = new int[buf.length / 4];
        buffer.asIntBuffer().get(ret);
        return ret;
    }

    private static boolean isEmpty(byte[] array) {
        for (byte b : array)
            if (b != 0)
                return false;

        return true;
    }

    private static boolean isEmpty(long[] array) {
        for (long b : array)
            if (b != 0L)
                return false;

        return true;
    }

    private static byte[] generateSlimeWorld(List<SlimeChunk> chunks, byte worldVersion, LevelData levelData, List<CompoundTag> worldMaps) {
        List<SlimeChunk> sortedChunks = new ArrayList<>(chunks);
        sortedChunks.sort(Comparator.comparingLong(chunk -> (long) chunk.getZ() * Integer.MAX_VALUE + (long) chunk.getX()));

        ByteArrayOutputStream outByteStream = new ByteArrayOutputStream();
        DataOutputStream outStream = new DataOutputStream(outByteStream);

        try {
            // File Header and Slime version
            outStream.write(SlimeFormat.SLIME_HEADER);
            outStream.write(SlimeFormat.SLIME_VERSION);

            // World version
            outStream.writeByte(worldVersion);

            // Lowest chunk coordinates
            int minX = sortedChunks.stream().mapToInt(SlimeChunk::getX).min().orElseThrow();
            int minZ = sortedChunks.stream().mapToInt(SlimeChunk::getZ).min().orElseThrow();
            int maxX = sortedChunks.stream().mapToInt(SlimeChunk::getX).max().orElseThrow();
            int maxZ = sortedChunks.stream().mapToInt(SlimeChunk::getZ).max().orElseThrow();

            outStream.writeShort(minX);
            outStream.writeShort(minZ);

            // Width and depth
            int width = maxX - minX + 1;
            int depth = maxZ - minZ + 1;

            outStream.writeShort(width);
            outStream.writeShort(depth);

            // Chunk Bitmask
            BitSet chunkBitset = new BitSet(width * depth);
            for (SlimeChunk chunk : sortedChunks) {
                int bitsetIndex = (chunk.getZ() - minZ) * width + (chunk.getX() - minX);
                chunkBitset.set(bitsetIndex, true);
            }

            int chunkMaskSize = (int) Math.ceil((width * depth) / 8.0D);
            writeBitSetAsBytes(outStream, chunkBitset, chunkMaskSize);

            // Chunks
            byte[] chunkData = serializeChunks(sortedChunks, worldVersion);
            byte[] compressedChunkData = Zstd.compress(chunkData);

            outStream.writeInt(compressedChunkData.length);
            outStream.writeInt(chunkData.length);
            outStream.write(compressedChunkData);

            // Tile Entities
            List<CompoundTag> tileEntitiesList = sortedChunks.stream().flatMap(chunk -> chunk.getTileEntities().stream()).collect(Collectors.toList());
            ListTag<CompoundTag> tileEntitiesNbtList = new ListTag<>("tiles", TagType.TAG_COMPOUND, tileEntitiesList);
            CompoundTag tileEntitiesCompound = new CompoundTag("", new CompoundMap(Collections.singletonList(tileEntitiesNbtList)));
            byte[] tileEntitiesData = serializeCompoundTag(tileEntitiesCompound);
            byte[] compressedTileEntitiesData = Zstd.compress(tileEntitiesData);

            outStream.writeInt(compressedTileEntitiesData.length);
            outStream.writeInt(tileEntitiesData.length);
            outStream.write(compressedTileEntitiesData);

            // Entities
            List<CompoundTag> entitiesList = sortedChunks.stream().flatMap(chunk -> chunk.getEntities().stream()).collect(Collectors.toList());
            outStream.writeBoolean(!entitiesList.isEmpty());

            if (!entitiesList.isEmpty()) {
                ListTag<CompoundTag> entitiesNbtList = new ListTag<>("entities", TagType.TAG_COMPOUND, entitiesList);
                CompoundTag entitiesCompound = new CompoundTag("", new CompoundMap(Collections.singletonList(entitiesNbtList)));
                byte[] entitiesData = serializeCompoundTag(entitiesCompound);
                byte[] compressedEntitiesData = Zstd.compress(entitiesData);

                outStream.writeInt(compressedEntitiesData.length);
                outStream.writeInt(entitiesData.length);
                outStream.write(compressedEntitiesData);
            }

            // Extra Tag
            CompoundMap extraMap = new CompoundMap();
            if (!levelData.gameRules().isEmpty()) {
                CompoundMap gamerules = new CompoundMap();
                levelData.gameRules().forEach((rule, value) -> gamerules.put(rule, new StringTag(rule, value)));
                extraMap.put("gamerules", new CompoundTag("gamerules", gamerules));
            }

            byte[] extraData = serializeCompoundTag(new CompoundTag("", extraMap));
            byte[] compressedExtraData = Zstd.compress(extraData);

            outStream.writeInt(compressedExtraData.length);
            outStream.writeInt(extraData.length);
            outStream.write(compressedExtraData);

            // World Maps
            CompoundMap map = new CompoundMap();
            map.put("maps", new ListTag<>("maps", TagType.TAG_COMPOUND, worldMaps));

            CompoundTag mapsCompound = new CompoundTag("", map);
            byte[] mapArray = serializeCompoundTag(mapsCompound);
            byte[] compressedMapArray = Zstd.compress(mapArray);

            outStream.writeInt(compressedMapArray.length);
            outStream.writeInt(mapArray.length);
            outStream.write(compressedMapArray);
        } catch (IOException ex) { // Ignore
            //noinspection CallToPrintStackTrace
            ex.printStackTrace();
        }

        return outByteStream.toByteArray();
    }

    private static void writeBitSetAsBytes(DataOutputStream outStream, BitSet set, int fixedSize) throws IOException {
        byte[] array = set.toByteArray();
        outStream.write(array);

        int chunkMaskPadding = fixedSize - array.length;

        for (int i = 0; i < chunkMaskPadding; i++) {
            outStream.write(0);
        }
    }

    private static byte[] serializeChunks(List<SlimeChunk> chunks, byte worldVersion) throws IOException {
        ByteArrayOutputStream outByteStream = new ByteArrayOutputStream(16384);
        DataOutputStream outStream = new DataOutputStream(outByteStream);

        for (SlimeChunk chunk : chunks) {
            // Height Maps
            if (worldVersion >= 0x04) {
                byte[] heightMaps = serializeCompoundTag(chunk.getHeightMaps());
                outStream.writeInt(heightMaps.length);
                outStream.write(heightMaps);
            } else {
                int[] heightMap = chunk.getHeightMaps().getIntArrayValue("heightMap").orElseThrow();
                for (int i = 0; i < 256; i++) {
                    outStream.writeInt(heightMap[i]);
                }
            }

            // Biomes
            int[] biomes = chunk.getBiomes();
            if (worldVersion >= 0x04)
                outStream.writeInt(biomes.length);

            for (int biome : biomes)
                outStream.writeInt(biome);

            // Chunk sections
            SlimeChunkSection[] sections = chunk.getSections();
            BitSet sectionBitmask = new BitSet(16);

            for (int i = 0; i < sections.length; i++)
                sectionBitmask.set(i, sections[i] != null);

            writeBitSetAsBytes(outStream, sectionBitmask, 2);

            for (SlimeChunkSection section : sections) {
                if (section == null)
                    continue;

                // Block Light
                boolean hasBlockLight = section.blockLight() != null;
                outStream.writeBoolean(hasBlockLight);

                if (hasBlockLight)
                    outStream.write(section.blockLight().getBacking());

                // Block Data
                if (worldVersion >= 0x04) {
                    // Palette
                    List<CompoundTag> palette = section.palette().getValue();
                    outStream.writeInt(palette.size());

                    for (CompoundTag value : palette) {
                        byte[] serializedValue = serializeCompoundTag(value);

                        outStream.writeInt(serializedValue.length);
                        outStream.write(serializedValue);
                    }

                    // Block states
                    long[] blockStates = section.blockStates();
                    outStream.writeInt(blockStates.length);

                    for (long value : section.blockStates()) {
                        outStream.writeLong(value);
                    }
                } else {
                    outStream.write(section.blocks());
                    outStream.write(section.data().getBacking());
                }

                // Sky Light
                boolean hasSkyLight = section.skyLight() != null;
                outStream.writeBoolean(hasSkyLight);

                if (hasSkyLight) {
                    outStream.write(section.skyLight().getBacking());
                }
            }
        }

        return outByteStream.toByteArray();
    }

    private static byte[] serializeCompoundTag(CompoundTag tag) throws IOException {
        ByteArrayOutputStream outByteStream = new ByteArrayOutputStream();
        NBTOutputStream outStream = new NBTOutputStream(outByteStream, NBTInputStream.NO_COMPRESSION, ByteOrder.BIG_ENDIAN);
        outStream.writeTag(tag);
        return outByteStream.toByteArray();
    }

}
