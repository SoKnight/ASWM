package com.grinderwolf.swm.api.event;

import com.grinderwolf.swm.api.loader.SlimeLoader;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter @Setter
public class AsyncPreMigrateWorldEvent extends Event implements Cancellable {

    @Getter
    private static final HandlerList handlerList = new HandlerList();

    private boolean cancelled;
    private String worldName;
    private SlimeLoader currentLoader;
    private SlimeLoader newLoader;

    public AsyncPreMigrateWorldEvent(String worldName, SlimeLoader currentLoader, SlimeLoader newLoader) {
        super(true);
        this.worldName = Objects.requireNonNull(worldName, "worldName cannot be null");
        this.currentLoader = Objects.requireNonNull(currentLoader, "currentLoader cannot be null");
        this.newLoader = Objects.requireNonNull(newLoader, "newLoader cannot be null");
    }

    public void setWorldName(String worldName) {
        this.worldName = Objects.requireNonNull(worldName, "worldName cannot be null");
    }

    public void setCurrentLoader(SlimeLoader currentLoader) {
        this.currentLoader = Objects.requireNonNull(currentLoader, "currentLoader cannot be null");
    }

    public void setNewLoader(SlimeLoader newLoader) {
        this.newLoader = Objects.requireNonNull(newLoader, "newLoader cannot be null");
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlerList;
    }

}