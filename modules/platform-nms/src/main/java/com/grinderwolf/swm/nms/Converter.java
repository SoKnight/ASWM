package com.grinderwolf.swm.nms;

import com.flowpowered.nbt.*;
import com.flowpowered.nbt.Tag;
import com.grinderwolf.swm.api.util.NibbleArray;
import lombok.extern.log4j.Log4j2;
import net.minecraft.server.v1_16_R3.*;

import java.util.List;
import java.util.stream.Collectors;

@Log4j2
public class Converter {

    static net.minecraft.server.v1_16_R3.NibbleArray pureToNms(NibbleArray array) {
        return new net.minecraft.server.v1_16_R3.NibbleArray(array.getBacking());
    }

    static NibbleArray nmsToPure(net.minecraft.server.v1_16_R3.NibbleArray array) {
        return array != null ? new NibbleArray(array.asBytes()) : null;
    }

    static NBTBase convertTag(Tag<?> tag) {
        try {
            return switch (tag.getType()) {
                case TAG_BYTE -> NBTTagByte.a(((ByteTag) tag).getValue());
                case TAG_SHORT -> NBTTagShort.a(((ShortTag) tag).getValue());
                case TAG_INT -> NBTTagInt.a(((IntTag) tag).getValue());
                case TAG_LONG -> NBTTagLong.a(((LongTag) tag).getValue());
                case TAG_FLOAT -> NBTTagFloat.a(((FloatTag) tag).getValue());
                case TAG_DOUBLE -> NBTTagDouble.a(((DoubleTag) tag).getValue());
                case TAG_BYTE_ARRAY -> new NBTTagByteArray(((ByteArrayTag) tag).getValue());
                case TAG_STRING -> NBTTagString.a(((StringTag) tag).getValue());
                case TAG_LIST -> ((ListTag<?>) tag).getValue().stream()
                        .map(Converter::convertTag)
                        .collect(NBTTagList::new, NBTTagList::add, NBTTagList::addAll);
                case TAG_COMPOUND -> {
                    NBTTagCompound compound = new NBTTagCompound();
                    ((CompoundTag) tag).getValue().forEach((key, value) -> compound.set(key, convertTag(value)));
                    yield compound;
                }
                case TAG_INT_ARRAY -> new NBTTagIntArray(((IntArrayTag) tag).getValue());
                case TAG_LONG_ARRAY -> new NBTTagLongArray(((LongArrayTag) tag).getValue());
                default -> throw new IllegalArgumentException("Invalid tag type: " + tag.getType().name());
            };
        } catch (Exception ex) {
            log.error("Failed to convert NBT object:");
            log.error(tag.toString());
            throw ex;
        }
    }

    static Tag<?> convertTag(String name, NBTBase base) {
        return switch (base.getTypeId()) {
            case 1 -> new ByteTag(name, ((NBTTagByte) base).asByte());
            case 2 -> new ShortTag(name, ((NBTTagShort) base).asShort());
            case 3 -> new IntTag(name, ((NBTTagInt) base).asInt());
            case 4 -> new LongTag(name, ((NBTTagLong) base).asLong());
            case 5 -> new FloatTag(name, ((NBTTagFloat) base).asFloat());
            case 6 -> new DoubleTag(name, ((NBTTagDouble) base).asDouble());
            case 7 -> new ByteArrayTag(name, ((NBTTagByteArray) base).getBytes());
            case 8 -> new StringTag(name, base.asString());
            case 9 -> {
                NBTTagList tagList = ((NBTTagList) base);
                List<Tag<?>> list = tagList.stream().map(entry -> convertTag("", entry)).collect(Collectors.toList());
                yield new ListTag<>(name, TagType.getById(tagList.d_()), list);
            }
            case 10 -> {
                NBTTagCompound originalCompound = ((NBTTagCompound) base);
                CompoundTag compound = new CompoundTag(name, new CompoundMap());

                for (String key : originalCompound.getKeys())
                    compound.getValue().put(key, convertTag(key, originalCompound.get(key)));

                yield compound;
            }
            case 11 -> new IntArrayTag(name, ((NBTTagIntArray) base).getInts());
            case 12 -> new LongArrayTag(name, ((NBTTagLongArray) base).getLongs());
            default -> throw new IllegalArgumentException("Invalid tag type: " + base.getTypeId());
        };
    }

}