package com.grinderwolf.swm.api.event;

import com.grinderwolf.swm.api.world.SlimeWorld;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter @Setter
public class PreGenerateWorldEvent extends Event implements Cancellable {

    @Getter
    private static final HandlerList handlerList = new HandlerList();

    private boolean cancelled;
    private SlimeWorld world;

    public PreGenerateWorldEvent(SlimeWorld world) {
        super(false);
        this.world = Objects.requireNonNull(world, "world cannot be null");
    }

    public void setWorld(SlimeWorld world) {
        this.world = Objects.requireNonNull(world, "world cannot be null");
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlerList;
    }

}