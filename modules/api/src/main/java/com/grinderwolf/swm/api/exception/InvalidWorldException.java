package com.grinderwolf.swm.api.exception;

import java.nio.file.Path;

/**
 * Exception thrown when a folder does
 * not contain a valid Minecraft world.
 */
public class InvalidWorldException extends SlimeException {

    public InvalidWorldException(Path worldDir, String reason) {
        super("Directory %s does not contain a valid MC world! %s".formatted(worldDir, reason));
    }

    public InvalidWorldException(Path worldDir) {
        super("Directory %s does not contain a valid MC world!".formatted(worldDir));
    }

}
