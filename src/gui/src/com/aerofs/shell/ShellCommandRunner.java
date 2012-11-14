package com.aerofs.shell;

import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.ui.UIUtil;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.TreeMap;

public class ShellCommandRunner<T>
{
    public static interface ICallback<T>
    {
        String getPrompt_();
    }

    public static final Options EMPTY_OPTS = new Options();
    public static final String ARG_ECHO_LONG = "--echo";
    public static final String ARG_ECHO = "-e";
    public static final String ARG_SHOW_STACK_LONG = "--show-stack";
    public static final String ARG_SHOW_STACK = "-s";

    private final Map<String, IShellCommand<T>> _cmds = new TreeMap<String, IShellCommand<T>>();
    private final Map<String, String[]> _aliases = new TreeMap<String, String[]>();
    private final Scanner _scanner = new Scanner(System.in);
    private final PrintStream _out = System.out;
    private final PrintStream _err = System.err;
    private boolean _echo;
    private boolean _showStack;
    private final ICallback<T> _cb;
    private final T _data;
    private final String _prog, _desc;

    private String[] _cmdlineInput;
    private String _lastInput = "";

    public ShellCommandRunner(ICallback<T> cb, T data, String prog, String desc, String[] args)
            throws ExBadArgs
    {
        _cb = cb;
        _data = data;
        _prog = prog;
        _desc = desc;

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

    private void parseArgs(String[] args)
            throws ExBadArgs
    {
        int argsEnd = 0;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals(ARG_ECHO) || a.equals(ARG_ECHO_LONG)) {
                _echo = true;
            } else if (a.equals(ARG_SHOW_STACK) || a.equals(ARG_SHOW_STACK_LONG)) {
                _showStack = true;
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

    public String nextInputLine()
    {
        String line = _scanner.nextLine();
        if (_echo) {
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

    public T cookie_()
    {
        return _data;
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
    }

    private void exec_(String args[])
    {
        if (args.length == 0) return;

        if (_aliases.containsKey(args[0])) args = _aliases.get(args[0]);

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
        } catch (ExBadArgs e) {
            handleCommandException_(cmd, e);
        } catch (ParseException e) {
            handleCommandException_(cmd, e);
        } catch (Throwable e) {
            handleGeneralException_(cmd, e);
        }
    }

    private void handleCommandException_(IShellCommand<T> cmd, Throwable e)
    {
        _err.println(cmd.getName() + ": " + UIUtil.e2msgNoBracket(e));
        _err.println("Type " + Util.quote("help " + cmd.getName()) + " for usage");
    }

    private void handleGeneralException_(IShellCommand<T> cmd, Throwable e)
    {
        _err.println(cmd.getName() + ": " + UIUtil.e2msgNoBracket(e));
        if (_showStack) _err.println(Util.e(e));
    }

    public void start_()
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
            if (System.console() != null) {
                _out.print(_cb.getPrompt_());
            }

            String line = null;
            try {
                line = nextInputLine();
            } catch (NoSuchElementException e) {
                break;
            }

            if (line.isEmpty()) {
                line = _lastInput;
            } else {
                _lastInput = line;
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
