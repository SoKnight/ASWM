package com.grinderwolf.swm.api.event;

import com.grinderwolf.swm.api.world.SlimeWorld;
import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
public class PostGenerateWorldEvent extends Event {

    @Getter
    private static final HandlerList handlerList = new HandlerList();

    private final SlimeWorld world;

    public PostGenerateWorldEvent(SlimeWorld world) {
        super(false);
        this.world = Objects.requireNonNull(world, "world cannot be null");
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlerList;
    }

}