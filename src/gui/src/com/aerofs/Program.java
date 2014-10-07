// we put it here instead of com.aerofs.ui so ProGuard can rename the entire ui
// package.
//
package com.aerofs;

import com.aerofs.cli.CLIProgram;
import com.aerofs.gui.GUIProgram;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.LibParam;
import com.aerofs.shell.ShProgram;
import com.aerofs.tools.ToolsProgram;

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
        case LibParam.TOOLS_NAME:   return new ToolsProgram();
        default: throw new ExProgramNotFound(prog);
        }
    }
}
