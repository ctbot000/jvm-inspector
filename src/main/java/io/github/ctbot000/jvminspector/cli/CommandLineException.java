package io.github.ctbot000.jvminspector.cli;

/** A problem with what the user typed, as opposed to a problem with the target. */
public final class CommandLineException extends RuntimeException {

    public CommandLineException(String message) {
        super(message);
    }
}
