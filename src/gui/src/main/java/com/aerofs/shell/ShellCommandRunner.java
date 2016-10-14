package com.aerofs.shell;

import com.aerofs.lib.Util;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.ui.error.ErrorMessage;
import com.aerofs.ui.error.ErrorMessages;
import jline.console.ConsoleReader;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

public class ShellCommandRunner<T>
{
    public static interface ICallback
    {
        String getPrompt_();
    }

    public static final String ARG_ECHO_LONG = "--echo";
    public static final String ARG_ECHO = "-e";

    private final Map<String, IShellCommand<T>> _cmds = new TreeMap<String, IShellCommand<T>>();
    private final PrintStream _err = System.err;
    private final PrintStream _out = System.out;
    private final ConsoleReader _reader;
    private boolean _echo;
    private final ICallback _cb;
    private final T _data;
    private final String _prog, _desc;

    private String[] _cmdlineInput;

    public ShellCommandRunner(ICallback cb, T data, String prog, String desc, String[] args)
            throws ExBadArgs, IOException
    {
        _cb = cb;
        _data = data;
        _prog = prog;
        _desc = desc;

        _reader = new ConsoleReader();
        setupConsoleReader();

        addCommand_(new CmdHelp<T>());
        addCommand_(new CmdExit<T>());

        parseArgs(args);
    }

    public String getProgram()
    {
        return _prog;
    }

    public String getDescription()
    {
        return _desc;
    }

    private void setupConsoleReader()
    {
        _reader.setBellEnabled(false);
        // http://www.gnu.org/software/bash/manual/html_node/Event-Designators.html
        _reader.setExpandEvents(true);
        _reader.setHistoryEnabled(true);
        // TODO: tab completion (context specific)
    }

    private void parseArgs(String[] args)
            throws ExBadArgs
    {
        int argsEnd = 0;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals(ARG_ECHO) || a.equals(ARG_ECHO_LONG)) {
                _echo = true;
            } else if (a.startsWith("-")) {
                throw new ExBadArgs(a);
            } else {
                break;
            }
            argsEnd = i + 1;
        }

        if (argsEnd <= args.length - 1) {
            _cmdlineInput = Arrays.copyOfRange(args, argsEnd, args.length);
            if (_echo) {
                _out.print(_cb.getPrompt_());
                for (String arg : _cmdlineInput) _out.print(arg + " ");
                _out.println();
            }
        }
    }

    public void addCommand_(IShellCommand<T> cmd)
    {
        _cmds.put(cmd.getName(), cmd);
    }

    public @Nullable String nextInputLineNullable() throws IOException
    {
        String line = _reader.readLine(_cb.getPrompt_());
        if (line != null && _echo) {
            _out.print(_cb.getPrompt_());
            _out.println(line);
        }
        return line;
    }

    public PrintStream out()
    {
        return _out;
    }

    public Map<String, IShellCommand<T>> getCommands_()
    {
        return _cmds;
    }

    public void usage(IShellCommand<T> cmd)
    {
        out().println(cmd.getName() + ": " + cmd.getDescription());
        if (cmd.getOpts().getOptions().isEmpty()) {
            out().println("usage: " + cmd.getName() + " " + cmd.getOptsSyntax());
        } else {
            assert out() == System.out;
            new HelpFormatter().printHelp(cmd.getName() + " [OPTIONS] " +
                    cmd.getOptsSyntax(), cmd.getOpts());
        }

        if (!StringUtils.isEmpty(cmd.getFooter())) {
            out().println(cmd.getFooter());
        }
    }

    private void exec_(String args[])
    {
        if (args.length == 0) return;

        IShellCommand<T> cmd = _cmds.get(args[0]);
        if (cmd == null) {
            _err.println(_prog + ": command '" + args[0] + "' not found");
            return;
        }

        try {
            CommandLine cl = new PosixParser().parse(cmd.getOpts(),
                    Arrays.copyOfRange(args, 1, args.length));

            try {
                cmd.execute(this, cl);
            } catch (Exception e) {
                if (e.getCause() instanceof Error) throw e.getCause();
                else throw e;
            }
        } catch (Throwable e) {
            String prefix = cmd.getName() + ": ";
            String typeHelp = "Type " + Util.quote("help " + cmd.getName()) + " for usage.";
            ErrorMessages.show(e, prefix + ErrorMessages.e2msgNoBracketDeprecated(e) + '.',
                    new ErrorMessage(ExBadArgs.class, prefix + "bad arguments. " + typeHelp),
                    new ErrorMessage(ParseException.class, prefix + "bad command input. " + typeHelp));
        }
    }

    public void start_() throws IOException
    {
        if (_cmdlineInput != null) {
            exec_(_cmdlineInput);
            return;
        }

        if (System.console() != null) {
            _out.println("type 'help' for help");
            _out.println();
        }

        while (true) {
            String line;
            try {
                line = nextInputLineNullable();
                if (line == null) break;
            } catch (NoSuchElementException e) {
                break;
            }

            try {
                exec_(new Parser().parse(line));
            } catch (ExBadArgs e) {
                _err.println("bad arguments: " + e.getMessage());
            }
        }
    }

    public T d()
    {
        return _data;
    }
}
