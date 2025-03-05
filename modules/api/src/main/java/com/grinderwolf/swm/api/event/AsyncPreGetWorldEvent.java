package com.grinderwolf.swm.api.event;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter @Setter
public class AsyncPreGetWorldEvent extends Event implements Cancellable {

    @Getter
    private static final HandlerList handlerList = new HandlerList();

    private boolean cancelled;
    private String worldName;

    public AsyncPreGetWorldEvent(String worldName) {
        super(true);
        this.worldName = Objects.requireNonNull(worldName, "worldName cannot be null");
    }

    public void setWorldName(String worldName) {
        this.worldName = Objects.requireNonNull(worldName, "worldName cannot be null");
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlerList;
    }

}