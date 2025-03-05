package com.grinderwolf.swm.api.event;

import com.grinderwolf.swm.api.loader.SlimeLoader;
import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
public class AsyncPostMigrateWorldEvent extends Event {

    @Getter
    private static final HandlerList handlerList = new HandlerList();

    private final String worldName;
    private final SlimeLoader currentLoader;
    private final SlimeLoader newLoader;

    public AsyncPostMigrateWorldEvent(String worldName, SlimeLoader currentLoader, SlimeLoader newLoader) {
        super(true);
        this.worldName = Objects.requireNonNull(worldName, "worldName cannot be null");
        this.currentLoader = Objects.requireNonNull(currentLoader, "currentLoader cannot be null");
        this.newLoader = Objects.requireNonNull(newLoader, "newLoader cannot be null");
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlerList;
    }

}