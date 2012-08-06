package com.aerofs.shell;

import com.aerofs.lib.C;
import com.aerofs.lib.S;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.fsi.FSIUtil;
import com.aerofs.lib.spsv.SVClient;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class CmdReport implements IShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        StringBuilder sb = new StringBuilder("(via shell): ");
        if (cl.getArgs().length == 0) {
            throw new ExBadArgs("please provide a brief description of the problem");
        }

        for (String arg : cl.getArgs()) {
            sb.append(arg);
            sb.append(" ");
        }

        SVClient.logSendDefectSync(false, sb.toString() +
                "\n" + C.END_OF_DEFECT_MESSAGE, null,
                FSIUtil.dumpStatForDefectLogging(s.d().getRitualClient_()));

        s.out().println("problem submitted. thank you!");
    }

    @Override
    public String getName()
    {
        return "report";
    }

    @Override
    public String getDescription()
    {
        return "report an issue to the " + S.PRODUCT + " team";
    }

    @Override
    public String getOptsSyntax()
    {
        return "DESCRIPTION";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }
}
