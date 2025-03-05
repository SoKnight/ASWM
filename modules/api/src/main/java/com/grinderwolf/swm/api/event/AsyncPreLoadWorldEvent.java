package com.grinderwolf.swm.api.event;

import com.grinderwolf.swm.api.loader.SlimeLoader;
import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter @Setter
public class AsyncPreLoadWorldEvent extends Event implements Cancellable {

    @Getter
    private static final HandlerList handlerList = new HandlerList();

    private boolean cancelled;
    private SlimeLoader slimeLoader;
    private String worldName;
    private boolean readOnly;
    private SlimePropertyMap slimePropertyMap;

    public AsyncPreLoadWorldEvent(SlimeLoader slimeLoader, String worldName, boolean readOnly, SlimePropertyMap slimePropertyMap) {
        super(true);
        this.slimeLoader = Objects.requireNonNull(slimeLoader, "slimeLoader cannot be null");
        this.worldName = Objects.requireNonNull(worldName, "worldName cannot be null");
        this.readOnly = readOnly;
        this.slimePropertyMap = Objects.requireNonNull(slimePropertyMap, "slimePropertyMap cannot be null");
    }

    public void setSlimeLoader(SlimeLoader slimeLoader) {
        this.slimeLoader = Objects.requireNonNull(slimeLoader, "slimeLoader cannot be null");
    }

    public void setWorldName(String worldName) {
        this.worldName = Objects.requireNonNull(worldName, "worldName cannot be null");
    }

    public void setSlimePropertyMap(SlimePropertyMap slimePropertyMap) {
        this.slimePropertyMap = Objects.requireNonNull(slimePropertyMap, "slimePropertyMap cannot be null");
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlerList;
    }

}