package com.aerofs.shell;

import com.aerofs.labeling.L;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.base.ex.ExBadArgs;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;

import java.io.PrintStream;
import java.io.PrintWriter;

public class CmdHelp<T> extends AbstractShellCommand<T>
{
    private static final String INDENT = "    ";
    private static final int INDENT_WIDTH = 4;
    private static final int LINE_WIDTH = 80;

    @Override
    public void execute(ShellCommandRunner<T> s, CommandLine cl) throws ExBadArgs
    {
        if (cl.getArgs().length == 0) {
            helpGlobal(s);
        } else for (String arg : cl.getArgs()) {
            IShellCommand<T> cmd = s.getCommands_().get(arg);
            if (cmd != null) {
                s.usage(cmd);
            } else {
                s.out().println("command " + Util.quote(arg) + " not found. Type \"help\"" +
                        " for a list of available commands.");
                // ignore the rest of the arguments
                break;
            }
        }
    }

    private void helpGlobal(ShellCommandRunner<T> s)
    {
        String prog = s.getProgram();

        PrintStream out = s.out();
        PrintWriter pw = new PrintWriter(out);
        HelpFormatter hf = new HelpFormatter();

        out.println("NAME");
        out.println(INDENT + prog + " -- " + s.getDescription());

        out.println();
        out.println("SYNOPSIS");
        out.println(INDENT + prog + " [OPTIONS] [COMMAND [COMMAND_OPTIONS]]");

        out.println();
        out.println("DESCRIPTION");

        hf.printWrapped(pw, LINE_WIDTH, INDENT_WIDTH, INDENT +
                "As a client program of " + L.product() + ", " + prog +
                " requires a running " + L.product() + " CLI " +
                "(" + S.CLI_NAME + ") or GUI (" + L.productUnixName() +
                ") process to be fully functional. " +
                "Multiple " + prog + " instances may be executed at the same time.");
        pw.println();

        hf.printWrapped(pw, LINE_WIDTH, INDENT_WIDTH, INDENT +
        prog + " runs in one of these three modes: command-line mode," +
                " interactive mode, and batch mode. The command-line mode exits the process" +
                " immediately after executing the command specified in " + prog +
                "'s command line" + ". If no command is not specified in the command line," +
                " " + prog + " enters the interactive mode where it prompts the user to enter" +
                " commands from the console. If the console is disabled, " + prog +
                " enters the batch mode where it takes commands from stdin." +
                " Multiple commands are separated by line breaks. ");
        pw.println();
        hf.printWrapped(pw, LINE_WIDTH, INDENT_WIDTH, INDENT +
                "The following options are available:");
        pw.println();
        pw.println(
                INDENT + ShellCommandRunner.ARG_ECHO + "," + ShellCommandRunner.ARG_ECHO_LONG + INDENT +
                        "echo commands to stdout, useful for script debugging");
        pw.flush();

        out.println();
        out.println("COMMANDS");
        for (IShellCommand<T> cmd : s.getCommands_().values()) {
            if (!cmd.isHidden()) {
                out.println(INDENT + cmd.getName() + " -- " + cmd.getDescription());
            }
        }
        out.println();
        hf.printWrapped(pw, LINE_WIDTH, INDENT_WIDTH, INDENT +
                "(More to come. Submit or vote for new commands at http://vote.aerofs.com)");
        pw.flush();

        out.println();
        out.println("EXAMPLES");
        hf.printWrapped(pw, LINE_WIDTH, INDENT_WIDTH, INDENT +
                "The following runs the program in the command-line mode:");
        pw.println();
        pw.println(INDENT + INDENT + prog + " ls");
        pw.println();
        hf.printWrapped(pw, LINE_WIDTH, INDENT_WIDTH, INDENT +
                "The following runs the program in the batch mode, reading commands" +
                " from test.ash. It also prints the commands before executing them:");
        pw.println();
        pw.println(INDENT + INDENT + prog + " -e < test.ash");
        pw.flush();

        out.println();
        hf.printWrapped(pw, LINE_WIDTH, INDENT_WIDTH, INDENT +
                "Need more functions? Submit feature requests at http://vote.aerofs.com.");
        hf.printWrapped(pw, LINE_WIDTH, INDENT_WIDTH, INDENT +
                "Got problems? Visit http://support.aerofs.com.");
        pw.flush();
    }

    @Override
    public String getName()
    {
        return "help";
    }

    @Override
    public String getDescription()
    {
        return "print global or command-specific help messages";
    }

    @Override
    public String getOptsSyntax()
    {
        return "[COMMAND]...";
    }
}
