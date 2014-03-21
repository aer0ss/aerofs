/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.shell;

import com.aerofs.proto.Diagnostics.DeviceDiagnostics;
import com.aerofs.proto.Diagnostics.TransportDiagnostics;
import com.aerofs.proto.Ritual.GetDiagnosticsReply;
import org.apache.commons.cli.CommandLine;

import java.io.PrintStream;

import static com.aerofs.lib.JsonFormat.prettyPrint;

public class CmdDiagnostics extends AbstractShellCommand<ShProgram>
{
    @Override
    public String getName()
    {
        return "diagnostics";
    }

    @Override
    public String getDescription()
    {
        return "print diagnostics";
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        GetDiagnosticsReply diagnostics = s.d().getRitualClient_().getDiagnostics();

        if (diagnostics.hasDeviceDiagnostics()) {
            printDeviceDiagnostics(diagnostics.getDeviceDiagnostics(), s.out());
        }

        if (diagnostics.hasTransportDiagnostics()) {
            printTransportDiagnostics(diagnostics.getTransportDiagnostics(), s.out());
        }
    }

    private void printDeviceDiagnostics(DeviceDiagnostics deviceDiagnostics, PrintStream out)
    {
        out.print("devices:");
        out.println(prettyPrint(deviceDiagnostics));
    }

    private void printTransportDiagnostics(TransportDiagnostics transportDiagnostics, PrintStream out)
    {
        if (transportDiagnostics.hasTcpDiagnostics()) {
            out.print("lan:");
            out.println(prettyPrint(transportDiagnostics.getTcpDiagnostics()));
        }
        if (transportDiagnostics.hasJingleDiagnostics()) {
            out.print("wan:");
            out.println(prettyPrint(transportDiagnostics.getJingleDiagnostics()));
        }
        if (transportDiagnostics.hasZephyrDiagnostics()) {
            out.print("relay:");
            out.println(prettyPrint(transportDiagnostics.getZephyrDiagnostics()));
        }
    }
}
