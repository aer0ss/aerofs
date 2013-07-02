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
        IProgram program;
        if (prog.equals(LibParam.SH_NAME)) program = new ShProgram();
        else if (prog.equals(LibParam.GUI_NAME)) program = new GUIProgram();
        else if (prog.equals(LibParam.CLI_NAME)) program = new CLIProgram();
        else if (prog.equals(LibParam.TOOLS_NAME)) program = new ToolsProgram();
        else throw new ExProgramNotFound(prog);
        program.launch_(rtRoot, prog, args);
    }
}
