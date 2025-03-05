package com.grinderwolf.swm.api.event;

import com.grinderwolf.swm.api.loader.SlimeLoader;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Objects;

@Getter @Setter
public class AsyncPreImportWorldEvent extends Event implements Cancellable {

    @Getter
    private static final HandlerList handlerList = new HandlerList();

    private boolean cancelled;
    private Path worldDir;
    private String worldName;
    private SlimeLoader slimeLoader;

    public AsyncPreImportWorldEvent(Path worldDir, String worldName, SlimeLoader slimeLoader) {
        super(true);
        this.worldDir = Objects.requireNonNull(worldDir, "worldDir cannot be null");
        this.worldName = Objects.requireNonNull(worldName, "worldName cannot be null");
        this.slimeLoader = Objects.requireNonNull(slimeLoader, "slimeLoader cannot be null");
    }

    public void setWorldDir(Path worldDir) {
        this.worldDir = Objects.requireNonNull(worldDir, "worldDir cannot be null");
    }

    public void setWorldName(String worldName) {
        this.worldName = Objects.requireNonNull(worldName, "worldName cannot be null");
    }

    public void setSlimeLoader(SlimeLoader slimeLoader) {
        this.slimeLoader = Objects.requireNonNull(slimeLoader, "slimeLoader cannot be null");
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlerList;
    }

}