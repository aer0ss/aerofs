/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.shell;

import com.aerofs.proto.Ritual.GetTransportDiagnosticsReply;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import static com.aerofs.lib.JsonFormat.prettyPrint;

public class CmdTransportDiagnostics implements IShellCommand<ShProgram>
{
    @Override
    public String getName()
    {
        return "netinfo";
    }

    @Override
    public String getDescription()
    {
        return "print network debugging informaion";
    }

    @Override
    public String getOptsSyntax()
    {
        return "";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }

    @Override
    public boolean isHidden()
    {
        return false;
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {

        GetTransportDiagnosticsReply diagnostics = s.d().getRitualClient_().getTransportDiagnostics();

        if (diagnostics.hasTcpDiagnostics()) {
            s.out().print("lan:");
            s.out().println(prettyPrint(diagnostics.getTcpDiagnostics()));
        }
        if (diagnostics.hasJingleDiagnostics()) {
            s.out().print("wan:");
            s.out().println(prettyPrint(diagnostics.getJingleDiagnostics()));
        }
        if (diagnostics.hasZephyrDiagnostics()) {
            s.out().print("relay:");
            s.out().println(prettyPrint(diagnostics.getZephyrDiagnostics()));
        }
    }
}
