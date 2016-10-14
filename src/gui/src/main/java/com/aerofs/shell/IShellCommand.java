package com.aerofs.shell;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public interface IShellCommand<T>
{
    String getName();

    /**
     * @return a short phrase with lower-case initial
     */
    String getDescription();

    /**
     * @return e.g. "[OPTIONS] [FILE]...". must not return null
     */
    String getOptsSyntax();

    /**
     * @return ShellCommandRunner.EMPTY_OPTS if there is no option
     */
    Options getOpts();

    /**
     * N.B. we can also add opts header, but it's not required right now.
     *
     * @return a footer text to be printed before the options
     */
    String getFooter();

    /**
     * @return whether the command is hidden from listing of available commands via 'help'.
     * Useful for internal commands that shouldn't be exposed to the end user.
     */
    boolean isHidden();

    void execute(ShellCommandRunner<T> s, CommandLine cl) throws Exception;
}
