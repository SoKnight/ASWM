package com.grinderwolf.swm.api.exception;

/**
 * Exception thrown when a world
 * already exists inside a data source.
 */
public class WorldAlreadyExistsException extends SlimeException {

    public WorldAlreadyExistsException(String world) {
        super("World '%s' already exists!".formatted(world));
    }

}
