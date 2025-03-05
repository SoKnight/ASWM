package com.grinderwolf.swm.api.event;

import com.grinderwolf.swm.api.world.SlimeWorld;
import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
public class AsyncPostLoadWorldEvent extends Event {

    @Getter
    private static final HandlerList handlerList = new HandlerList();

    private final SlimeWorld world;

    public AsyncPostLoadWorldEvent(SlimeWorld world) {
        super(true);
        this.world = Objects.requireNonNull(world, "world cannot be null");
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlerList;
    }

}