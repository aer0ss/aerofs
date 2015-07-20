package com.aerofs;

import com.aerofs.cli.CLIProgram;
import com.aerofs.gui.GUIProgram;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.LibParam;
import com.aerofs.shell.ShProgram;

public class Program implements IProgram {

    /**
     * supported program arguments:
     *
     * -E<message> - displays <message> to the user and then immediately abort due to
     *   failure to initialize configuration. Valid for GUI and CLI.
     */
    @Override
    public void launch_(String rtRoot, String prog, String[] args) throws Exception
    {
        getProgram(prog).launch_(rtRoot, prog, args);
    }

    private IProgram getProgram(String prog) throws ExProgramNotFound
    {
        switch (prog) {
        case LibParam.SH_NAME:      return new ShProgram();
        case LibParam.GUI_NAME:     return new GUIProgram();
        case LibParam.CLI_NAME:     return new CLIProgram();
        default: throw new ExProgramNotFound(prog);
        }
    }
}
