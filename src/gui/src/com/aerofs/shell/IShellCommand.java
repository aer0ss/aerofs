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

    void execute(ShellCommandRunner<T> s, CommandLine cl) throws Exception;
}
