package com.grinderwolf.swm.api.exception;

/**
 * Exception thrown when a MC world is
 * too big to be converted into the SRF.
 */
public class WorldTooBigException extends SlimeException {

    public WorldTooBigException(String worldName) {
        super("World '%s' is too big to be converted into the SRF!".formatted(worldName));
    }

}
