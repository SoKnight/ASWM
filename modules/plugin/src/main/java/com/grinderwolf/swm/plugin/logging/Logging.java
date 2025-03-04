package com.grinderwolf.swm.plugin.logging;

import com.grinderwolf.swm.plugin.SWMPlugin;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Logging {

    public static void info(String message, Object... args) {
        SWMPlugin.logger().info(String.format(message, args));
    }

    public static void warn(String message, Object... args) {
        SWMPlugin.logger().warning(String.format(message, args));
    }

    public static void error(String message, Object... args) {
        SWMPlugin.logger().severe(String.format(message, args));
    }

    public static void info(String message, Throwable throwable) {
        SWMPlugin.logger().info(message);
        printStackTrace(SWMPlugin.logger(), throwable, Logger::info);
    }

    public static void warn(String message, Throwable throwable) {
        SWMPlugin.logger().warning(message);
        printStackTrace(SWMPlugin.logger(), throwable, Logger::warning);
    }

    public static void error(String message, Throwable throwable) {
        SWMPlugin.logger().severe(message);
        printStackTrace(SWMPlugin.logger(), throwable, Logger::severe);
    }

    public static void info(Throwable throwable) {
        info(throwable.getMessage(), throwable);
    }

    public static void warn(Throwable throwable) {
        warn(throwable.getMessage(), throwable);
    }

    public static void error(Throwable throwable) {
        error(throwable.getMessage(), throwable);
    }

    private static void printStackTrace(Logger logger, Throwable throwable, BiConsumer<Logger, String> printFunction) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        String asString = stringWriter.toString();
        Arrays.stream(asString.split("\n")).forEach(line -> printFunction.accept(logger, line));
    }

}
