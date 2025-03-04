package com.grinderwolf.swm.plugin.world.upgrader.spec;

import com.flowpowered.nbt.ByteTag;
import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.IntTag;
import com.flowpowered.nbt.StringTag;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import com.grinderwolf.swm.nms.CraftSlimeWorld;
import com.grinderwolf.swm.plugin.world.upgrader.WorldUpgrader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VillageAndPillageWorldUpgrader implements WorldUpgrader {

    public static final WorldUpgrader INSTANCE = new VillageAndPillageWorldUpgrader();

    private static final int[] VILLAGER_XP = { 0, 10, 50, 100, 150 };

    private static final Map<String, String> oldToNewMap = new HashMap<>();

    static {
        rename("minecraft:tube_coral_fan", "minecraft:tube_coral_wall_fan");
        rename("minecraft:brain_coral_fan", "minecraft:brain_coral_wall_fan");
        rename("minecraft:bubble_coral_fan", "minecraft:bubble_coral_wall_fan");
        rename("minecraft:fire_coral_fan", "minecraft:fire_coral_wall_fan");
        rename("minecraft:horn_coral_fan", "minecraft:horn_coral_wall_fan");
        rename("minecraft:stone_slab", "minecraft:smooth_stone_slab");
        rename("minecraft:sign", "minecraft:oak_sign");
        rename("minecraft:wall_sign", "minecraft:oak_wall_sign");
    }

    private static void rename(String oldName, String newName) {
        oldToNewMap.put(oldName, newName);
    }

    @Override
    public void upgrade(CraftSlimeWorld world) {
        for (SlimeChunk chunk : new ArrayList<>(world.getChunks().values())) {
            // Update renamed blocks
            for (int sectionIndex = 0; sectionIndex < chunk.getSections().length; sectionIndex++) {
                SlimeChunkSection section = chunk.getSections()[sectionIndex];
                if (section != null) {
                    List<CompoundTag> palette = section.palette().getValue();
                    for (int paletteIndex = 0; paletteIndex < palette.size(); paletteIndex++) {
                        CompoundTag blockTag = palette.get(paletteIndex);
                        String name = blockTag.getStringValue("Name").orElseThrow();

                        // Trapped chests have now a different tile entity,
                        // so we have to update every block entity type
                        if (name.equals("minecraft:trapped_chest")) {
                            updateBlockEntities(chunk, sectionIndex, paletteIndex, "minecraft:chest", "minecraft:trapped_chest");
                        }

                        String newName = oldToNewMap.get(name);
                        if (newName != null) {
                            blockTag.getValue().put("Name", new StringTag("Name", newName));
                        }
                    }
                }
            }

            if (chunk.getEntities() != null) {
                for (CompoundTag entityTag : chunk.getEntities()) {
                    switch (entityTag.getStringValue("id").orElseThrow()) {
                        case "minecraft:ocelot":
                            // Cats are no longer ocelots
                            int catType = entityTag.getIntValue("CatType").orElse(0);
                            if (catType == 0) {
                                Optional<String> owner = entityTag.getStringValue("Owner");
                                Optional<String> ownerId = entityTag.getStringValue("OwnerUUID");
                                if (owner.isPresent() || ownerId.isPresent())
                                    entityTag.getValue().put("Trusting", new ByteTag("Trusting", (byte) 1));

                                entityTag.getValue().remove("CatType");
                            } else if (catType > 0 && catType < 4) {
                                entityTag.getValue().put("id", new StringTag("id", "minecraft:cat"));
                            }
                            break;
                        case "minecraft:villager":
                        case "minecraft:zombie_villager":
                            // Villager data has changed
                            int profession = entityTag.getIntValue("Profession").orElse(0);
                            int career = entityTag.getIntValue("Career").orElse(0);
                            int careerLevel = entityTag.getIntValue("CareerLevel").orElse(1);

                            // Villager level and xp has to be rebuilt
                            Optional<CompoundTag> offersOpt = entityTag.getAsCompoundTag("Offers");
                            if (offersOpt.isPresent()) {
                                if (careerLevel == 0 || careerLevel == 1) {
                                    int amount = offersOpt.flatMap((offers) -> offers.getAsCompoundTag("Recipes")).map((recipes) -> recipes.getValue().size()).orElse(0);
                                    careerLevel = clamp(amount / 2, 1, 5);
                                }
                            }

                            Optional<CompoundTag> xp = entityTag.getAsCompoundTag("Xp");
                            if (xp.isEmpty())
                                entityTag.getValue().put("Xp", new IntTag("Xp", VILLAGER_XP[clamp(careerLevel - 1, 0, VILLAGER_XP.length - 1)]));

                            entityTag.getValue().remove("Profession");
                            entityTag.getValue().remove("Career");
                            entityTag.getValue().remove("CareerLevel");

                            CompoundMap dataMap = new CompoundMap();
                            dataMap.put("type", new StringTag("type", "minecraft:plains"));
                            dataMap.put("profession", new StringTag("profession", getVillagerProfession(profession, career)));
                            dataMap.put("level", new IntTag("level", careerLevel));

                            entityTag.getValue().put("VillagerData", new CompoundTag("VillagerData", dataMap));
                            break;
                        case "minecraft:banner":
                            // The illager banners changed the translation message
                            entityTag.getStringValue("CustomName").ifPresent(name -> {
                                String newName = name.replace(
                                        "\"translate\":\"block.minecraft.illager_banner\"",
                                        "\"translate\":\"block.minecraft.ominous_banner\""
                                );

                                entityTag.getValue().put("CustomName", new StringTag("CustomName", newName));
                            });
                            break;
                    }
                }
            }
        }
    }

    private int clamp(int i, int i1, int i2) {
        return i < i1 ? i1 : (Math.min(i, i2));
    }

    private String getVillagerProfession(int profession, int career) {
        return switch (profession) {
            case 0 -> switch (career) {
                case 2 -> "minecraft:fisherman";
                case 3 -> "minecraft:shepherd";
                case 4 -> "minecraft:fletcher";
                default -> "minecraft:farmer";
            };
            case 1 -> (career == 2 ? "minecraft:cartographer" : "minecraft:librarian");
            case 2 -> "minecraft:cleric";
            case 3 -> switch (career) {
                case 2 -> "minecraft:weaponsmith";
                case 3 -> "minecraft:toolsmith";
                default -> "minecraft:armorer";
            };
            case 4 -> (career == 2 ? "minecraft:leatherworker" : "minecraft:butcher");
            case 5 -> "minecraft:nitwit";
            default -> "minecraft:none";
        };
    }

    private void updateBlockEntities(
            SlimeChunk chunk,
            int sectionIndex,
            int paletteIndex,
            String oldName,
            String newName
    ) {
        if (chunk.getTileEntities() != null) {
            SlimeChunkSection section = chunk.getSections()[sectionIndex];
            long[] blockData = section.blockStates();

            int bitsPerBlock = Math.max(4, blockData.length * 64 / 4096);
            long maxEntryValue = (1L << bitsPerBlock) - 1;

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int arrayIndex = y << 8 | z << 4 | x;
                        int bitIndex = arrayIndex * bitsPerBlock;
                        int startIndex = bitIndex / 64;
                        int endIndex = ((arrayIndex + 1) * bitsPerBlock - 1) / 64;
                        int startBitSubIndex = bitIndex % 64;

                        int val;
                        if (startIndex == endIndex) {
                            val = (int) (blockData[startIndex] >>> startBitSubIndex & maxEntryValue);
                        } else {
                            int endBitSubIndex = 64 - startBitSubIndex;
                            val = (int) ((blockData[startIndex] >>> startBitSubIndex | blockData[endIndex] << endBitSubIndex) & maxEntryValue);
                        }

                        // It's the right block type
                        if (val == paletteIndex) {
                            int blockX = x + chunk.getX() * 16;
                            int blockY = y + sectionIndex * 16;
                            int blockZ = z + chunk.getZ() * 16;

                            for (CompoundTag tileEntityTag : chunk.getTileEntities()) {
                                int tileX = tileEntityTag.getIntValue("x").orElseThrow();
                                int tileY = tileEntityTag.getIntValue("y").orElseThrow();
                                int tileZ = tileEntityTag.getIntValue("z").orElseThrow();

                                if (tileX == blockX && tileY == blockY && tileZ == blockZ) {
                                    String type = tileEntityTag.getStringValue("id").orElseThrow();
                                    if (!type.equals(oldName))
                                        throw new IllegalStateException("Expected block entity to be " + oldName + ", not " + type);

                                    tileEntityTag.getValue().put("id", new StringTag("id", newName));
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
