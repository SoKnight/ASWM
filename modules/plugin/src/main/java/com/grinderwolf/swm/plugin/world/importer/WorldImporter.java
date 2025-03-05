package com.grinderwolf.swm.plugin.world.importer;

import com.flowpowered.nbt.*;
import com.flowpowered.nbt.stream.NBTInputStream;
import com.grinderwolf.swm.api.exception.InvalidWorldException;
import com.grinderwolf.swm.api.util.NibbleArray;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import com.grinderwolf.swm.api.world.properties.SlimeProperties;
import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;
import com.grinderwolf.swm.nms.CraftSlimeChunk;
import com.grinderwolf.swm.nms.CraftSlimeChunkSection;
import com.grinderwolf.swm.nms.CraftSlimeWorld;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public class WorldImporter {

    private static final Pattern MAP_FILE_PATTERN = Pattern.compile("^map_([0-9]*).dat$");
    private static final int SECTOR_SIZE = 4096;

    public static CraftSlimeWorld readFromDirectory(Path worldDir) throws InvalidWorldException, IOException {
        Path levelFile = worldDir.resolve("level.dat");
        if (!Files.isRegularFile(levelFile))
            throw new InvalidWorldException(worldDir);

        LevelData data = readLevelData(levelFile);

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
        } else if (data.version() < 2724) {
            worldVersion = 0x06; // 1.16 world
        } else {
            worldVersion = 0x07;
        }

        // Chunks
        Path regionDir = worldDir.resolve("region");
        if (!Files.isDirectory(regionDir))
            throw new InvalidWorldException(worldDir);

        Map<Long, SlimeChunk> chunks = new HashMap<>();
        try (Stream<Path> files = Files.list(regionDir)) {
            var iterator = files.filter(path -> path.getFileName().toString().endsWith(".mca")).iterator();
            while (iterator.hasNext()) {
                loadChunks(iterator.next(), worldVersion).forEach(chunk -> chunks.put(
                        ((long) chunk.getZ()) * Integer.MAX_VALUE + ((long) chunk.getX()),
                        chunk
                ));
            }
        }

        if (chunks.isEmpty())
            throw new InvalidWorldException(worldDir);

        // World maps
        Path dataDir = worldDir.resolve("data");
        List<CompoundTag> maps = new ArrayList<>();

        if (Files.exists(dataDir)) {
            if (!Files.isDirectory(dataDir))
                throw new InvalidWorldException(worldDir);

            try (Stream<Path> files = Files.list(dataDir)) {
                var iterator = files.filter(path -> MAP_FILE_PATTERN.matcher(path.getFileName().toString()).matches()).iterator();
                while (iterator.hasNext()) {
                    maps.add(loadMap(iterator.next()));
                }
            }
        }

        // Extra Data
        CompoundMap extraData = new CompoundMap();
        if (!data.gameRules().isEmpty()) {
            CompoundMap gamerules = new CompoundMap();
            data.gameRules().forEach((rule, value) -> gamerules.put(rule, new StringTag(rule, value)));
            extraData.put("gamerules", new CompoundTag("gamerules", gamerules));
        }

        SlimePropertyMap propertyMap = new SlimePropertyMap();
        propertyMap.setValue(SlimeProperties.SPAWN_X, data.spawnX());
        propertyMap.setValue(SlimeProperties.SPAWN_Y, data.spawnY());
        propertyMap.setValue(SlimeProperties.SPAWN_Z, data.spawnZ());

        return new CraftSlimeWorld(
                null,
                worldDir.getFileName().toString(),
                chunks,
                new CompoundTag("", extraData),
                maps,
                worldVersion,
                propertyMap,
                false,
                true
        );
    }

    private static CompoundTag loadMap(Path mapFile) throws IOException {
        String fileName = mapFile.getFileName().toString();
        int mapId = Integer.parseInt(fileName.substring(4, fileName.length() - 4));

        try (var nbtStream = new NBTInputStream(Files.newInputStream(mapFile), NBTInputStream.GZIP_COMPRESSION, ByteOrder.BIG_ENDIAN)) {
            CompoundTag tag = nbtStream.readTag().getAsCompoundTag().flatMap(t -> t.getAsCompoundTag("data")).orElseThrow();
            tag.getValue().put("id", new IntTag("id", mapId));
            return tag;
        }
    }

    private static LevelData readLevelData(Path levelDatFile) throws IOException, InvalidWorldException {
        try (var nbtStream = new NBTInputStream(Files.newInputStream(levelDatFile))) {
            CompoundTag dataTag = nbtStream.readTag().getAsCompoundTag()
                    .flatMap(t -> t.getAsCompoundTag("Data"))
                    .orElseThrow(() -> new InvalidWorldException(levelDatFile.getParent()));

            // Data version
            int dataVersion = dataTag.getIntValue("DataVersion").orElse(-1);

            // Game rules
            Map<String, String> gameRules = new HashMap<>();
            Optional<CompoundTag> rulesList = dataTag.getAsCompoundTag("GameRules");
            rulesList.ifPresent(compoundTag -> compoundTag.getValue().forEach((ruleName, ruleTag) -> gameRules.put(
                    ruleName,
                    ruleTag.getAsStringTag().orElseThrow().getValue()
            )));

            int spawnX = dataTag.getIntValue("SpawnX").orElse(0);
            int spawnY = dataTag.getIntValue("SpawnY").orElse(255);
            int spawnZ = dataTag.getIntValue("SpawnZ").orElse(0);

            return new LevelData(dataVersion, gameRules, spawnX, spawnY, spawnZ);
        }
    }

    private static Stream<SlimeChunk> loadChunks(Path regionFile, byte worldVersion) throws IOException {
        byte[] regionByteArray = Files.readAllBytes(regionFile);
        try (DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(regionByteArray))) {
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

            return chunks.stream().map((entry) -> {
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
            }).filter(Objects::nonNull);
        }
    }

    @SuppressWarnings("unchecked")
    private static SlimeChunk readChunk(CompoundTag compound, byte worldVersion) {
        Optional<String> status = compound.getStringValue("Status");
        if (status.isPresent() && !status.get().equals("postprocessed") && !status.get().startsWith("full"))
            // It's a protochunk
            return null;

        int chunkX = compound.getAsIntTag("xPos").orElseThrow().getValue();
        int chunkZ = compound.getAsIntTag("zPos").orElseThrow().getValue();

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

            NibbleArray blockLightArray = sectionTag.getByteArrayValue("BlockLight").map(NibbleArray::new).orElse(null);
            NibbleArray skyLightArray = sectionTag.getByteArrayValue("SkyLight").map(NibbleArray::new).orElse(null);
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

    private record ChunkEntry(int offset, int paddedSize) { }

}
