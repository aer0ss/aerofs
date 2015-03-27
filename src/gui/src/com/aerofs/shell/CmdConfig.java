/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.labeling.L;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgRestService;
import com.google.common.collect.Sets;
import org.apache.commons.cli.CommandLine;

import java.io.PrintStream;
import java.util.Set;

import static com.aerofs.lib.cfg.CfgDatabase.REST_SERVICE;

/**
 * N.B. since we only support one option for the time being, let's go with the simplest impl. As
 * we add more options, we'll architect a design that scales better with the number of options.
 */
public class CmdConfig extends AbstractShellCommand<ShProgram>
{
    private final String API = "api";

    @Override
    public String getName()
    {
        return "config";
    }

    @Override
    public String getDescription()
    {
        return "print or update configuration values";
    }

    @Override
    public String getOptsSyntax()
    {
        return "[KEY [VALUE]]";
    }

    @Override
    public String getFooter()
    {
        return "valid keys:\n" +
                " -" + API + ": enable | disable\n" +
                "  Enable to grant applications you've authorized access to the files on " +
                "this computer. See https://support.aerofs.com/hc/en-us/articles/202492734 for details.";
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        String[] args = cl.getArgs();
        try {
            switch (args.length) {
            case 0:
                printValues(s.out());
                break;
            case 1:
                printValue(s.out(), args[0]);
                break;
            case 2:
                updateValue(s.out(), args[0], args[1]);
                break;
            default:
                throw new ExBadArgs("Expected either 0, 1, or 2 arguments");
            }
        } catch (InvalidKeyException e) {
            onInvalidKey(s.out(), e);
        }
    }

    private void printValues(PrintStream out)
            throws Exception
    {
        printValue(out, API);
    }

    private void printValue(PrintStream out, String key)
            throws InvalidKeyException
    {
        if (key.equals(API)) {
            CfgRestService cfg = new CfgRestService();
            out.println(API + ": " + (cfg.isEnabled() ? "enabled" : "disabled"));
        } else {
            throw new InvalidKeyException(key);
        }
    }

    private void updateValue(PrintStream out, String key, String value)
            throws Exception
    {
        if (key.equals(API)) {
            boolean enable = parseBoolean(value);
            Cfg.db().set(REST_SERVICE, enable);

            out.println("Success: " + API + " is now set to " +
                    (enable ? "enabled" : "disabled") + ".");
            out.println("Note that this change will not take effect until you restart "
                    + L.product() + ". Please restart " + L.product() + " now.");
        } else {
            throw new InvalidKeyException(key);
        }
    }

    private void onInvalidKey(PrintStream out, InvalidKeyException e)
    {
        out.println("Invalid key: " + e._key);
        out.println("To see a list of available keys and their values, see: help " + getName());
    }

    // N.B. consider moving this to an utility class if we start getting more of this
    private boolean parseBoolean(String value)
    {
        Set<String> acceptedValues = Sets.newHashSet(
                "true", "t", "enable", "enabled", "e", "on", "yes", "y", "1"
        );

        return acceptedValues.contains(value.toLowerCase().trim());
    }

    private class InvalidKeyException extends Exception
    {
        private static final long serialVersionUID = 0;

        public final String _key;
        public InvalidKeyException(String key)
        {
            _key = key;
        }
    }
}
