/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.shell;

import com.aerofs.lib.SecUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.protobuf.ByteString;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class CmdPassword implements IShellCommand<ShProgram>
{

    @Override
    public String getName()
    {
        return "password";
    }

    @Override
    public String getDescription()
    {
        return "change your password";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }

    @Override
    public String getOptsSyntax()
    {
        return "[OLD PASSWORD] [NEW PASSWORD]";
    }


    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgList().size() != 2) throw new ExBadArgs();

        SPBlockingClient sp = s.d().getSPClient_();
        byte[] oldPassword = SecUtil.scrypt(cl.getArgs()[0].toCharArray(), Cfg.user());
        byte[] newPassword = SecUtil.scrypt(cl.getArgs()[1].toCharArray(), Cfg.user());
        sp.changePassword(
                ByteString.copyFrom(oldPassword),
                ByteString.copyFrom(newPassword));
    }
}
