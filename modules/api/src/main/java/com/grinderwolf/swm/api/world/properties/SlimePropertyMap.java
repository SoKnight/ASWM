package com.grinderwolf.swm.api.world.properties;

import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.CompoundTag;
import lombok.RequiredArgsConstructor;

/**
 * A Property Map object.
 */
@RequiredArgsConstructor
public class SlimePropertyMap {

    private final CompoundMap properties;

    public SlimePropertyMap() {
        this(new CompoundMap());
    }

    /**
     * Return the current value of the given property
     *
     * @param property The slime property
     * @return The current value
     */
    public <T> T getValue(SlimeProperty<T> property) {
        if (properties.containsKey(property.getNbtName())) {
            return property.readValue(properties.get(property.getNbtName()));
        } else {
            return property.getDefaultValue();
        }
    }

    /**
     * Update the value of the given property
     *
     * @param property The slime property
     * @param value The new value
     * @throws IllegalArgumentException if the value fails validation.
     */
    public <T> void setValue(SlimeProperty<T> property, T value) {
        if (property.getValidator() != null && !property.getValidator().apply(value))
            throw new IllegalArgumentException("'%s' is not a valid property value.".formatted(value));

        property.writeValue(properties, value);
    }

    /**
     * Copies all values from the specified {@link SlimePropertyMap}.
     * If the same property has different values on both maps, the one
     * on the providen map will be used.
     *
     * @param propertyMap A {@link SlimePropertyMap}.
     */
    public void merge(SlimePropertyMap propertyMap) {
        properties.putAll(propertyMap.properties);
    }

    /**
     * Returns a {@link CompoundTag} containing every property set in this map.
     *
     * @return A {@link CompoundTag} with all the properties stored in this map.
     */
    public CompoundTag toCompound() {
        return new CompoundTag("properties", properties);
    }

    public static SlimePropertyMap fromCompound(CompoundTag compound) {
        return new SlimePropertyMap(compound.getValue());
    }

    @Override
    public String toString() {
        return "SlimePropertyMap" + properties;
    }

}
