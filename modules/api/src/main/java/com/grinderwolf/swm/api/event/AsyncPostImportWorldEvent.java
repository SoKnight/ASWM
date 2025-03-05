package com.grinderwolf.swm.api.event;

import com.grinderwolf.swm.api.loader.SlimeLoader;
import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Objects;

@Getter
public class AsyncPostImportWorldEvent extends Event {

    @Getter
    private static final HandlerList handlerList = new HandlerList();

    private final Path worldDir;
    private final String worldName;
    private final SlimeLoader slimeLoader;

    public AsyncPostImportWorldEvent(Path worldDir, String worldName, SlimeLoader slimeLoader) {
        super(true);
        this.worldDir = Objects.requireNonNull(worldDir, "worldDir cannot be null");
        this.worldName = Objects.requireNonNull(worldName, "worldName cannot be null");
        this.slimeLoader = Objects.requireNonNull(slimeLoader, "slimeLoader cannot be null");
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlerList;
    }

}