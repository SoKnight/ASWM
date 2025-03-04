package com.grinderwolf.swm.api.exception;

/**
 * Exception thrown when a world is loaded
 * when trying to import it.
 */
public class WorldLoadedException extends SlimeException {

    public WorldLoadedException(String worldName) {
        super("World '%s' is loaded! Unload it before importing it.".formatted(worldName));
    }

}
